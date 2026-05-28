package com.codeaudit.repository;

import com.codeaudit.entity.FixSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FixSuggestionRepository extends JpaRepository<FixSuggestion, Long> {
    Optional<FixSuggestion> findByIssueId(Long issueId);
}
