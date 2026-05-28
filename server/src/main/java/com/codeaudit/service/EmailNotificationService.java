package com.codeaudit.service;

import com.codeaudit.entity.Review;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;

/**
 * Email notification service using Spring Mail.
 * Sends review result summary to configured recipients.
 *
 * @author CodeAudit Team
 */
@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);
    private static final DecimalFormat DF = new DecimalFormat("0.0");

    private final JavaMailSender mailSender;

    @Value("${codeaudit.notification.email.enabled:false}")
    private boolean enabled;

    @Value("${codeaudit.notification.email.to:}")
    private String recipients;

    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendReviewResult(Review review, int total, int high, int medium, int low) {
        if (!enabled) {
            log.debug("邮件通知未启用，跳过");
            return;
        }
        if (recipients == null || recipients.isBlank()) {
            log.warn("邮件通知未配置收件人，跳过");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String projectName = review.getProject() != null ? review.getProject().getName() : "N/A";
            helper.setSubject("CodeAudit 审查完成 — " + projectName);
            helper.setTo(recipients.split(","));
            helper.setText(buildHtml(review, total, high, medium, low), true);

            mailSender.send(message);
            log.info("邮件通知已发送至: {}", recipients);
        } catch (Exception e) {
            log.warn("邮件通知发送失败: {}", e.getMessage());
        }
    }

    private String buildHtml(Review review, int total, int high, int medium, int low) {
        String projectName = review.getProject() != null ? review.getProject().getName() : "N/A";
        String duration = DF.format((review.getDurationMs() != null ? review.getDurationMs() : 0) / 1000.0);
        return """
            <html>
            <body style="font-family: sans-serif; line-height: 1.6;">
              <h2>CodeAudit 审查报告</h2>
              <table border="1" cellpadding="8" cellspacing="0" style="border-collapse: collapse;">
                <tr><td><b>项目</b></td><td>%s</td></tr>
                <tr><td><b>标题</b></td><td>%s</td></tr>
                <tr><td><b>问题总数</b></td><td style="color: #f59e0b;">%d</td></tr>
                <tr><td><b>高危</b></td><td style="color: #ef4444;">%d</td></tr>
                <tr><td><b>中危</b></td><td>%d</td></tr>
                <tr><td><b>低危</b></td><td style="color: #22c55e;">%d</td></tr>
                <tr><td><b>耗时</b></td><td>%s s</td></tr>
              </table>
              <p style="color: #94a3b8;">此邮件由 CodeAudit 自动发送</p>
            </body>
            </html>
            """.formatted(projectName, review.getTitle(), total, high, medium, low, duration);
    }
}
