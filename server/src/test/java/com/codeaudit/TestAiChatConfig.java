package com.codeaudit;

import com.codeaudit.service.AiChatService;
import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestAiChatConfig {

    @Bean
    public ChatModel chatModel() {
        return Mockito.mock(ChatModel.class);
    }

    @Bean
    @Primary
    public AiChatService mockAiChatService() {
        return Mockito.mock(AiChatService.class);
    }
}
