package com.codeaudit.service;

import com.codeaudit.entity.*;
import com.codeaudit.repository.IssueCommentRepository;
import com.codeaudit.repository.IssueRepository;
import com.codeaudit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Collaboration service for team features — issue assignment, comments, share links.
 */
@Service
public class CollaborationService {

    private static final Logger log = LoggerFactory.getLogger(CollaborationService.class);

    private final IssueCommentRepository commentRepository;
    private final IssueRepository issueRepository;
    private final UserRepository userRepository;

    public CollaborationService(IssueCommentRepository commentRepository,
                                 IssueRepository issueRepository,
                                 UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.issueRepository = issueRepository;
        this.userRepository = userRepository;
    }

    // ---- Comments ----

    public IssueComment addComment(Long issueId, String authorName, String content) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found: " + issueId));
        IssueComment comment = IssueComment.builder()
                .issue(issue)
                .authorName(authorName)
                .content(content)
                .build();
        commentRepository.save(comment);
        log.info("评论已添加: issueId={}, author={}", issueId, authorName);
        return comment;
    }

    public List<IssueComment> getComments(Long issueId) {
        return commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
    }

    // ---- Issue Assignment ----
    // Uses authorName/authorEmail fields on Issue entity for lightweight assignment

    public Issue assignIssue(Long issueId, String assigneeName, String assigneeEmail) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found: " + issueId));
        issue.setAuthorName(assigneeName);
        issue.setAuthorEmail(assigneeEmail);
        issueRepository.save(issue);
        log.info("问题已指派: issueId={} -> {}", issueId, assigneeName);
        return issue;
    }

    // ---- User Management ----

    public User createUser(String username, String displayName, String email, String role) {
        User user = User.builder()
                .username(username)
                .displayName(displayName)
                .email(email)
                .role(role)
                .build();
        userRepository.save(user);
        return user;
    }

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    public User updateUserRole(Long userId, String role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        user.setRole(role);
        userRepository.save(user);
        return user;
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    // ---- Share Links ----

    public String generateShareLink(Long reviewId) {
        return "/reviews/" + reviewId + "?token=" + UUID.randomUUID().toString().substring(0, 8);
    }
}
