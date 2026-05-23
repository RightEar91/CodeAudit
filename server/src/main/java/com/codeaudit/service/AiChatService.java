package com.codeaudit.service;

/**
 * AI 对话服务接口 — 与具体模型提供商解耦
 * <p>
 * 通过 {@link OllamaAiChatService} 和 {@link OpenAiAiChatService} 两种实现，
 * 支持在本地 Ollama 和云端 OpenAI 兼容 API 之间切换。
 *
 * @author CodeAudit Team
 */
public interface AiChatService {

    /**
     * 发送单轮对话请求，返回模型生成的文本
     *
     * @param prompt 提示词
     * @return 模型响应内容
     */
    String chat(String prompt);
}
