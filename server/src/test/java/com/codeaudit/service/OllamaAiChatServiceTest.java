package com.codeaudit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * OllamaAiChatService 单元测试
 *
 * @author CodeAudit Team
 */
@ExtendWith(MockitoExtension.class)
class OllamaAiChatServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private AiConfigService aiConfigService;

    @Test
    void chat_shouldReturnContent_whenCallSucceeds() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(mock(ChatClient.ChatClientRequestSpec.class));
        when(chatClient.prompt().user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("Hello from Ollama");
        when(aiConfigService.getOllamaModel()).thenReturn("qwen3:8b");

        OllamaAiChatService service = new OllamaAiChatService(chatClientBuilder, aiConfigService);

        String result = service.chat("Say hello");

        assertEquals("Hello from Ollama", result);
    }
}
