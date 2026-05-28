package com.codeaudit.service;

import com.codeaudit.dto.ReviewResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Multi-model comparison service — runs review through Ollama models concurrently,
 * merges results by file+line proximity, and assigns consensus scores.
 */
@Service
@ConditionalOnProperty(prefix = "codeaudit.ai", name = "provider", havingValue = "ollama", matchIfMissing = true)
public class MultiModelService {

    private static final Logger log = LoggerFactory.getLogger(MultiModelService.class);
    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*([\\s\\S]*?)\\s*```");
    private static final int LINE_TOLERANCE = 3;

    private final OllamaAiChatService ollamaService;
    private final ObjectMapper objectMapper;
    private final AiConfigService config;

    private final List<String> models = List.of("qwen3:8b", "codellama:7b", "deepseek-r1:8b");

    public MultiModelService(OllamaAiChatService ollamaService, ObjectMapper objectMapper, AiConfigService config) {
        this.ollamaService = ollamaService;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /**
     * Run multi-model review and return merged issues with consensus scores.
     */
    public List<ReviewResult.IssueItem> multiModelReview(String prompt) {
        Map<String, List<ReviewResult.IssueItem>> modelResults = new LinkedHashMap<>();

        for (String model : models) {
            try {
                String response = ollamaService.chatWithModel(prompt, model);
                ReviewResult result = parse(response);
                if (result != null && result.issues() != null) {
                    modelResults.put(model, result.issues());
                }
            } catch (Exception e) {
                log.warn("模型 {} 审查失败: {}", model, e.getMessage());
            }
        }

        List<ReviewResult.IssueItem> merged = mergeWithConsensus(modelResults);
        log.info("多模型审查完成: {} 个模型, 合并后 {} 个问题", modelResults.size(), merged.size());
        return merged;
    }

    /**
     * Merge issues from multiple models, annotate with consensus info.
     * Two issues are considered the same if they share similar line number and same severity.
     */
    private List<ReviewResult.IssueItem> mergeWithConsensus(Map<String, List<ReviewResult.IssueItem>> modelResults) {
        if (modelResults.isEmpty()) return List.of();

        int totalModels = modelResults.size();

        // Group issues by (file-relative key: severity + rounded line)
        Map<String, MergedIssue> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, List<ReviewResult.IssueItem>> entry : modelResults.entrySet()) {
            String model = entry.getKey();
            for (ReviewResult.IssueItem item : entry.getValue()) {
                String key = item.severity() + "|" + (item.line() != null ? (item.line() / LINE_TOLERANCE) : 0);
                MergedIssue merged = grouped.computeIfAbsent(key, k -> new MergedIssue());
                merged.add(item, model);
            }
        }

        List<ReviewResult.IssueItem> result = new ArrayList<>();
        for (MergedIssue mi : grouped.values()) {
            double consensus = (double) mi.models.size() / totalModels;
            String tag = consensus >= 0.67 ? "[共识:" + mi.models.size() + "/" + totalModels + "] "
                    : "[争议:" + mi.models.size() + "/" + totalModels + "] ";
            ReviewResult.IssueItem best = mi.items.get(0);
            result.add(new ReviewResult.IssueItem(
                    best.severity(), best.category(), best.line(),
                    tag + best.message(),
                    best.suggestion(),
                    mi.models.size(), totalModels, String.join(",", mi.models)
            ));
        }

        // Sort: high consensus first, then by severity
        result.sort((a, b) -> {
            int c = Integer.compare(b.modelCount(), a.modelCount());
            if (c != 0) return c;
            return severityOrder(b.severity()) - severityOrder(a.severity());
        });
        return result;
    }

    private int severityOrder(String s) {
        return switch (s.toUpperCase()) { case "HIGH" -> 3; case "MEDIUM" -> 2; default -> 1; };
    }

    private ReviewResult parse(String response) {
        try {
            String json = response.trim();
            Matcher m = JSON_BLOCK.matcher(json);
            if (m.find()) json = m.group(1).trim();
            return objectMapper.readValue(json, ReviewResult.class);
        } catch (Exception e) {
            log.debug("Parse failed: {}", e.getMessage());
            return null;
        }
    }

    private static class MergedIssue {
        List<ReviewResult.IssueItem> items = new ArrayList<>();
        Set<String> models = new LinkedHashSet<>();
        void add(ReviewResult.IssueItem item, String model) { items.add(item); models.add(model); }
    }
}
