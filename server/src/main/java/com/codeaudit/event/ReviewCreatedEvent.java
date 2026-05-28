package com.codeaudit.event;

import com.codeaudit.dto.DiffBlock;
import com.codeaudit.entity.Review;

import java.util.List;

/**
 * 审查创建事件
 * <p>
 * 当审查任务创建并事务提交后发布此事件，
 * ReviewEngine 监听到后异步执行 AI 审查。
 * <p>
 * 替代原来手动的 TransactionSynchronizationManager.afterCommit()，
 * 利用 Spring 的 @TransactionalEventListener 确保事务提交后才触发。
 *
 * @author CodeAudit Team
 */
public class ReviewCreatedEvent {

    private final Review review;
    private final List<DiffBlock> diffBlocks;
    private final String language;

    public ReviewCreatedEvent(Review review, List<DiffBlock> diffBlocks, String language) {
        this.review = review;
        this.diffBlocks = diffBlocks;
        this.language = language;
    }

    public Review getReview() { return review; }
    public List<DiffBlock> getDiffBlocks() { return diffBlocks; }
    public String getLanguage() { return language; }
}
