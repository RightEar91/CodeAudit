package com.codeaudit.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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
 * 负责将代码 diff 送入本地 Ollama（Qwen3:8b）进行 AI 分析，
 * 并解析返回的结构化 JSON，写入数据库。
 * <p>
 * 审查流程：
 * <ol>
 *   <li>获取所有已启用的审查规则</li>
 *   <li>逐文件构建审查 Prompt（规则 + diff 内容）</li>
 *   <li>调用 Ollama LLM 获取 JSON 响应</li>
 *   <li>解析 JSON 并将问题写入 ca_issues 表</li>
 *   <li>汇总严重度计数并更新 Review 状态为 completed/failed</li>
 * </ol>
 * <p>
 * 该方法由 {@link org.springframework.scheduling.annotation.Async} 标记，
 * 在独立线程池中异步执行，避免阻塞 HTTP 请求线程。
 *
 * @author CodeAudit Team
 */
@Service
public class ReviewEngine {

    private static final Logger log = LoggerFactory.getLogger(ReviewEngine.class);
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");

    private final ChatClient chatClient;
    private final RuleRepository ruleRepository;
    private final ReviewRepository reviewRepository;
    private final IssueRepository issueRepository;
    private final ObjectMapper objectMapper;

    public ReviewEngine(ChatClient.Builder chatClientBuilder,
                        RuleRepository ruleRepository,
                        ReviewRepository reviewRepository,
                        IssueRepository issueRepository,
                        ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.ruleRepository = ruleRepository;
        this.reviewRepository = reviewRepository;
        this.issueRepository = issueRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步执行 AI 审查任务
     * <p>
     * 审查流程：
     * <ol>
     *   <li>将 Review 状态更新为 processing</li>
     *   <li>获取所有已启用的审查规则</li>
     *   <li>逐文件构建 Prompt 并调用 Ollama</li>
     *   <li>解析 LLM 返回的 JSON → 写入 Issue 实体</li>
     *   <li>统计 HIGH/MEDIUM/LOW 数量并更新 Review 汇总字段</li>
     * </ol>
     * <p>
     * 任何阶段发生异常都会将 Review 状态置为 failed 并记录错误信息。
     * 整个过程的耗时会被记录在 Review.durationMs 中。
     *
     * @param review     审查任务实体（传入时 status=pending）
     * @param diffBlocks 待审查的 diff 数据块列表
     */
    @Async("reviewExecutor")
    public void executeReview(Review review, List<DiffBlock> diffBlocks, String language) {
        long startTime = System.currentTimeMillis();
        try {
            // 1. 标记处理中（重新从数据库加载，确保即时刷新）
            Review attachedReview = reviewRepository.findById(review.getId())
                    .orElseThrow(() -> new IllegalStateException("审查任务不存在: " + review.getId()));
            attachedReview.setStatus("processing");
            reviewRepository.save(attachedReview);
            log.info("开始审查: reviewId={}, 文件数={}", attachedReview.getId(), diffBlocks.size());

            // 空 diff：直接标记完成，避免无意义调用 LLM
            if (diffBlocks.isEmpty()) {
                attachedReview.setStatus("completed");
                attachedReview.setErrorMessage("无待审查的变更文件");
                log.info("审查完成（无变更文件）: reviewId={}", attachedReview.getId());
                return;
            }

            // 2. 加载已启用的规则
            List<Rule> enabledRules = ruleRepository.findByIsEnabledTrue();
            log.debug("已加载 {} 条启用规则", enabledRules.size());

            // 3. 逐文件审查，汇总各级别问题数
            int highCount = 0, mediumCount = 0, lowCount = 0;
            List<Issue> issueBatch = new ArrayList<>();

            for (DiffBlock diffBlock : diffBlocks) {
                log.debug("审查文件: {}", diffBlock.filePath());

                // 3a. 构建 Prompt 并调用 LLM
                String prompt = buildReviewPrompt(diffBlock, enabledRules, language);
                String response = chatClient.prompt().user(prompt).call().content();

                // 3b. 解析 LLM 返回的 JSON
                ReviewResult result = parseReviewResponse(response);

                // 3c. 将 Issue 收集到批次列表
                if (result != null && result.issues() != null) {
                    for (ReviewResult.IssueItem item : result.issues()) {
                        Issue issue = Issue.builder()
                                .review(attachedReview)
                                .filePath(diffBlock.filePath())
                                .lineNumber(item.line())
                                .severity(item.severity())
                                .category(item.category())
                                .message(item.message())
                                .suggestion(item.suggestion())
                                .build();
                        issueBatch.add(issue);

                        // 按严重度递增计数
                        switch (item.severity().toUpperCase()) {
                            case "HIGH" -> highCount++;
                            case "MEDIUM" -> mediumCount++;
                            case "LOW" -> lowCount++;
                        }
                    }
                }
            }

            // 3d. 批量保存所有 Issue（减少数据库往返）
            if (!issueBatch.isEmpty()) {
                issueRepository.saveAll(issueBatch);
            }

            // 4. 写入汇总统计并标记完成
            attachedReview.setHighCount(highCount);
            attachedReview.setMediumCount(mediumCount);
            attachedReview.setLowCount(lowCount);
            attachedReview.setTotalIssues(highCount + mediumCount + lowCount);
            attachedReview.setStatus("completed");
            log.info("审查完成: reviewId={}, 问题数: HIGH={} MEDIUM={} LOW={}",
                    attachedReview.getId(), highCount, mediumCount, lowCount);

        } catch (Exception e) {
            log.error("审查失败: reviewId={}, 错误: {}", review.getId(), e.getMessage(), e);
            Review attachedReview = reviewRepository.findById(review.getId())
                    .orElse(review);
            attachedReview.setStatus("failed");
            attachedReview.setErrorMessage(e.getMessage());
            reviewRepository.save(attachedReview);
        } finally {
            // 5. 无论成功失败，记录耗时
            Review attachedReview = reviewRepository.findById(review.getId()).orElse(review);
            attachedReview.setDurationMs(System.currentTimeMillis() - startTime);
            reviewRepository.save(attachedReview);
        }
    }

    /**
     * 构建审查 Prompt
     * <p>
     * 将启用的规则列表和 diff 内容组合为结构化的 Prompt 文本，
     * 引导 LLM 以 JSON 格式返回审查结果。
     *
     * @param diffBlock 单个文件的 diff 数据块
     * @param rules     当前启用的审查规则列表
     * @return 完整的 Prompt 文本
     */
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
                   lang,
                   rulesText,
                   diffBlock.filePath(),
                   diffBlock.changeType(),
                   lang.toLowerCase(),
                   diffBlock.diffContent()
           );
    }

    /**
     * 解析 LLM 返回的 JSON 响应
     * <p>
     * 处理 LLM 可能返回的格式变体：
     * <ul>
     *   <li>纯 JSON 字符串</li>
     *   <li>被 ```json ... ``` 包裹的 JSON</li>
     * </ul>
     * 解析失败时返回空的 ReviewResult 而非抛出异常，
     * 保证个别文件的解析失败不会中断整体审查。
     *
     * @param response LLM 原始响应文本
     * @return 解析后的 ReviewResult，失败时返回空 issues 列表
     */
    private ReviewResult parseReviewResponse(String response) {
        try {
            String json = response.trim();
            // 处理 LLM 可能额外包裹的 markdown 代码块
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
