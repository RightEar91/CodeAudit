package com.codeaudit.repository;

import com.codeaudit.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByAction(String action);

    @Query("SELECT f FROM Feedback f WHERE f.issue.review.project.id = :projectId")
    List<Feedback> findByProjectId(@Param("projectId") Long projectId);
}
