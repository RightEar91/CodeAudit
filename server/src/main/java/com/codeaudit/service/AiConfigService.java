package com.codeaudit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 配置服务 — 运行时配置的单一事实来源
 * <p>
 * 启动时从 application.yml 加载初始值，运行时可通过 Settings 页面更新。
 * AI 对话服务（Ollama / OpenAI）和 SettingsController 都从此处读取配置。
 *
 * @author CodeAudit Team
 */
@Service
public class AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);

    @Value("${codeaudit.ai.provider:ollama}")
    private String provider;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model:qwen3:8b}")
    private String ollamaModel;

    @Value("${codeaudit.ai.openai.api-key:}")
    private String openaiApiKey;

    @Value("${codeaudit.ai.openai.base-url:https://api.openai.com}")
    private String openaiBaseUrl;

    @Value("${codeaudit.ai.openai.model:gpt-4o}")
    private String openaiModel;

    @Value("${codeaudit.ai.openai.temperature:0.1}")
    private double openaiTemperature;

    @Value("${codeaudit.ai.review.thinking-enabled:true}")
    private boolean thinkingEnabled;

    @Value("${codeaudit.ai.review.parallelism:2}")
    private int parallelism;

    // ==================== Getters ====================

    public String getProvider() {
        return provider;
    }

    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    public String getOllamaModel() {
        return ollamaModel;
    }

    public String getOpenaiApiKey() {
        return openaiApiKey;
    }

    public String getOpenaiBaseUrl() {
        return openaiBaseUrl;
    }

    public String getOpenaiModel() {
        return openaiModel;
    }

    public double getOpenaiTemperature() {
        return openaiTemperature;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public int getParallelism() {
        return parallelism;
    }

    // ==================== Setters（运行时更新） ====================

    public void setProvider(String provider) {
        this.provider = provider;
        log.info("AI Provider 已切换为: {}", provider);
    }

    public void setOllamaBaseUrl(String ollamaBaseUrl) {
        this.ollamaBaseUrl = ollamaBaseUrl;
        log.info("Ollama 地址已更新为: {}", ollamaBaseUrl);
    }

    public void setOllamaModel(String ollamaModel) {
        this.ollamaModel = ollamaModel;
        log.info("Ollama 模型已更新为: {}", ollamaModel);
    }

    public void setOpenaiApiKey(String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
        log.info("OpenAI API Key 已更新");
    }

    public void setOpenaiBaseUrl(String openaiBaseUrl) {
        this.openaiBaseUrl = openaiBaseUrl;
        log.info("OpenAI Base URL 已更新为: {}", openaiBaseUrl);
    }

    public void setOpenaiModel(String openaiModel) {
        this.openaiModel = openaiModel;
        log.info("OpenAI 模型已更新为: {}", openaiModel);
    }

    public void setOpenaiTemperature(double openaiTemperature) {
        this.openaiTemperature = openaiTemperature;
        log.info("OpenAI Temperature 已更新为: {}", openaiTemperature);
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
        log.info("Thinking 模式已{}", thinkingEnabled ? "启用" : "关闭");
    }

    public void setParallelism(int parallelism) {
        this.parallelism = Math.max(1, Math.min(8, parallelism));
        log.info("并行度已更新为: {}", this.parallelism);
    }

    // ==================== 对外视图 ====================

    /**
     * 获取所有配置的 Map 视图（apiKey 脱敏）
     */
    public Map<String, Object> toMap() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("provider", provider);
        settings.put("ollamaUrl", ollamaBaseUrl);
        settings.put("ollamaModel", ollamaModel);
        settings.put("openaiApiKey", maskApiKey(openaiApiKey));
        settings.put("openaiBaseUrl", openaiBaseUrl);
        settings.put("openaiModel", openaiModel);
        settings.put("openaiTemperature", openaiTemperature);
        settings.put("thinkingEnabled", thinkingEnabled);
        settings.put("parallelism", parallelism);
        return settings;
    }

    /**
     * 从 Map 更新配置（运行时保存）
     */
    @SuppressWarnings("unchecked")
    public void updateFromMap(Map<String, Object> body) {
        if (body.containsKey("provider")) {
            String p = (String) body.get("provider");
            if ("ollama".equals(p) || "openai".equals(p)) {
                setProvider(p);
            }
        }
        if (body.containsKey("ollamaUrl")) {
            String url = (String) body.get("ollamaUrl");
            if (url == null || url.isBlank() || !isValidHttpUrl(url)) {
                log.warn("无效的 Ollama URL: {}", url);
            } else {
                setOllamaBaseUrl(url);
            }
        }
        if (body.containsKey("ollamaModel")) {
            String model = (String) body.get("ollamaModel");
            if (model != null && !model.isBlank()) {
                setOllamaModel(model);
            }
        }
        if (body.containsKey("openaiApiKey")) {
            String key = (String) body.get("openaiApiKey");
            if (key != null && !key.isBlank() && !key.contains("****")) {
                setOpenaiApiKey(key);
            }
        }
        if (body.containsKey("openaiBaseUrl")) {
            String url = (String) body.get("openaiBaseUrl");
            if (url == null || url.isBlank() || !isValidHttpUrl(url)) {
                log.warn("无效的 OpenAI Base URL: {}", url);
            } else {
                setOpenaiBaseUrl(url);
            }
        }
        if (body.containsKey("openaiModel")) {
            setOpenaiModel((String) body.get("openaiModel"));
        }
        if (body.containsKey("openaiTemperature")) {
            Object temp = body.get("openaiTemperature");
            if (temp instanceof Number num) {
                double val = num.doubleValue();
                if (val >= 0.0 && val <= 2.0) {
                    setOpenaiTemperature(val);
                } else {
                    log.warn("Temperature 值超出范围 [0, 2]: {}", val);
                }
            }
        }
        if (body.containsKey("thinkingEnabled")) {
            Object te = body.get("thinkingEnabled");
            setThinkingEnabled(te instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(te)));
        }
        if (body.containsKey("parallelism")) {
            Object pl = body.get("parallelism");
            if (pl instanceof Number) {
                setParallelism(((Number) pl).intValue());
            }
        }
    }

    private String maskApiKey(String key) {
        if (key == null || key.isBlank()) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private boolean isValidHttpUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            return false;
        }
    }
}
