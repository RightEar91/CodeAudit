package com.codeaudit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 基于本地 Ollama 的 AI 对话实现
 * <p>
 * 使用 Spring AI 的 {@link ChatClient} 调用本地 Ollama 服务。
 * 每次请求通过 {@link OllamaOptions} 动态覆写模型名，
 * 使 Settings 页面的运行时修改无需重启即可生效。
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
        String model = config.getOllamaModel();
        log.debug("调用本地 Ollama 模型: {}", model);
        return chatClient.prompt()
                .user(prompt)
                .options(OllamaOptions.builder().model(model).build())
                .call()
                .content();
    }
}
