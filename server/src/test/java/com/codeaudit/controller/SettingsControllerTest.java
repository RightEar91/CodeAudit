package com.codeaudit.controller;

import com.codeaudit.TestAiChatConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAiChatConfig.class)
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    void shouldGetSettings() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    @WithMockUser
    void shouldSaveSettings() throws Exception {
        Map<String, Object> body = Map.of(
                "provider", "ollama",
                "ollamaModel", "qwen3:4b",
                "parallelism", 3,
                "thinkingEnabled", false
        );

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ollamaModel").value("qwen3:4b"))
                .andExpect(jsonPath("$.data.parallelism").value(3))
                .andExpect(jsonPath("$.data.thinkingEnabled").value(false));
    }

    @Test
    @WithMockUser
    void shouldSaveSettingsAndSwitchProvider() throws Exception {
        Map<String, Object> body = Map.of(
                "provider", "openai",
                "openaiModel", "deepseek-chat",
                "openaiApiKey", "sk-test-key",
                "openaiBaseUrl", "https://api.deepseek.com"
        );

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("openai"));
    }

    @Test
    void shouldRejectSettingsWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isUnauthorized());
    }
}
