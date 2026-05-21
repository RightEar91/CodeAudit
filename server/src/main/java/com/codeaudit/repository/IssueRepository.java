package com.codeaudit.repository;

import com.codeaudit.entity.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审查问题数据访问层
 * <p>
 * 提供对 ca_issues 表的 CRUD 及按审查任务、严重程度等维度的查询。
 *
 * @author CodeAudit Team
 */
@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    /**
     * 查询某次审查发现的所有问题
     */
    List<Issue> findByReviewId(Long reviewId);

    /**
     * 分页查询某次审查发现的所有问题
     */
    Page<Issue> findByReviewId(Long reviewId, Pageable pageable);

    /**
     * 按严重程度过滤某次审查的问题（用于分组展示 HIGH / MEDIUM / LOW）
     */
    List<Issue> findByReviewIdAndSeverity(Long reviewId, String severity);

    /**
     * 统计某次审查中指定状态的问题数量（open / resolved / ignored）
     */
    long countByReviewIdAndStatus(Long reviewId, String status);
}
