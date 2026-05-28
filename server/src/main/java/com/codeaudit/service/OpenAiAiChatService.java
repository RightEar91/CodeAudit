package com.codeaudit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于云端 OpenAI 兼容 API 的 AI 对话实现
 * <p>
 * 使用 Spring 6.1 {@link RestClient} 调用 OpenAI 兼容的云端服务。
 * 兼容所有遵循 OpenAI Chat Completions API 格式的服务商。
 * <p>
 * 特性：
 * <ul>
 *   <li>复用 RestClient（仅在 baseUrl 或 apiKey 变化时重建）</li>
 *   <li>支持关闭 Thinking / 推理模式（适用于 Kimi K2 / DeepSeek-R1 等）</li>
 *   <li>内置 3 次重试</li>
 *   <li>30 秒连接超时</li>
 * </ul>
 *
 * @author CodeAudit Team
 */
@Service
@ConditionalOnProperty(prefix = "codeaudit.ai", name = "provider", havingValue = "openai")
public class OpenAiAiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAiChatService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final AiConfigService config;

    private volatile String cachedBaseUrl;
    private volatile String cachedApiKey;
    private volatile RestClient cachedRestClient;

    public OpenAiAiChatService(AiConfigService config) {
        this.config = config;
    }

    private RestClient getRestClient() {
        String baseUrl = config.getOpenaiBaseUrl();
        String apiKey = config.getOpenaiApiKey();

        if (cachedRestClient == null || !Objects.equals(cachedBaseUrl, baseUrl) || !Objects.equals(cachedApiKey, apiKey)) {
            synchronized (this) {
                if (cachedRestClient == null || !Objects.equals(cachedBaseUrl, baseUrl) || !Objects.equals(cachedApiKey, apiKey)) {
                    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                    requestFactory.setConnectTimeout(Duration.ofSeconds(15));
                    requestFactory.setReadTimeout(Duration.ofSeconds(90));

                    this.cachedRestClient = RestClient.builder()
                            .baseUrl(baseUrl)
                            .requestFactory(requestFactory)
                            .build();

                    this.cachedBaseUrl = baseUrl;
                    this.cachedApiKey = apiKey;
                    log.info("RestClient 已创建/重建: baseUrl={}, apiKey={}", baseUrl, maskKey(apiKey));
                }
            }
        }
        return cachedRestClient;
    }

    @Override
    public String chat(String prompt) {
        String model = config.getOpenaiModel();
        double temperature = config.getOpenaiTemperature();
        boolean thinkingEnabled = config.isThinkingEnabled();

        log.info("调用云端 API: url={}, model={}, thinking={}, promptSize={} chars",
                config.getOpenaiBaseUrl(), model, thinkingEnabled, prompt.length());

        Map<String, Object> request = buildRequest(model, prompt, temperature, thinkingEnabled);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return doChat(request, attempt);
            } catch (ResourceAccessException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    log.warn("云端 API 连接失败 (第{}次尝试), {}ms 后重试: {}", attempt, RETRY_DELAY_MS, e.getMessage());
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            } catch (RuntimeException e) {
                if (e.getMessage() != null && (e.getMessage().contains("401") || e.getMessage().contains("403"))) {
                    throw e;
                }
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    log.warn("云端 API 调用失败 (第{}次尝试), {}ms 后重试: {}", attempt, RETRY_DELAY_MS, e.getMessage());
                    try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }

        throw new RuntimeException("云端 API 调用失败，已重试 " + MAX_RETRIES + " 次: " +
                (lastException != null ? lastException.getMessage() : "未知错误"));
    }

    private Map<String, Object> buildRequest(String model, String prompt, double temperature, boolean thinkingEnabled) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        request.put("temperature", temperature);

        if (!thinkingEnabled) {
            request.put("thinking", Map.of("type", "disabled"));
        }

        return request;
    }

    private String doChat(Map<String, Object> request, int attempt) {
        RestClient restClient = getRestClient();

        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + config.getOpenaiApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (attempt > 1) {
            log.info("云端 API 第{}次重试成功", attempt);
        }

        if (response == null || !response.containsKey("choices")) {
            throw new RuntimeException("云端 API 返回异常: " + response);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("云端 API 返回空 choices");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new RuntimeException("云端 API 返回空 message");
        }

        return (String) message.get("content");
    }

    private static String maskKey(String key) {
        if (key == null || key.isBlank()) return "(empty)";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
