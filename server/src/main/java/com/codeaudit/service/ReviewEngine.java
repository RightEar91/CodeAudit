package com.codeaudit.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.codeaudit.dto.DiffBlock;
import com.codeaudit.dto.ReviewResult;
import com.codeaudit.entity.Issue;
import com.codeaudit.entity.Review;
import com.codeaudit.entity.Rule;
import com.codeaudit.event.ReviewCompletedEvent;
import com.codeaudit.event.ReviewProgressEvent;
import com.codeaudit.repository.IssueRepository;
import com.codeaudit.repository.ReviewRepository;
import com.codeaudit.repository.RuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * AI 审查引擎 — 核心审查逻辑
 * <p>
 * 负责将代码 diff 送入 AI 进行分析，并解析返回的结构化 JSON，写入数据库。
 * <p>
 * 支持文件级并行审查：通过 {@link CompletableFuture} 并行调用 AI，
 * 并行度由 {@code codeaudit.ai.review.parallelism} 配置。
 *
 * @author CodeAudit Team
 */
@Service
public class ReviewEngine {

    private static final Logger log = LoggerFactory.getLogger(ReviewEngine.class);
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");

    private final AiChatService aiChatService;
    private final AiConfigService aiConfigService;
    private final RuleRepository ruleRepository;
    private final ReviewRepository reviewRepository;
    private final IssueRepository issueRepository;
    private final ObjectMapper objectMapper;
    private final Executor reviewExecutor;
    private final ApplicationEventPublisher eventPublisher;
    private final BlameService blameService;
    private final GitService gitService;
    @Nullable
    private final MultiModelService multiModelService;
    private final ContextLoaderService contextLoaderService;
    private final RAGService ragService;
    private final FeedbackService feedbackService;
    private final AutoFixService autoFixService;

    /** 取消令牌注册表：key=reviewId, value=是否已取消 */
    private final ConcurrentHashMap<Long, Boolean> cancellationTokens = new ConcurrentHashMap<>();

    public ReviewEngine(AiChatService aiChatService,
                        AiConfigService aiConfigService,
                        RuleRepository ruleRepository,
                        ReviewRepository reviewRepository,
                        IssueRepository issueRepository,
                        ObjectMapper objectMapper,
                        @Qualifier("reviewExecutor") Executor reviewExecutor,
                        ApplicationEventPublisher eventPublisher,
                        BlameService blameService,
                        GitService gitService,
                        @Nullable MultiModelService multiModelService,
                        ContextLoaderService contextLoaderService,
                        RAGService ragService,
                        FeedbackService feedbackService,
                        AutoFixService autoFixService) {
        this.aiChatService = aiChatService;
        this.aiConfigService = aiConfigService;
        this.ruleRepository = ruleRepository;
        this.reviewRepository = reviewRepository;
        this.issueRepository = issueRepository;
        this.objectMapper = objectMapper;
        this.reviewExecutor = reviewExecutor;
        this.eventPublisher = eventPublisher;
        this.blameService = blameService;
        this.gitService = gitService;
        this.multiModelService = multiModelService;
        this.contextLoaderService = contextLoaderService;
        this.ragService = ragService;
        this.feedbackService = feedbackService;
        this.autoFixService = autoFixService;
    }

    /**
     * 标记审查任务为已取消，异步执行中的 {@link #executeReview} 会检测此标记并提前终止
     */
    public void requestCancellation(Long reviewId) {
        cancellationTokens.put(reviewId, true);
    }

    /**
     * 检查指定审查是否已被取消
     */
    private boolean isCancelled(Long reviewId) {
        return Boolean.TRUE.equals(cancellationTokens.get(reviewId));
    }

    /**
     * 清理取消令牌（审查正常结束或被取消后调用）
     */
    private void clearCancellation(Long reviewId) {
        cancellationTokens.remove(reviewId);
    }

