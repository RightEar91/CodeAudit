package com.codeaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;

/**
 * 审查问题实体 — 对应数据库表 ca_issues
 * <p>
 * 每条 Issue 是 AI 对某文件某行代码发现的一个具体问题，
 * 包含严重程度、分类、问题描述和修复建议。
 * 用户可标记处理状态（open / resolved / ignored）。
 *
 * @author CodeAudit Team
 */
@Entity
@Table(name = "ca_issues")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属审查任务 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    /** 问题所在的文件路径（相对于仓库根目录） */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /** 问题所在行号（可由 LLM 估算） */
    @Column(name = "line_number")
    private Integer lineNumber;

    /**
     * 严重程度：
     * <ul>
     *   <li>{@code HIGH}   — 高危（安全漏洞、严重 Bug）</li>
     *   <li>{@code MEDIUM} — 中危（性能问题、潜在风险）</li>
     *   <li>{@code LOW}    — 低危（代码规范、风格建议）</li>
     * </ul>
     */
    @Column(nullable = false, length = 20)
    private String severity;

    /**
     * 问题分类：
     * <ul>
     *   <li>{@code SECURITY}    — 安全漏洞</li>
     *   <li>{@code PERFORMANCE} — 性能问题</li>
     *   <li>{@code STYLE}       — 代码规范</li>
     *   <li>{@code BUG}         — 潜在缺陷</li>
     * </ul>
     */
    @Column(nullable = false, length = 30)
    private String category;

    /** 问题的具体描述 */
    @Column(nullable = false, length = 2000)
    private String message;

    /** 修复建议或代码示例 */
    @Column(length = 2000)
    private String suggestion;

    /**
     * 问题处理状态：
     * <ul>
     *   <li>{@code open}     — 未处理</li>
     *   <li>{@code resolved} — 已修复</li>
     *   <li>{@code ignored}  — 已忽略</li>
     * </ul>
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "open";

    /** 创建时间（自动填充） */
    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;
}
