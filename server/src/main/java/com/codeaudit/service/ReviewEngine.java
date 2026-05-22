package com.codeaudit.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.codeaudit.dto.DiffBlock;
import com.codeaudit.dto.ReviewResult;
import com.codeaudit.entity.Issue;
import com.codeaudit.entity.Review;
import com.codeaudit.entity.Rule;
import com.codeaudit.repository.IssueRepository;
import com.codeaudit.repository.ReviewRepository;
import com.codeaudit.repository.RuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    public ReviewEngine(AiChatService aiChatService,
                        AiConfigService aiConfigService,
                        RuleRepository ruleRepository,
                        ReviewRepository reviewRepository,
                        IssueRepository issueRepository,
                        ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.aiConfigService = aiConfigService;
        this.ruleRepository = ruleRepository;
        this.reviewRepository = reviewRepository;
        this.issueRepository = issueRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步执行 AI 审查任务
     * <p>
     * 文件级并行：用固定线程池 + CompletableFuture 并行处理 diff 文件，
     * 并行度由 {@code codeaudit.ai.review.parallelism} 控制（默认 2，上限 8）。
     */
    @Async("reviewExecutor")
    public void executeReview(Review review, List<DiffBlock> diffBlocks, String language) {
        long startTime = System.currentTimeMillis();
        Review attachedReview = null;
        try {
            attachedReview = reviewRepository.findById(review.getId())
                    .orElseThrow(() -> new IllegalStateException("审查任务不存在: " + review.getId()));
            attachedReview.setStatus("processing");
            reviewRepository.save(attachedReview);
            log.info("开始审查: reviewId={}, 文件数={}, 并行度={}",
                    attachedReview.getId(), diffBlocks.size(), aiConfigService.getParallelism());

            if (diffBlocks.isEmpty()) {
                attachedReview.setStatus("completed");
                attachedReview.setErrorMessage("无待审查的变更文件");
                reviewRepository.save(attachedReview);
                return;
            }

            List<Rule> enabledRules = ruleRepository.findByIsEnabledTrue();
            log.debug("已加载 {} 条启用规则", enabledRules.size());

            final Review finalReview = attachedReview;

            // 并行审查
            int parallelism = Math.max(1, Math.min(diffBlocks.size(), aiConfigService.getParallelism()));
            ExecutorService executor = Executors.newFixedThreadPool(parallelism);

            AtomicInteger highCount = new AtomicInteger(0);
            AtomicInteger mediumCount = new AtomicInteger(0);
            AtomicInteger lowCount = new AtomicInteger(0);
            AtomicInteger reviewedFiles = new AtomicInteger(0);
            List<Issue> issueBatch = Collections.synchronizedList(new ArrayList<>());

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (DiffBlock diffBlock : diffBlocks) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    log.debug("审查文件: {}", diffBlock.filePath());
                    String prompt = buildReviewPrompt(diffBlock, enabledRules, language);
                    String response = aiChatService.chat(prompt);
                    ReviewResult result = parseReviewResponse(response);

                    if (result != null && result.issues() != null) {
                        for (ReviewResult.IssueItem item : result.issues()) {
                            Issue issue = Issue.builder()
                                    .review(finalReview)
                                    .filePath(diffBlock.filePath())
                                    .lineNumber(item.line())
                                    .severity(item.severity())
                                    .category(item.category())
                                    .message(item.message())
                                    .suggestion(item.suggestion())
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
                    finalReview.setReviewedFiles(done);
                    reviewRepository.save(finalReview);
                    log.debug("文件审查完成 [{}/{}]: {}", done, diffBlocks.size(), diffBlock.filePath());
                }, executor);

                futures.add(future);
            }

            // 等待全部完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            executor.shutdown();
            try { executor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

            if (!issueBatch.isEmpty()) {
                issueRepository.saveAll(issueBatch);
            }

            attachedReview.setHighCount(highCount.get());
            attachedReview.setMediumCount(mediumCount.get());
            attachedReview.setLowCount(lowCount.get());
            attachedReview.setTotalIssues(highCount.get() + mediumCount.get() + lowCount.get());
            attachedReview.setStatus("completed");
            attachedReview.setReviewedFiles(diffBlocks.size());
            reviewRepository.save(attachedReview);
            log.info("审查完成: reviewId={}, 问题数: HIGH={} MEDIUM={} LOW={}",
                    attachedReview.getId(), highCount.get(), mediumCount.get(), lowCount.get());

        } catch (Exception e) {
            log.error("审查失败: reviewId={}, 错误: {}", review.getId(), e.getMessage(), e);
            attachedReview = attachedReview != null ? reviewRepository.findById(review.getId()).orElse(attachedReview) : reviewRepository.findById(review.getId()).orElse(review);
            attachedReview.setStatus("failed");
            attachedReview.setErrorMessage(e.getMessage());
            reviewRepository.save(attachedReview);
        } finally {
            attachedReview = attachedReview != null ? reviewRepository.findById(review.getId()).orElse(attachedReview) : reviewRepository.findById(review.getId()).orElse(review);
            attachedReview.setDurationMs(System.currentTimeMillis() - startTime);
            reviewRepository.save(attachedReview);
        }
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
        try {
            String json = response.trim();
            Matcher matcher = JSON_CODE_BLOCK.matcher(json);
            if (matcher.find()) {
                json = matcher.group(1).trim();
            }
            return objectMapper.readValue(json, ReviewResult.class);
        } catch (JsonProcessingException e) {
            log.warn("LLM 返回的 JSON 解析失败，原始响应: {}", response);
            return new ReviewResult(List.of());
        }
    }
}
