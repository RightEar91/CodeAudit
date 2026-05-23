package com.codeaudit.controller;

import com.codeaudit.common.Response;
import com.codeaudit.service.AiConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统设置 REST 控制器
 * <p>
 * 提供前端设置页所需的配置读取、保存和 AI 服务连接检测接口。
 * 配置读写统一委托给 {@link AiConfigService}，确保 Settings 页面修改的配置
 * 能立即被 AI 对话服务感知（OpenAI 模式），Ollama 地址/模型修改需重启生效。
 * <p>
 * 路由设计：
 * <ul>
 *   <li>{@code GET  /api/settings}     — 获取当前配置</li>
 *   <li>{@code PUT  /api/settings}     — 保存配置</li>
 *   <li>{@code GET  /api/ollama/check} — 检测 Ollama 连接状态</li>
 * </ul>
 *
 * @author CodeAudit Team
 */
@RestController
@RequestMapping("/api")
public class SettingsController {

    private static final Logger log = LoggerFactory.getLogger(SettingsController.class);

    private final AiConfigService config;
    private final ObjectMapper objectMapper;

    public SettingsController(AiConfigService config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/settings")
    public Response<Map<String, Object>> getSettings() {
        return Response.ok(config.toMap());
    }

    @PutMapping("/settings")
    public Response<Map<String, Object>> saveSettings(@RequestBody Map<String, Object> body) {
        config.updateFromMap(body);
        return Response.ok(config.toMap());
    }

    @GetMapping("/ollama/check")
    public Response<Map<String, String>> checkOllama() {
        String baseUrl = config.getOllamaBaseUrl();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("url", baseUrl);

        if (baseUrl == null || baseUrl.isBlank()) {
            result.put("status", "disconnected");
            result.put("error", "Ollama Base URL 未配置");
            return Response.fail(HttpStatus.SERVICE_UNAVAILABLE.value(), "Ollama Base URL 未配置");
        }

        try {
            RestClient client = RestClient.builder().build();
            client.get()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .retrieve()
                    .body(String.class);
            result.put("status", "connected");
            log.info("Ollama 连接正常: {}", baseUrl);
        } catch (Exception e) {
            result.put("status", "disconnected");
            result.put("error", e.getMessage());
            log.warn("Ollama 连接失败: {} — {}", baseUrl, e.getMessage());
            return Response.fail(HttpStatus.SERVICE_UNAVAILABLE.value(), "Ollama 服务不可达: " + e.getMessage());
        }

        return Response.ok(result);
    }

    /**
     * 获取 Ollama 已安装的模型列表
     */
    @GetMapping("/ollama/models")
    public Response<List<String>> listOllamaModels() {
        String baseUrl = config.getOllamaBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return Response.fail(HttpStatus.SERVICE_UNAVAILABLE.value(), "Ollama Base URL 未配置");
        }
        try {
            RestClient client = RestClient.builder().build();
            String body = client.get()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .retrieve()
                    .body(String.class);

            List<String> models = new ArrayList<>();
            JsonNode root = objectMapper.readTree(body);
            JsonNode modelsNode = root.get("models");
            if (modelsNode != null && modelsNode.isArray()) {
                for (JsonNode model : modelsNode) {
                    String name = model.get("name").asText();
                    if (name != null && !name.isBlank()) {
                        models.add(name);
                    }
                }
            }
            return Response.ok(models);
        } catch (Exception e) {
            log.warn("获取 Ollama 模型列表失败: {}", e.getMessage());
            return Response.fail(HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "无法获取模型列表: " + e.getMessage());
        }
    }
}
