package com.codeaudit.repository;

import com.codeaudit.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    List<KnowledgeDocument> findByProjectId(Long projectId);

    @Query("SELECT k FROM KnowledgeDocument k WHERE k.project.id = :projectId AND " +
           "(k.title LIKE %:keyword% OR k.content LIKE %:keyword%)")
    List<KnowledgeDocument> searchByProject(@Param("projectId") Long projectId, @Param("keyword") String keyword);
}
