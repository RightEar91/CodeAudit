package com.codeaudit.controller;

import com.codeaudit.common.Response;
import com.codeaudit.entity.*;
import com.codeaudit.repository.*;
import com.codeaudit.service.AutoFixService;
import com.codeaudit.service.CollaborationService;
import com.codeaudit.service.FeedbackService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Collaboration REST controller for team features, auto-fix, feedback, and knowledge docs.
 */
@RestController
@RequestMapping("/api")
public class CollaborationController {

    private final CollaborationService collaborationService;
    private final AutoFixService autoFixService;
    private final FeedbackService feedbackService;
    private final IssueCommentRepository commentRepository;
    private final FixSuggestionRepository fixSuggestionRepository;
    private final FeedbackRepository feedbackRepository;
    private final KnowledgeDocumentRepository knowledgeDocRepository;
    private final IssueRepository issueRepository;
    private final ProjectRepository projectRepository;

    public CollaborationController(CollaborationService collaborationService,
                                    AutoFixService autoFixService,
                                    FeedbackService feedbackService,
                                    IssueCommentRepository commentRepository,
                                    FixSuggestionRepository fixSuggestionRepository,
                                    FeedbackRepository feedbackRepository,
                                    KnowledgeDocumentRepository knowledgeDocRepository,
                                    IssueRepository issueRepository,
                                    ProjectRepository projectRepository) {
        this.collaborationService = collaborationService;
        this.autoFixService = autoFixService;
        this.feedbackService = feedbackService;
        this.commentRepository = commentRepository;
        this.fixSuggestionRepository = fixSuggestionRepository;
        this.feedbackRepository = feedbackRepository;
        this.knowledgeDocRepository = knowledgeDocRepository;
        this.issueRepository = issueRepository;
        this.projectRepository = projectRepository;
    }

    // ---- Comments ----

    @GetMapping("/issues/{issueId}/comments")
    public Response<List<IssueComment>> getComments(@PathVariable Long issueId) {
        return Response.ok(collaborationService.getComments(issueId));
    }

    @PostMapping("/issues/{issueId}/comments")
    public Response<IssueComment> addComment(@PathVariable Long issueId,
                                              @RequestBody Map<String, String> body) {
        IssueComment comment = collaborationService.addComment(
                issueId, body.get("authorName"), body.get("content"));
        return Response.created(comment);
    }

    // ---- Issue Assignment ----

    @PutMapping("/issues/{issueId}/assign")
    public Response<Issue> assignIssue(@PathVariable Long issueId,
                                        @RequestBody Map<String, String> body) {
        Issue issue = collaborationService.assignIssue(
                issueId, body.get("assigneeName"), body.get("assigneeEmail"));
        return Response.ok(issue);
    }

    // ---- Auto-fix ----

    @GetMapping("/issues/{issueId}/fix")
    public Response<FixSuggestion> getFix(@PathVariable Long issueId) {
        FixSuggestion fix = fixSuggestionRepository.findByIssueId(issueId).orElse(null);
        return Response.ok(fix);
    }

    @PostMapping("/fixes/{fixId}/accept")
    public Response<FixSuggestion> acceptFix(@PathVariable Long fixId,
                                              @RequestBody Map<String, String> body) {
        FixSuggestion fix = autoFixService.acceptFix(fixId, body.get("acceptedBy"));
        return Response.ok(fix);
    }

    // ---- Feedback ----

    @PostMapping("/issues/{issueId}/feedback")
    public Response<Feedback> recordFeedback(@PathVariable Long issueId,
                                              @RequestBody Map<String, String> body) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new RuntimeException("Issue not found: " + issueId));
        Feedback feedback = feedbackService.recordFeedback(issue,
                body.get("action"), body.get("reason"), body.get("reviewedBy"));
        return Response.ok(feedback);
    }

    @GetMapping("/projects/{projectId}/feedback/stats")
    public Response<FeedbackService.FeedbackStats> feedbackStats(@PathVariable Long projectId) {
        return Response.ok(feedbackService.getStats(projectId));
    }

    // ---- Users ----

    @GetMapping("/users")
    public Response<List<User>> listUsers() {
        return Response.ok(collaborationService.listUsers());
    }

    @PostMapping("/users")
    public Response<User> createUser(@RequestBody Map<String, String> body) {
        User user = collaborationService.createUser(
                body.get("username"), body.get("displayName"),
                body.get("email"), body.get("role"));
        return Response.created(user);
    }

    @PutMapping("/users/{userId}/role")
    public Response<User> updateRole(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        User user = collaborationService.updateUserRole(userId, body.get("role"));
        return Response.ok(user);
    }

    // ---- Share Links ----

    @GetMapping("/reviews/{reviewId}/share")
    public Response<Map<String, String>> getShareLink(@PathVariable Long reviewId) {
        return Response.ok(Map.of("link", collaborationService.generateShareLink(reviewId)));
    }

    // ---- Knowledge Documents (RAG) ----

    @GetMapping("/projects/{projectId}/knowledge")
    public Response<List<KnowledgeDocument>> listKnowledge(@PathVariable Long projectId) {
        return Response.ok(knowledgeDocRepository.findByProjectId(projectId));
    }

    @PostMapping("/projects/{projectId}/knowledge")
    public Response<KnowledgeDocument> addKnowledge(@PathVariable Long projectId,
                                                     @RequestBody Map<String, String> body) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .project(project)
                .title(body.get("title"))
                .content(body.get("content"))
                .category(body.get("category"))
                .build();
        return Response.created(knowledgeDocRepository.save(doc));
    }

    @DeleteMapping("/knowledge/{id}")
    public Response<Void> deleteKnowledge(@PathVariable Long id) {
        knowledgeDocRepository.deleteById(id);
        return Response.ok();
    }
}
