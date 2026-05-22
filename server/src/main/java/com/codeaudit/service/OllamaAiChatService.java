package com.codeaudit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 基于本地 Ollama 的 AI 对话实现
 * <p>
 * 使用 Spring AI 的 {@link ChatClient} 调用本地 Ollama 服务。
 * ChatClient 由 Spring AI 自动配置根据 {@code spring.ai.ollama.*} 属性创建，
 * 运行时修改 Ollama 地址/模型需要重启应用生效。
 * <p>
 * 默认启用，向后兼容现有配置。
 *
 * @author CodeAudit Team
 */
@Service
@ConditionalOnProperty(prefix = "codeaudit.ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaAiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiChatService.class);

    private final ChatClient chatClient;
    private final AiConfigService config;

    public OllamaAiChatService(ChatClient.Builder chatClientBuilder, AiConfigService config) {
        this.chatClient = chatClientBuilder.build();
        this.config = config;
    }

    @Override
    public String chat(String prompt) {
        log.debug("调用本地 Ollama 模型: {}", config.getOllamaModel());
        return chatClient.prompt().user(prompt).call().content();
    }
}
