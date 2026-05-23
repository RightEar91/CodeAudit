package com.codeaudit.repository;

import com.codeaudit.entity.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审查问题数据访问层
 * <p>
 * 提供对 ca_issues 表的 CRUD 及按审查任务、严重程度等维度的查询。
 * 查询方法使用 {@link EntityGraph} 预加载 review 及 review.project 关联，
 * 避免 open-in-view=false 时 Jackson 序列化触发懒加载异常。
 *
 * @author CodeAudit Team
 */
@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    @EntityGraph(attributePaths = {"review", "review.project"})
    List<Issue> findByReviewId(Long reviewId);

    @EntityGraph(attributePaths = {"review", "review.project"})
    Page<Issue> findByReviewId(Long reviewId, Pageable pageable);

    @EntityGraph(attributePaths = {"review", "review.project"})
    List<Issue> findByReviewIdAndSeverity(Long reviewId, String severity);

    /**
     * 统计某次审查中指定状态的问题数量（open / resolved / ignored）
     */
    long countByReviewIdAndStatus(Long reviewId, String status);
}
