package com.codeaudit.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OpenAiAiChatService 单元测试
 * <p>
 * 使用 MockWebServer 模拟云端 OpenAI 兼容 API。
 *
 * @author CodeAudit Team
 */
class OpenAiAiChatServiceTest {

    private static MockWebServer mockWebServer;

    @BeforeAll
    static void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void chat_shouldReturnContent_whenApiReturnsOk() throws InterruptedException {
        String jsonResponse = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\\"issues\\": []}"
                      }
                    }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse));

        String baseUrl = mockWebServer.url("").toString().replaceAll("/$", "");
        AiConfigService config = mockConfig(baseUrl, "fake-api-key", "gpt-4o", 0.1);
        OpenAiAiChatService service = new OpenAiAiChatService(config);

        String result = service.chat("Review this code");

        assertEquals("{\"issues\": []}", result);

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("/chat/completions", request.getPath());
        assertEquals("Bearer fake-api-key", request.getHeader("Authorization"));
    }

    @Test
    void chat_shouldThrowException_whenApiReturnsEmptyChoices() {
        String jsonResponse = """
                {
                  "choices": []
                }
                """;

        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(jsonResponse));
        }

        String baseUrl = mockWebServer.url("").toString().replaceAll("/$", "");
        AiConfigService config = mockConfig(baseUrl, "fake-api-key", "gpt-4o", 0.1);
        OpenAiAiChatService service = new OpenAiAiChatService(config);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.chat("Review this code"));
        assertTrue(ex.getMessage().contains("API 调用失败"));
    }

    @Test
    void chat_shouldThrowException_whenApiReturnsMalformed() {
        String jsonResponse = """
                {"error": "invalid_api_key"}
                """;

        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(jsonResponse));
        }

        String baseUrl = mockWebServer.url("").toString().replaceAll("/$", "");
        AiConfigService config = mockConfig(baseUrl, "fake-api-key", "gpt-4o", 0.1);
        OpenAiAiChatService service = new OpenAiAiChatService(config);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.chat("Review this code"));
        assertTrue(ex.getMessage().contains("API 调用失败"));
    }

    private static AiConfigService mockConfig(String baseUrl, String apiKey, String model, double temperature) {
        AiConfigService config = mock(AiConfigService.class);
        when(config.getOpenaiBaseUrl()).thenReturn(baseUrl);
        when(config.getOpenaiApiKey()).thenReturn(apiKey);
        when(config.getOpenaiModel()).thenReturn(model);
        when(config.getOpenaiTemperature()).thenReturn(temperature);
        return config;
    }
}
