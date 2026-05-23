package com.codeaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 审查规则实体 — 对应数据库表 ca_rules
 * <p>
 * 规则定义 AI 在审查代码时需要关注的检查维度。
 * 每条规则包含一段 Prompt 指令，审查时注入到 LLM 上下文中。
 * <p>
 * 内置规则（is_builtin=true）不可删除但可启用/禁用，
 * 自定义规则可自由创建、编辑、删除。
 *
 * @author CodeAudit Team
 */
@Entity
@Table(name = "ca_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则名称（如"SQL注入检查"） */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 规则分类，与 Issue.category 一致：
     * {@code SECURITY / PERFORMANCE / STYLE / BUG}
     */
    @Column(nullable = false, length = 50)
    private String category;

    /** 目标编程语言（如 Java / Python / Go） */
    @Column(nullable = false, length = 50)
    private String language;

    /** 规则简要说明 */
    @Column(length = 500)
    private String description;

    /**
     * Prompt 指令文本 — 描述 AI 应遵循的审查标准。
     * 审查时所有已启用规则的 Prompt 会被拼接注入。
     */
    @Column(nullable = false, length = 2000)
    private String prompt;

    /** 是否为内置规则（内置规则不可删除） */
    @Column(name = "is_builtin", nullable = false)
    @Builder.Default
    private Boolean isBuiltin = false;

    /** 是否已启用（禁用的规则不会参与审查） */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;

    /** 创建时间（自动填充） */
    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    /** 最后更新时间（自动填充） */
    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
