package com.codeaudit.service;

import com.codeaudit.entity.Review;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Webhook bot service for WeCom / DingTalk / Feishu notifications.
 * Sends a simple markdown card via webhook URL to the specified robot.
 *
 * @author CodeAudit Team
 */
@Service
public class WebhookBotService {

    private static final Logger log = LoggerFactory.getLogger(WebhookBotService.class);
    private static final DecimalFormat DF = new DecimalFormat("0.0");

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${codeaudit.notification.wework.webhook:}")
    private String wecomWebhook;

    @Value("${codeaudit.notification.dingtalk.webhook:}")
    private String dingtalkWebhook;

    @Value("${codeaudit.notification.feishu.webhook:}")
    private String feishuWebhook;

    @Async
    public void sendWeCom(Review review, int total, int high, int medium, int low) {
        if (wecomWebhook == null || wecomWebhook.isBlank()) return;
        String text = buildMarkdown(review, total, high, medium, low);
        sendMarkdown(wecomWebhook, text);
    }

    @Async
    public void sendDingTalk(Review review, int total, int high, int medium, int low) {
        if (dingtalkWebhook == null || dingtalkWebhook.isBlank()) return;
        String text = buildMarkdown(review, total, high, medium, low);
        sendDingTalkMarkdown(dingtalkWebhook, title(review), text);
    }

    @Async
    public void sendFeishu(Review review, int total, int high, int medium, int low) {
        if (feishuWebhook == null || feishuWebhook.isBlank()) return;
        String text = buildMarkdown(review, total, high, medium, low);
        sendFeishuText(feishuWebhook, title(review) + "\n" + text);
    }

    private String title(Review review) {
        return "CodeAudit 审查报告 — " + (review.getProject() != null ? review.getProject().getName() : "Unknown");
    }

    private String buildMarkdown(Review review, int total, int high, int medium, int low) {
        String name = review.getProject() != null ? review.getProject().getName() : "N/A";
        return "### " + title(review) + "\n"
                + "> 标题: " + review.getTitle() + "\n"
                + "> 问题总数: <font color=\"warning\">" + total + "</font>\n"
                + "> 高危: <font color=\"red\">" + high + "</font>  中危: " + medium + "  低危: " + low + "\n"
                + "> 耗时: " + DF.format((review.getDurationMs() != null ? review.getDurationMs() : 0) / 1000.0) + "s\n";
    }

    private void sendMarkdown(String webhook, String content) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");
            Map<String, Object> md = new LinkedHashMap<>();
            md.put("content", content);
            body.put("markdown", md);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(webhook, new HttpEntity<>(body, headers), String.class);
            log.info("WeCom webhook 通知已发送");
        } catch (Exception e) {
            log.warn("WeCom webhook 发送失败: {}", e.getMessage());
        }
    }

    private void sendDingTalkMarkdown(String webhook, String title, String text) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "markdown");
            Map<String, Object> md = new LinkedHashMap<>();
            md.put("title", title);
            md.put("text", text);
            body.put("markdown", md);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(webhook, new HttpEntity<>(body, headers), String.class);
            log.info("DingTalk webhook 通知已发送");
        } catch (Exception e) {
            log.warn("DingTalk webhook 发送失败: {}", e.getMessage());
        }
    }

    private void sendFeishuText(String webhook, String text) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msg_type", "text");
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("text", text);
            body.put("content", content);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(webhook, new HttpEntity<>(body, headers), String.class);
            log.info("Feishu webhook 通知已发送");
        } catch (Exception e) {
            log.warn("Feishu webhook 发送失败: {}", e.getMessage());
        }
    }
}
