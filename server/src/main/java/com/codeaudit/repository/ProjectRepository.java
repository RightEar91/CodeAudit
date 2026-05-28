package com.codeaudit.repository;

import com.codeaudit.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 项目数据访问层
 * <p>
 * 提供对 ca_projects 表的基础 CRUD 及按名称搜索、路径查重等查询。
 *
 * @author CodeAudit Team
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * 按名称模糊搜索（忽略大小写）
     */
    List<Project> findByNameContainingIgnoreCase(String name);

    /**
     * 按名称模糊搜索（分页）
     */
    Page<Project> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * 检查指定仓库路径是否已添加，用于防止重复接入
     */
    boolean existsByRepoPath(String repoPath);

    /**
     * 检查指定 GitHub 仓库 URL 是否已添加
     */
    boolean existsByRepoUrl(String repoUrl);
}
