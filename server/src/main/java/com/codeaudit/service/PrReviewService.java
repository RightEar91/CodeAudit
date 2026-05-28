package com.codeaudit.service;

import com.codeaudit.entity.Project;
import com.codeaudit.entity.Review;
import com.codeaudit.event.ReviewCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * PR 审查服务 — 监听审查完成事件并将结果回写到 GitHub PR
 * <p>
 * 流程：
 * <ol>
 *   <li>WebhookController 收到 PR 事件后，创建 Review 并关联 prNumber/prUrl</li>
 *   <li>ReviewEngine 完成审查后发布 ReviewCompletedEvent</li>
 *   <li>本服务监听到事件，判断是否为 PR 触发的审查（prNumber != null）</li>
 *   <li>若属于 GITHUB 项目的 PR，则构建 Markdown 摘要并调用 GitHub API 发布评论</li>
 * </ol>
 *
 * @author CodeAudit Team
 */
@Service
public class PrReviewService {

    private static final Logger log = LoggerFactory.getLogger(PrReviewService.class);

    private final GithubService githubService;

    public PrReviewService(GithubService githubService) {
        this.githubService = githubService;
    }

    /**
     * 监听审查完成事件，若审查关联了 GitHub PR，则自动发布评论
     * <p>
     * 异步执行，不阻塞审查主流程。
     */
    @Async
    @EventListener
    public void onReviewCompleted(ReviewCompletedEvent event) {
        Review review = event.getReview();
        if (review.getPrNumber() == null) {
            return;
        }

        Project project = review.getProject();
        if (project == null || !"GITHUB".equalsIgnoreCase(project.getRepoType())) {
            return;
        }
        if (project.getRepoUrl() == null || project.getRepoUrl().isBlank()) {
            log.warn("GitHub 项目缺少 repoUrl: projectId={}", project.getId());
            return;
        }

        try {
            String markdown = githubService.buildReviewSummaryMarkdown(review);
            String commentUrl = githubService.postPullRequestComment(
                    project.getRepoUrl(), review.getPrNumber(), markdown);
            log.info("PR 审查结果已发布: reviewId={}, prUrl={}, commentUrl={}",
                    review.getId(), review.getPrUrl(), commentUrl);
        } catch (Exception e) {
            log.error("发布 PR 审查结果失败: reviewId={}, prNumber={}, error={}",
                    review.getId(), review.getPrNumber(), e.getMessage());
        }
    }
}
