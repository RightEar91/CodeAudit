package com.codeaudit.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 审查任务实体 — 对应数据库表 ca_reviews
 * <p>
 * 每个审查任务关联一个项目和一组 diff 变更。
 * 任务采用异步执行，状态流转为：
 * {@code pending -> processing -> completed/failed}
 * <p>
 * 完成后的汇总统计（高/中/低危计数）会写入本实体对应字段。
 *
 * @author CodeAudit Team
 */
@Entity
@Table(name = "ca_reviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属项目 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** 审查标题（用户自定义或自动生成） */
    @Column(nullable = false, length = 300)
    private String title;

    /** 源引用（commit hash / 分支名），默认 HEAD~1 */
    @Column(name = "from_ref", length = 100)
    private String fromRef;

    /** 目标引用（commit hash / 分支名），默认 HEAD */
    @Column(name = "to_ref", length = 100)
    private String toRef;

    /**
     * 审查状态：
     * <ul>
     *   <li>{@code pending}    — 已创建，等待处理</li>
     *   <li>{@code processing} — AI 审查进行中</li>
     *   <li>{@code completed}  — 审查完成</li>
     *   <li>{@code failed}     — 审查失败（LLM 异常/手动取消）</li>
     * </ul>
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    /** 发现的问题总数 */
    @Column(name = "total_issues")
    @Builder.Default
    private Integer totalIssues = 0;

    /** 高危问题数量 */
    @Column(name = "high_count")
    @Builder.Default
    private Integer highCount = 0;

    /** 中危问题数量 */
    @Column(name = "medium_count")
    @Builder.Default
    private Integer mediumCount = 0;

    /** 低危问题数量 */
    @Column(name = "low_count")
    @Builder.Default
    private Integer lowCount = 0;

    /** 审查耗时（毫秒） */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** 待审查的文件总数（diff 提取后写入，用于计算进度百分比） */
    @Column(name = "total_files")
    @Builder.Default
    private Integer totalFiles = 0;

    /** 已完成审查的文件数（每处理完一个文件 +1，前端轮询可感知进度） */
    @Column(name = "reviewed_files")
    @Builder.Default
    private Integer reviewedFiles = 0;

    /** 失败原因（仅 status=failed 时有值） */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** 审查发现的问题列表（级联删除），列表查询时不序列化，通过 /api/reviews/:id/issues 单独获取 */
    @JsonIgnore
    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Issue> issues = new ArrayList<>();

    /** 创建时间（自动填充） */
    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    /** 最后更新时间（自动填充） */
    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
