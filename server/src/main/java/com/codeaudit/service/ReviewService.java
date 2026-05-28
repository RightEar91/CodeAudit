package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.dto.DiffBlock;
import com.codeaudit.dto.ReviewFilter;
import com.codeaudit.entity.Project;
import com.codeaudit.entity.Review;
import com.codeaudit.event.ReviewCreatedEvent;
import com.codeaudit.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
    private final GitService gitService;
    private final ReviewEngine reviewEngine;
    private final DiffFilterService diffFilterService;
    private final ApplicationEventPublisher eventPublisher;

    public ReviewService(ReviewRepository reviewRepository,
                         ProjectService projectService,
                         GitDiffService gitDiffService,
                         GitService gitService,
                         ReviewEngine reviewEngine,
                         DiffFilterService diffFilterService,
                         ApplicationEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.projectService = projectService;
        this.gitDiffService = gitDiffService;
        this.gitService = gitService;
        this.reviewEngine = reviewEngine;
        this.diffFilterService = diffFilterService;
        this.eventPublisher = eventPublisher;
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
    @Transactional(readOnly = true)
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
        return create(projectId, title, fromRef, toRef, null, null);
    }

    /**
     * 创建审查任务（带 PR 信息，用于 Webhook 触发的 PR 审查）
     */
    @Transactional
    public Review create(Long projectId, String title, String fromRef, String toRef,
                         Integer prNumber, String prUrl) {
        return doCreate(projectId, title, fromRef, toRef, prNumber, prUrl, null);
    }

    /**
     * 创建审查任务（带过滤条件，支持目录/文件类型/作者/时间段过滤）
     *
     * @param projectId 项目 ID
     * @param title     审查标题
     * @param fromRef   源引用
     * @param toRef     目标引用
     * @param filter    过滤条件（可为 null）
     * @return 新创建的 Review
     */
    @Transactional
    public Review create(Long projectId, String title, String fromRef, String toRef, ReviewFilter filter) {
        return doCreate(projectId, title, fromRef, toRef, null, null, filter);
    }

    /**
     * 核心创建逻辑：统一处理无过滤/带过滤/PR 三种场景
     */
    private Review doCreate(Long projectId, String title, String fromRef, String toRef,
                            Integer prNumber, String prUrl, ReviewFilter filter) {
        Project project = projectService.findById(projectId)
                .orElseThrow(() -> new BizException(404, "项目不存在: " + projectId));

        Review.ReviewBuilder builder = Review.builder()
                .project(project)
                .title(title)
                .fromRef(fromRef != null ? fromRef : "HEAD~1")
                .toRef(toRef != null ? toRef : "HEAD")
                .status("pending");
        if (prNumber != null) {
            builder.prNumber(prNumber).prUrl(prUrl);
        }
        Review review = builder.build();
        review = reviewRepository.save(review);
        log.info("创建审查: reviewId={}, 范围={}..{}{}{}",
                review.getId(), review.getFromRef(), review.getToRef(),
                prNumber != null ? ", PR=#" + prNumber : "",
                filter != null && filter.hasAnyFilter() ? ", 含过滤条件" : "");

        String effectiveRepoPath = gitService.resolveRepoPath(project);
        String language = project.getLanguage();

        List<DiffBlock> diffBlocks = gitService.getFilteredDiffBlocks(
                effectiveRepoPath, review.getFromRef(), review.getToRef(), language, filter);

        diffBlocks = diffFilterService.applyFilters(diffBlocks, filter);

        if (diffBlocks.isEmpty()) {
            log.warn("审查范围内无{}文件变更: reviewId={}", language, review.getId());
        }

        review.setTotalFiles(diffBlocks.size());
        review.setStatus("processing");
        review = reviewRepository.save(review);

        log.info("审查已提交，等待事务提交后异步执行: reviewId={}", review.getId());

        eventPublisher.publishEvent(new ReviewCreatedEvent(review, diffBlocks, language));

        return review;
    }

    /**
     * 监听审查创建事件，在事务提交后异步启动 AI 审查
     * <p>
     * 使用 @TransactionalEventListener 替代手动的 TransactionSynchronizationManager，
     * 确保事务成功提交后才触发，避免审查卡在 pending 状态。
     * <p>
     * fallbackExecution = true：若因任何原因事务不存在/未提交，也立即执行，以防事件丢失。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReviewCreated(ReviewCreatedEvent event) {
        log.info("事务已提交，启动异步审查: reviewId={}", event.getReview().getId());
        reviewEngine.executeReview(event.getReview(), event.getDiffBlocks(), event.getLanguage());
    }

    /**
     * 取消进行中的审查（立即返回，DB 写操作异步执行）
     * <p>
     * 仅 pending 或 processing 状态的审查可被取消。
     * 先同步设置取消令牌（ConcurrentHashMap 操作，永不阻塞），
     * 再异步执行数据库状态更新，避免因 DB 锁竞争导致 HTTP 响应超时。
     *
     * @param reviewId 审查 ID
     * @throws BizException 审查不存在时抛出（404）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(404, "审查不存在: " + reviewId));
        if ("processing".equals(review.getStatus()) || "pending".equals(review.getStatus())) {
            review.setStatus("failed");
            review.setErrorMessage("用户手动取消");
            reviewRepository.save(review);
            reviewEngine.requestCancellation(reviewId);
            log.info("审查已取消: reviewId={}", reviewId);
        } else {
            throw new BizException("当前状态不可取消: " + review.getStatus());
        }
    }

    /**
     * 立即取消审查（非事务版本，用于 REST 端点立即返回）
     * <p>
     * 先同步读取并校验状态，设置取消令牌（永不阻塞），
     * 将 DB 写操作提交到 ForkJoinPool 异步执行。
     * 即使 DB 写失败，executeReview 也会在结束时检测到 isCancelled=true 并保持 failed 状态。
     */
    public void cancelImmediate(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BizException(404, "审查不存在: " + reviewId));
        String status = review.getStatus();
        if (!"processing".equals(status) && !"pending".equals(status)) {
            throw new BizException("当前状态不可取消: " + status);
        }
        reviewEngine.requestCancellation(reviewId);
        log.info("审查取消令牌已设置: reviewId={}", reviewId);

        CompletableFuture.runAsync(() -> {
            try {
                Review latest = reviewRepository.findById(reviewId).orElse(null);
                if (latest != null && ("processing".equals(latest.getStatus()) || "pending".equals(latest.getStatus()))) {
                    latest.setStatus("failed");
                    latest.setErrorMessage("用户手动取消");
                    reviewRepository.save(latest);
                    log.info("审查状态已更新为 failed: reviewId={}", reviewId);
                }
            } catch (Exception e) {
                log.warn("异步更新审查状态失败（不影响取消效果）: reviewId={}, error={}", reviewId, e.getMessage());
            }
        });
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
