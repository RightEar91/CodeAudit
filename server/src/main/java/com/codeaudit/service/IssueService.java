package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Issue;
import com.codeaudit.repository.IssueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * 审查问题管理服务
 * <p>
 * 提供问题列表查询和状态更新，状态只允许 open / resolved / ignored 三种值。
 *
 * @author CodeAudit Team
 */
@Service
public class IssueService {

    private static final Set<String> VALID_STATUSES = Set.of("open", "resolved", "ignored");

    private final IssueRepository issueRepository;

    public IssueService(IssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    /**
     * 查询某次审查发现的所有问题
     */
    public List<Issue> listByReviewId(Long reviewId) {
        return issueRepository.findByReviewId(reviewId);
    }

    /**
     * 分页查询某次审查发现的所有问题
     */
    @Transactional(readOnly = true)
    public Page<Issue> listByReviewId(Long reviewId, Pageable pageable) {
        return issueRepository.findByReviewId(reviewId, pageable);
    }

    /**
     * 更新问题处理状态
     *
     * @param issueId 问题 ID
     * @param status  目标状态（open / resolved / ignored）
     * @return 更新后的 Issue
     * @throws BizException 问题不存在（404）或状态非法（400）
     */
    @Transactional
    public Issue updateStatus(Long issueId, String status) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new BizException("无效的状态值，允许的值为: open, resolved, ignored");
        }
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new BizException(404, "问题不存在: " + issueId));
        issue.setStatus(status);
        return issueRepository.save(issue);
    }
}
