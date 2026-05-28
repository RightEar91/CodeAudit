package com.codeaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Feedback entity for false-positive learning loop
 */
@Entity
@Table(name = "ca_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private Issue issue;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(length = 1000)
    private String reason;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;
}