    /**
     * 异步执行 AI 审查任务
     * <p>
     * 文件级并行：用 CompletableFuture + 共享线程池 reviewExecutor 并行处理 diff 文件。
     * 通过 {@code codeaudit.ai.review.parallelism} 控制并发度。
     */
    @Async("reviewExecutor")
    // 不使用 @Transactional：避免长事务持有 InnoDB 行锁导致 cancel() 超时
    // 各 JPA 操作会自动使用短事务（Spring Data 默认 @Transactional）
    public void executeReview(Review review, List<DiffBlock> diffBlocks, String language) {
        long startTime = System.currentTimeMillis();
        Review attachedReview;
        try {
            attachedReview = reviewRepository.findById(review.getId())
                    .orElseThrow(() -> new IllegalStateException("审查任务不存在: " + review.getId()));

            // 检查终态：可能在事务提交后的极短时间内被取消
            if ("failed".equals(attachedReview.getStatus()) || "completed".equals(attachedReview.getStatus())) {
                log.info("审查已被取消或已完成，跳过执行: reviewId={}, status={}",
                        review.getId(), attachedReview.getStatus());
                clearCancellation(review.getId());
                return;
            }

            // 兜底：如果状态不是 processing（异常恢复场景）
            if (!"processing".equals(attachedReview.getStatus())) {
                attachedReview.setStatus("processing");
                reviewRepository.saveAndFlush(attachedReview);
            }

            log.info("开始审查: reviewId={}, 文件数={}, 并行度={}",
                    attachedReview.getId(), diffBlocks.size(), aiConfigService.getParallelism());

            final Long reviewId = attachedReview.getId();

            if (diffBlocks.isEmpty()) {
                attachedReview.setStatus("completed");
                attachedReview.setErrorMessage("无待审查的变更文件");
                reviewRepository.saveAndFlush(attachedReview);
                return;
            }

            List<Rule> enabledRules = ruleRepository.findByIsEnabledTrue();
            log.debug("已加载 {} 条启用规则", enabledRules.size());

            final Long projectId = attachedReview.getProject().getId();
            final String repoPath = gitService.resolveRepoPath(attachedReview.getProject());

            AtomicInteger highCount = new AtomicInteger(0);
            AtomicInteger mediumCount = new AtomicInteger(0);
            AtomicInteger lowCount = new AtomicInteger(0);
            AtomicInteger reviewedFiles = new AtomicInteger(0);
            List<Issue> issueBatch = Collections.synchronizedList(new ArrayList<>());

            // Load few-shot examples from historical feedback
            String fewShotPrompt = feedbackService.buildFewShotPrompt(projectId);

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (DiffBlock diffBlock : diffBlocks) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    // 检查是否已被取消，取消则跳过当前文件
                    if (isCancelled(reviewId)) {
                        log.info("审查已取消，跳过文件: {}", diffBlock.filePath());
                        return;
                    }

                    log.debug("审查文件: {}", diffBlock.filePath());

                    // Load context files for cross-file analysis
                    Map<String, String> contextFiles = contextLoaderService.loadContext(
                            repoPath, diffBlock.filePath(), diffBlock.diffContent());

                    // Retrieve relevant knowledge docs via RAG
                    String ragContext = ragService.retrieveKnowledge(projectId, diffBlock.diffContent());

                    // Build enhanced prompt with context + RAG + few-shot
                    String prompt = buildEnhancedReviewPrompt(diffBlock, enabledRules, language,
                            contextFiles, ragContext, fewShotPrompt);

                    String response;
                    ReviewResult result;

                    // Use multi-model comparison if Ollama provider and available
                    if ("ollama".equals(aiConfigService.getProvider()) && multiModelService != null) {
                        List<ReviewResult.IssueItem> merged = multiModelService.multiModelReview(prompt);
                        result = new ReviewResult(merged);
                    } else {
                        response = aiChatService.chat(prompt);
                        result = parseReviewResponse(response);
                    }

                    if (result != null && result.issues() != null) {
                        for (ReviewResult.IssueItem item : result.issues()) {
                            Issue issue = Issue.builder()
                                    .review(reviewRepository.getReferenceById(reviewId))
                                    .filePath(diffBlock.filePath())
                                    .lineNumber(item.line())
                                    .severity(item.severity())
                                    .category(item.category())
                                    .message(item.message())
                                    .suggestion(item.suggestion())
                                    .modelCount(item.modelCount() != null ? item.modelCount() : 1)
                                    .totalModelCount(item.totalModelCount() != null ? item.totalModelCount() : 1)
                                    .models(item.models() != null ? item.models() : "default")
                                    .build();
                            issueBatch.add(issue);

                            switch (item.severity().toUpperCase()) {
                                case "HIGH" -> highCount.incrementAndGet();
                                case "MEDIUM" -> mediumCount.incrementAndGet();
                                case "LOW" -> lowCount.incrementAndGet();
                            }
                        }
                    }

                    int done = reviewedFiles.incrementAndGet();
                    eventPublisher.publishEvent(new ReviewProgressEvent(
                            reviewId, done, diffBlocks.size(), diffBlock.filePath(),
                            "progress", "processing", 0, 0, 0, 0, null));
                    log.debug("文件审查完成 [{}/{}]: {}", done, diffBlocks.size(), diffBlock.filePath());
                }, reviewExecutor);

