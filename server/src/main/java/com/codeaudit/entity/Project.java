package com.codeaudit.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 项目实体 — 对应数据库表 ca_projects
 * <p>
 * 每个项目代表一个接入 CodeAudit 的本地 Git 仓库。
 * 添加项目时系统会校验路径是否存在且为有效 Git 仓库。
 *
 * @author CodeAudit Team
 */
@Entity
@Table(name = "ca_projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 项目显示名称 */
    @Column(nullable = false, length = 200)
    private String name;

    /** 本地 Git 仓库绝对路径（如 E:/projects/my-app） */
    @Column(name = "repo_path", nullable = false, length = 500)
    private String repoPath;

    /** 仓库当前活跃分支 */
    @Column(name = "current_branch", length = 200)
    private String currentBranch;

    /** 项目使用的编程语言，默认 Java */
    @Column(length = 50)
    @Builder.Default
    private String language = "Java";

    /** 创建时间（自动填充） */
    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    /** 最后更新时间（自动填充） */
    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
