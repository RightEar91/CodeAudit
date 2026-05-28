package com.codeaudit.service;

import com.codeaudit.entity.Review;
import com.codeaudit.event.ReviewCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Listens to ReviewCompletedEvent and sends notifications via email / WeCom / DingTalk / Feishu.
 * All notification providers are called asynchronously and failures are logged without
 * affecting the review flow.
 *
 * @author CodeAudit Team
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final EmailNotificationService emailService;
    private final WebhookBotService webhookBotService;

    public NotificationEventListener(EmailNotificationService emailService,
                                      WebhookBotService webhookBotService) {
        this.emailService = emailService;
        this.webhookBotService = webhookBotService;
    }

    @EventListener
    public void onReviewCompleted(ReviewCompletedEvent event) {
        Review review = event.getReview();
        if (review == null || !"completed".equals(review.getStatus())) return;

        int total = review.getTotalIssues() != null ? review.getTotalIssues() : 0;
        int high = review.getHighCount() != null ? review.getHighCount() : 0;
        int medium = review.getMediumCount() != null ? review.getMediumCount() : 0;
        int low = review.getLowCount() != null ? review.getLowCount() : 0;

        log.info("审查完成，触发通知: reviewId={}, 问题数={}", review.getId(), total);

        try { emailService.sendReviewResult(review, total, high, medium, low); } catch (Exception e) { log.warn("邮件通知异常", e); }
        try { webhookBotService.sendWeCom(review, total, high, medium, low); } catch (Exception e) { log.warn("WeCom 通知异常", e); }
        try { webhookBotService.sendDingTalk(review, total, high, medium, low); } catch (Exception e) { log.warn("DingTalk 通知异常", e); }
        try { webhookBotService.sendFeishu(review, total, high, medium, low); } catch (Exception e) { log.warn("Feishu 通知异常", e); }
    }
}