                futures.add(future);
            }

            // 等待所有文件审查完成，带超时保护（每文件最多 3 分钟，最少 10 分钟）
            long timeoutSeconds = Math.max(600, diffBlocks.size() * 180L / Math.max(1, aiConfigService.getParallelism()));
            log.info("等待 {} 个文件审查完成，超时阈值: {} 秒", diffBlocks.size(), timeoutSeconds);
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("审查超时（{}秒），部分文件可能未完成: reviewId={}", timeoutSeconds, reviewId);
            } catch (Exception e) {
                log.error("审查执行异常: reviewId={}", reviewId, e);
                throw e;
            }

            attachedReview = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalStateException("审查任务消失: " + reviewId));

            // 检查是否被取消：如果已被取消则不覆盖为 completed
            if (isCancelled(reviewId)) {
                attachedReview.setReviewedFiles(reviewedFiles.get());
                attachedReview.setDurationMs(System.currentTimeMillis() - startTime);
                // 兜底：cancelImmediate 的异步 DB 写可能尚未完成，确保状态为 failed
                if (!"failed".equals(attachedReview.getStatus())) {
                    attachedReview.setStatus("failed");
                    attachedReview.setErrorMessage("用户手动取消");
                }
                reviewRepository.saveAndFlush(attachedReview);
                log.info("审查已取消（状态保持为 failed）: reviewId={}", reviewId);
                clearCancellation(reviewId);
                return;
            }

            if (!issueBatch.isEmpty()) {
                issueRepository.saveAll(issueBatch);
            }

            // 审查完成后通过 git blame 自动关联代码作者
            try {
                blameService.fillBlameInfo(repoPath, attachedReview.getToRef(), issueBatch);
                if (!issueBatch.isEmpty()) {
                    issueRepository.saveAll(issueBatch);
                }
            } catch (Exception e) {
                log.warn("Git blame 执行失败，不影响审查结果: reviewId={}", reviewId, e);
            }

            attachedReview = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new IllegalStateException("审查任务消失: " + reviewId));
            attachedReview.setHighCount(highCount.get());
            attachedReview.setMediumCount(mediumCount.get());
            attachedReview.setLowCount(lowCount.get());
            attachedReview.setTotalIssues(highCount.get() + mediumCount.get() + lowCount.get());
            attachedReview.setStatus("completed");
            attachedReview.setReviewedFiles(diffBlocks.size());
            attachedReview.setDurationMs(System.currentTimeMillis() - startTime);
            reviewRepository.saveAndFlush(attachedReview);
            log.info("审查完成: reviewId={}, 问题数: HIGH={} MEDIUM={} LOW={}",
                    attachedReview.getId(), highCount.get(), mediumCount.get(), lowCount.get());
            clearCancellation(reviewId);

            eventPublisher.publishEvent(new ReviewProgressEvent(
                    attachedReview.getId(), diffBlocks.size(), diffBlocks.size(), null,
                    "completed", "completed",
                    highCount.get(), mediumCount.get(), lowCount.get(),
                    attachedReview.getDurationMs(), null));
            eventPublisher.publishEvent(new ReviewCompletedEvent(attachedReview));

            // Asynchronously generate auto-fix suggestions for all issues
            if (!issueBatch.isEmpty()) {
                try {
                    autoFixService.generateFixes(issueBatch);
                } catch (Exception e) {
                    log.warn("Auto-fix 生成任务提交失败", e);
                }
            }

        } catch (Exception e) {
            log.error("审查失败: reviewId={}, 错误: {}", review.getId(), e.getMessage(), e);
            try {
                attachedReview = reviewRepository.findById(review.getId()).orElse(null);
                if (attachedReview != null) {
                    attachedReview.setStatus("failed");
                    attachedReview.setErrorMessage(e.getMessage());
                    attachedReview.setDurationMs(System.currentTimeMillis() - startTime);
                    reviewRepository.saveAndFlush(attachedReview);
                }
                eventPublisher.publishEvent(new ReviewProgressEvent(
                        review.getId(), 0, diffBlocks.size(), null,
                        "failed", "failed", 0, 0, 0, 0, e.getMessage()));
            } catch (Exception inner) {
                log.error("无法更新审查失败状态: reviewId={}", review.getId(), inner);
            }
            clearCancellation(review.getId());
        }
    }

    /**
     * Enhanced review prompt with context files, RAG knowledge, and few-shot examples.
     */
    private String buildEnhancedReviewPrompt(DiffBlock diffBlock, List<Rule> rules, String language,
                                             Map<String, String> contextFiles,
                                             String ragContext, String fewShotPrompt) {
        String basePrompt = buildReviewPrompt(diffBlock, rules, language);

        StringBuilder sb = new StringBuilder(basePrompt);

        if (!contextFiles.isEmpty()) {
            sb.append(contextLoaderService.buildContextSnippet(contextFiles));
        }

        if (ragContext != null && !ragContext.isBlank()) {
            sb.append(ragContext);
        }

        if (fewShotPrompt != null && !fewShotPrompt.isBlank()) {
            sb.append(fewShotPrompt);
        }

        return sb.toString();
    }

    private String buildReviewPrompt(DiffBlock diffBlock, List<Rule> rules, String language) {
        String lang = (language != null && !language.isBlank()) ? language : "Java";
        String rulesText = rules.stream()
                .map(r -> "- " + r.getName() + ": " + r.getPrompt())
                .collect(Collectors.joining("\n"));

        return """
           你是一名资深%s代码审查专家。请审查以下代码变更，找出安全漏洞、性能问题、代码规范违规。

           审查规则：
           %s

           代码文件：%s
           变更类型：%s

           变更代码：
           ```%s
           %s
           ```

           请严格按以下JSON格式返回审查结果（不要包含markdown代码块标记）：
           {
             "issues": [
               {
                 "severity": "HIGH|MEDIUM|LOW",
                 "category": "SECURITY|PERFORMANCE|STYLE|BUG",
                 "line": 行号,
                 "message": "问题描述",
                 "suggestion": "修复建议"
               }
             ]
           }

           如果没有发现问题，返回 {"issues": []}
           """.formatted(
                   lang, rulesText, diffBlock.filePath(), diffBlock.changeType(),
                   lang.toLowerCase(), diffBlock.diffContent()
           );
    }

    private ReviewResult parseReviewResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("LLM 返回空响应");
            return new ReviewResult(List.of());
        }
        try {
            String json = response.trim();
            Matcher matcher = JSON_CODE_BLOCK.matcher(json);
            if (matcher.find()) {
                json = matcher.group(1).trim();
            }
            return objectMapper.readValue(json, ReviewResult.class);
        } catch (Exception e) {
            log.warn("LLM 返回的 JSON 解析失败，原始响应: {}", response, e);
            return new ReviewResult(List.of());
        }
    }
}
