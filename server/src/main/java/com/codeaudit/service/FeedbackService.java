package com.codeaudit.service;

import com.codeaudit.entity.Feedback;
import com.codeaudit.entity.Issue;
import com.codeaudit.repository.FeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Feedback learning service — records false-positive feedback and builds few-shot examples
 * to optimize review prompts over time.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    private static final int MAX_FEW_SHOT = 3;

    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * Record feedback when a developer marks an issue as ignored/false-positive
     */
    public Feedback recordFeedback(Issue issue, String action, String reason, String reviewedBy) {
        Feedback feedback = Feedback.builder()
                .issue(issue)
                .action(action)
                .reason(reason)
                .reviewedBy(reviewedBy)
                .build();
        feedbackRepository.save(feedback);
        log.info("反馈已记录: issueId={}, action={}", issue.getId(), action);
        return feedback;
    }

    /**
     * Build few-shot examples from historical feedback to reduce false positives.
     * Returns examples of previously flagged false positives to guide the model.
     */
    public String buildFewShotPrompt(Long projectId) {
        List<Feedback> fpFeedback = feedbackRepository.findByProjectId(projectId)
                .stream()
                .filter(f -> "false_positive".equals(f.getAction()) || "ignored".equals(f.getAction()))
                .limit(MAX_FEW_SHOT)
                .toList();

        if (fpFeedback.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n### 历史误报案例（请避免报告类似情况）\n");
        for (int i = 0; i < fpFeedback.size(); i++) {
            Feedback f = fpFeedback.get(i);
            Issue issue = f.getIssue();
            sb.append("案例").append(i + 1).append(":\n");
            sb.append("- 文件: ").append(issue.getFilePath()).append("\n");
            sb.append("- 问题: ").append(issue.getMessage()).append("\n");
            if (f.getReason() != null && !f.getReason().isBlank()) {
                sb.append("- 误报原因: ").append(f.getReason()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Get feedback statistics for a project
     */
    public FeedbackStats getStats(Long projectId) {
        List<Feedback> all = feedbackRepository.findByProjectId(projectId);
        long fp = all.stream().filter(f -> "false_positive".equals(f.getAction())).count();
        long ignored = all.stream().filter(f -> "ignored".equals(f.getAction())).count();
        long confirmed = all.stream().filter(f -> "confirmed".equals(f.getAction())).count();
        return new FeedbackStats(fp, ignored, confirmed, all.size());
    }

    public record FeedbackStats(long falsePositive, long ignored, long confirmed, long total) {}
}
