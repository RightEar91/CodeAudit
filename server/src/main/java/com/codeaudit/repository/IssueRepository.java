package com.codeaudit.repository;

import com.codeaudit.entity.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 统计某项目中各严重程度的问题数量
     */
    @Query("SELECT i.severity, COUNT(i) FROM Issue i WHERE i.review.project.id = :projectId GROUP BY i.severity")
    List<Object[]> countByProjectIdGroupBySeverity(@Param("projectId") Long projectId);

    /**
     * 统计某项目中各分类的问题数量
     */
    @Query("SELECT i.category, COUNT(i) FROM Issue i WHERE i.review.project.id = :projectId GROUP BY i.category")
    List<Object[]> countByProjectIdGroupByCategory(@Param("projectId") Long projectId);

    /**
     * 统计某项目中各状态的问题数量
     */
    @Query("SELECT i.status, COUNT(i) FROM Issue i WHERE i.review.project.id = :projectId GROUP BY i.status")
    List<Object[]> countByProjectIdGroupByStatus(@Param("projectId") Long projectId);

    /**
     * 按作者统计问题数量 Top N
     */
    @Query("SELECT i.authorName, i.authorEmail, COUNT(i) FROM Issue i " +
           "WHERE i.review.project.id = :projectId AND i.authorName IS NOT NULL " +
           "GROUP BY i.authorName, i.authorEmail ORDER BY COUNT(i) DESC")
    List<Object[]> countByProjectIdGroupByAuthor(@Param("projectId") Long projectId);
}
