package com.codeaudit.repository;

import com.codeaudit.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 审查任务数据访问层
 *
 * @author CodeAudit Team
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    @Query("SELECT r FROM Review r JOIN FETCH r.project WHERE r.id = :id")
    Optional<Review> findById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "project")
    List<Review> findAll();

    @EntityGraph(attributePaths = "project")
    List<Review> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    @EntityGraph(attributePaths = "project")
    Page<Review> findByProjectIdOrderByCreatedAtDesc(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = "project")
    List<Review> findByStatus(String status);
}
