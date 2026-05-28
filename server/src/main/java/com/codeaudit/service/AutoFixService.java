package com.codeaudit.service;

import com.codeaudit.entity.FixSuggestion;
import com.codeaudit.entity.Issue;
import com.codeaudit.repository.FixSuggestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Auto-fix service — uses AI to generate fix patches for issues.
 * Stores suggestions and supports accept/reject workflow.
 */
@Service
public class AutoFixService {

    private static final Logger log = LoggerFactory.getLogger(AutoFixService.class);

    private final AiChatService aiChatService;
    private final FixSuggestionRepository fixSuggestionRepository;

    public AutoFixService(AiChatService aiChatService, FixSuggestionRepository fixSuggestionRepository) {
        this.aiChatService = aiChatService;
        this.fixSuggestionRepository = fixSuggestionRepository;
    }

    /**
     * Generate fix patch for a single issue
     */
    @Async
    public void generateFix(Issue issue) {
        if (fixSuggestionRepository.findByIssueId(issue.getId()).isPresent()) return;

        try {
            String prompt = buildFixPrompt(issue);
            String response = aiChatService.chat(prompt);

            FixSuggestion fix = FixSuggestion.builder()
                    .issue(issue)
                    .fixPatch(response)
                    .explanation("AI 自动生成的修复方案")
                    .build();

            fixSuggestionRepository.save(fix);
            log.info("Auto-fix 已生成: issueId={}", issue.getId());
        } catch (Exception e) {
            log.warn("Auto-fix 生成失败: issueId={}, error={}", issue.getId(), e.getMessage());
        }
    }

    /**
     * Generate fixes for all issues in a review
     */
    @Async
    public void generateFixes(List<Issue> issues) {
        for (Issue issue : issues) {
            try {
                generateFix(issue);
            } catch (Exception e) {
                log.warn("Auto-fix 跳过: issueId={}", issue.getId());
            }
        }
    }

    /**
     * Accept a fix suggestion
     */
    public FixSuggestion acceptFix(Long fixId, String acceptedBy) {
        FixSuggestion fix = fixSuggestionRepository.findById(fixId)
                .orElseThrow(() -> new RuntimeException("Fix suggestion not found: " + fixId));
        fix.setStatus("accepted");
        fix.setAcceptedBy(acceptedBy);
        fixSuggestionRepository.save(fix);
        fix.getIssue().setStatus("resolved");

        log.info("用户 {} 接受了 Auto-fix: fixId={}", acceptedBy, fixId);
        return fix;
    }

    private String buildFixPrompt(Issue issue) {
        return """
            你是一名资深代码修复专家。请根据以下代码问题生成修复方案。

            文件: %s
            行号: %d
            严重程度: %s
            分类: %s
            问题: %s
            建议: %s

            请生成修复后的代码 patch（unified diff 格式），并附简要说明。
            如无法自动修复，请说明原因。
            """.formatted(
                issue.getFilePath(), issue.getLineNumber() != null ? issue.getLineNumber() : 0,
                issue.getSeverity(), issue.getCategory(), issue.getMessage(),
                issue.getSuggestion() != null ? issue.getSuggestion() : ""
            );
    }
}
