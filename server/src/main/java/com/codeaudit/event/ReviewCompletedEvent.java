package com.codeaudit.event;

import com.codeaudit.entity.Review;

/**
 * 审查完成事件
 * <p>
 * 当 ReviewEngine 完成审查并写入数据库后发布此事件，
 * 监听方（如 PrReviewService）据此执行后续动作（如发布 PR 评论）。
 *
 * @author CodeAudit Team
 */
public class ReviewCompletedEvent {

    private final Review review;

    public ReviewCompletedEvent(Review review) {
        this.review = review;
    }

    public Review getReview() {
        return review;
    }
}
