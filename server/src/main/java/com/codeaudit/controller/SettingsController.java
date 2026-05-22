package com.codeaudit.controller;

import com.codeaudit.common.Response;
import com.codeaudit.service.AiConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.LinkedHashMap;
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

    public SettingsController(AiConfigService config) {
        this.config = config;
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
}
