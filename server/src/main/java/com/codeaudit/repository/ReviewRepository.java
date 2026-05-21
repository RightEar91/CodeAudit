package com.codeaudit.repository;

import com.codeaudit.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 审查任务数据访问层
 * <p>
 * 提供对 ca_reviews 表的 CRUD 及按项目、状态等维度的查询。
 *
 * @author CodeAudit Team
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 按项目 ID 查询审查历史，按创建时间降序排列（最新的在前）
     */
    List<Review> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    /**
     * 按项目 ID 分页查询审查历史，按创建时间降序
     */
    Page<Review> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    /**
     * 按审查状态筛选（pending / processing / completed / failed）
     */
    List<Review> findByStatus(String status);
}
