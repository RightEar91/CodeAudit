package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.dto.DiffBlock;
import com.codeaudit.entity.Project;
import com.codeaudit.entity.Review;
import com.codeaudit.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

/**
 * 审查任务管理服务
 * <p>
 * 负责审查任务的创建、查询、取消和删除。
 * 创建审查时会自动：
 * <ol>
 *   <li>校验项目是否存在</li>
 *   <li>提取指定 diff 范围的变更文件</li>
 *   <li>创建 pending 状态的 Review 并异步提交给 ReviewEngine 执行</li>
 *   <li>立即返回 Review 对象（此时 status=pending）</li>
 * </ol>
 * <p>
 * 调用方通过轮询 Review.status 感知审查进展。
 *
 * @author CodeAudit Team
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final ProjectService projectService;
    private final GitDiffService gitDiffService;
    private final ReviewEngine reviewEngine;

    public ReviewService(ReviewRepository reviewRepository,
                         ProjectService projectService,
                         GitDiffService gitDiffService,
                         ReviewEngine reviewEngine) {
        this.reviewRepository = reviewRepository;
        this.projectService = projectService;
        this.gitDiffService = gitDiffService;
        this.reviewEngine = reviewEngine;
    }

    /**
     * 查询某项目的所有审查记录，按创建时间降序
     */
    public List<Review> listByProjectId(Long projectId) {
        return reviewRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    /**
     * 分页查询某项目的审查记录，按创建时间降序
     */
    public Page<Review> listByProjectId(Long projectId, Pageable pageable) {
        return reviewRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable);
    }

    /**
     * 按 ID 查询审查详情（含所有关联 Issue）
     */
    public Optional<Review> findById(Long id) {
        return reviewRepository.findById(id);
    }

    /**
     * 创建并异步执行一次审查
     * <p>
     * 完整流程：
     * <ol>
     *   <li>校验项目存在</li>
     *   <li>创建 pending 状态的 Review 条目</li>
     *   <li>通过 GitDiffService 提取 diff 数据</li>
     *   <li>异步提交给 ReviewEngine 执行 AI 分析</li>
     *   <li>立即返回 Review（调用方通过 status 轮询结果）</li>
     * </ol>
     *
     * @param projectId 项目 ID
     * @param title     审查标题
     * @param fromRef   源引用（默认为 HEAD~1）
     * @param toRef     目标引用（默认为 HEAD）
     * @return 新创建的 Review（status=pending）
     * @throws BizException 项目不存在时抛出（404）
     */
    @Transactional
    public Review create(Long projectId, String title, String fromRef, String toRef) {
        // 1. 校验项目存在
        Project project = projectService.findById(projectId)
                .orElseThrow(() -> new BizException(404, "项目不存在: " + projectId));

        // 2. 创建 pending 状态的 Review
        Review review = Review.builder()
                .project(project)
                .title(title)
                .fromRef(fromRef != null ? fromRef : "HEAD~1")
                .toRef(toRef != null ? toRef : "HEAD")
                .status("pending")
                .build();
        review = reviewRepository.save(review);
        log.info("创建审查: reviewId={}, 范围={}..{}", review.getId(), review.getFromRef(), review.getToRef());

        // 3. 提取 diff（同步执行，保证合法性校验在返回前完成）
        List<DiffBlock> diffBlocks = gitDiffService.extractDiffBetweenCommits(
                project.getRepoPath(), review.getFromRef(), review.getToRef(), project.getLanguage());

        if (diffBlocks.isEmpty()) {
            log.warn("审查范围内无{}文件变更: reviewId={}", project.getLanguage(), review.getId());
        }

        // 3.5 回写文件总数，供前端展示进度（如 3/12 文件）
        review.setTotalFiles(diffBlocks.size());
        review = reviewRepository.save(review);

        // 4. 注册事务提交后回调：确保 Review 已持久化到 DB 后再触发异步审查
        final Review finalReview = review;
        final List<DiffBlock> finalDiffBlocks = diffBlocks;
        final String language = project.getLanguage();
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        reviewEngine.executeReview(finalReview, finalDiffBlocks, language);
                    }
                });

        return review;
    }

    /**
     * 取消进行中的审查
     * <p>
     * 仅 pending 或 processing 状态的审查可被取消，
     * 取消后状态置为 failed 并备注"用户手动取消"。
     *
     * @param reviewId 审查 ID
     * @throws BizException 审查不存在时抛出（404）
     */
    @Transactional
    public void cancel(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(404, "审查不存在: " + reviewId));
        if ("processing".equals(review.getStatus()) || "pending".equals(review.getStatus())) {
            review.setStatus("failed");
            review.setErrorMessage("用户手动取消");
            reviewRepository.save(review);
            log.info("审查已取消: reviewId={}", reviewId);
        } else {
            throw new BizException("当前状态不可取消: " + review.getStatus());
        }
    }

    /**
     * 删除审查记录（级联删除关联的所有 Issue）
     *
     * @param reviewId 审查 ID
     */
    @Transactional
    public void delete(Long reviewId) {
        log.info("删除审查: reviewId={}", reviewId);
        reviewRepository.deleteById(reviewId);
    }
}
