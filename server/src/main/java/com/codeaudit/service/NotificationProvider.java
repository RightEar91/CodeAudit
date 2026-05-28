package com.codeaudit.service;

import com.codeaudit.entity.Review;

/**
 * Notification provider interface for sending review result notifications.
 * Implementations include Email, WeCom, DingTalk, Feishu.
 *
 * @author CodeAudit Team
 */
public interface NotificationProvider {

    /** Unique identifier for the provider */
    String getName();

    /** Whether this provider is enabled */
    boolean isEnabled();

    /** Send notification for a completed review */
    void notify(Review review, int totalIssues, int highCount, int mediumCount, int lowCount);
}
