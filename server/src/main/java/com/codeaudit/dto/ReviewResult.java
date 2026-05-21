package com.codeaudit.dto;

import java.util.List;

/**
 * AI 审查返回的结果结构（LLM JSON 响应映射）
 * <p>
 * 顶层 key 为 "issues"，包含问题项数组。
 * 如果审查未发现问题，issues 为空列表。
 *
 * @param issues AI 发现的问题列表
 *
 * @author CodeAudit Team
 */
public record ReviewResult(
        List<IssueItem> issues
) {
    /**
     * 单条审查问题项，对应 LLM 返回 JSON 中 issues 数组的每个元素。
     *
     * @param severity   严重程度（HIGH / MEDIUM / LOW）
     * @param category   问题分类（SECURITY / PERFORMANCE / STYLE / BUG）
     * @param line       问题行号
     * @param message    问题描述
     * @param suggestion 修复建议
     */
    public record IssueItem(
            String severity,
            String category,
            Integer line,
            String message,
            String suggestion
    ) {}
}
