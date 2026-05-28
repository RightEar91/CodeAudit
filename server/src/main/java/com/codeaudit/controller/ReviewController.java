package com.codeaudit.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.codeaudit.common.BizException;
import com.codeaudit.common.Response;
import com.codeaudit.dto.CreateReviewRequest;
import com.codeaudit.dto.DiffBlock;
import com.codeaudit.entity.Issue;
import com.codeaudit.entity.Review;
import com.codeaudit.service.GitDiffService;
import com.codeaudit.service.GitService;
import com.codeaudit.service.GithubService;
import com.codeaudit.service.IssueService;
import com.codeaudit.service.PdfExportService;
import com.codeaudit.service.ReviewService;
import com.codeaudit.service.SseService;

/**
 * 审查管理 REST 控制器
 * <p>
 * 提供审查任务的 CRUD、问题列表查询和问题状态管理接口。
 * 所有响应统一封装为 {@link Response} 格式。
 * <p>
 * 路由设计：
 * <ul>
 *   <li>{@code GET    /api/projects/:projectId/reviews} — 某项目的审查历史</li>
 *   <li>{@code POST   /api/projects/:projectId/reviews} — 创建审查（触发异步 AI 分析）</li>
 *   <li>{@code GET    /api/reviews/:id}                — 审查详情</li>
 *   <li>{@code POST   /api/reviews/:id/cancel}         — 取消审查</li>
 *   <li>{@code DELETE /api/reviews/:id}                — 删除审查及关联问题</li>
 *   <li>{@code GET    /api/reviews/:reviewId/issues}   — 审查的问题列表</li>
 *   <li>{@code PUT    /api/issues/:id/status}          — 更新问题处理状态</li>
 * </ul>
 *
 * @author CodeAudit Team
 */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final IssueService issueService;
    private final PdfExportService pdfExportService;
    private final GitDiffService gitDiffService;
    private final GitService gitService;
    private final GithubService githubService;
    private final SseService sseService;

    public ReviewController(ReviewService reviewService, IssueService issueService,
                            PdfExportService pdfExportService, GitDiffService gitDiffService,
                            GitService gitService, GithubService githubService,
                            SseService sseService) {
        this.reviewService = reviewService;
        this.issueService = issueService;
        this.pdfExportService = pdfExportService;
        this.gitDiffService = gitDiffService;
        this.gitService = gitService;
        this.githubService = githubService;
        this.sseService = sseService;
    }

    /**
     * 查询某项目的所有审查记录，按创建时间降序（支持分页）
     * <p>
     * 分页参数示例：?page=0&size=20&sort=createdAt,desc
     */
    @GetMapping("/projects/{projectId}/reviews")
    public Response<Page<Review>> listByProject(@PathVariable Long projectId, Pageable pageable) {
        Page<Review> reviews = reviewService.listByProjectId(projectId, pageable);
        return Response.ok(reviews);
    }

    /**
     * 按 ID 查询审查详情
     *
     * @throws BizException 审查不存在时由全局异常处理器拦截（404）
     */
    @GetMapping("/reviews/{id}")
    public Response<Review> getById(@PathVariable Long id) {
        Review review = reviewService.findById(id)
                .orElseThrow(() -> new BizException(404, "审查不存在: " + id));
        return Response.ok(review);
    }

    /**
     * 创建审查任务（异步执行 AI 分析）
     * <p>
     * 请求体支持：
     * <ul>
     *   <li>{@code title}   — 审查标题（可选，默认 "Code Review"）</li>
     *   <li>{@code fromRef} — 源引用（可选，默认 HEAD~1）</li>
     *   <li>{@code toRef}   — 目标引用（可选，默认 HEAD）</li>
     * </ul>
     * <p>
     * 返回的 Review 初始状态为 pending，调用方应通过轮询 status 字段感知进展。
     */
    @PostMapping("/projects/{projectId}/reviews")
    public Response<Review> createReview(@PathVariable Long projectId,
                                         @RequestBody CreateReviewRequest body) {
        String title = body.getTitle() != null ? body.getTitle() : "Code Review";
        String fromRef = body.getFromRef();
        String toRef = body.getToRef();
        Review review = reviewService.create(projectId, title, fromRef, toRef, body.getFilters());
        return Response.created(review);
    }

    /**
     * 取消进行中的审查（仅 pending/processing 状态可取消）
     * <p>
     * 使用 cancelImmediate：取消令牌立即可用，DB 写操作异步不阻塞 HTTP 响应。
     */
    @PostMapping("/reviews/{id}/cancel")
    public Response<Void> cancel(@PathVariable Long id) {
        reviewService.cancelImmediate(id);
        return Response.ok();
    }

    /**
     * 删除审查记录及关联的所有问题
     */
    @DeleteMapping("/reviews/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        reviewService.delete(id);
        return Response.ok();
    }

    /**
     * 查询某次审查发现的所有问题（支持分页）
     * <p>
     * 分页参数示例：?page=0&size=20&sort=severity,desc
     */
    @GetMapping("/reviews/{reviewId}/issues")
    public Response<Page<Issue>> listIssues(@PathVariable Long reviewId, Pageable pageable) {
        Page<Issue> issues = issueService.listByReviewId(reviewId, pageable);
        return Response.ok(issues);
    }

    /**
     * 更新问题处理状态（open / resolved / ignored）
     * <p>
     * 请求体：{@code {"status": "resolved"}}
     *
     * @throws BizException 问题不存在（404）或状态非法（400）
     */
    @PutMapping("/issues/{id}/status")
    public Response<Issue> updateIssueStatus(@PathVariable Long id,
                                             @RequestBody Map<String, String> body) {
        Issue issue = issueService.updateStatus(id, body.get("status"));
        return Response.ok(issue);
    }

    /**
     * 导出审查报告为 PDF 格式
     * <p>
     * 返回 PDF 二进制流，浏览器自动触发下载。
     * 文件名格式：code-review-{reviewId}.pdf
     */
    @GetMapping("/reviews/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long id) {
        byte[] pdfBytes = pdfExportService.exportReviewPdf(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "code-review-" + id + ".pdf");
        headers.setCacheControl("no-cache");
        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    /**
     * 查询审查任务的 diff 变更文件列表，用于报告详情页展示代码高亮
     * <p>
     * 根据 Review 记录的 fromRef / toRef 重新提取 diff，
     * 前端将 Issue 的 filePath 与此列表匹配以展示对应的变更代码。
     */
    @GetMapping("/reviews/{id}/diffs")
    public Response<List<DiffBlock>> getReviewDiffs(@PathVariable Long id) {
        Review review = reviewService.findById(id)
                .orElseThrow(() -> new BizException(404, "审查不存在: " + id));
        String repoPath = gitService.resolveRepoPath(review.getProject());
        String language = review.getProject().getLanguage() != null
                ? review.getProject().getLanguage() : "Java";
        List<DiffBlock> diffBlocks = gitDiffService.extractDiffBetweenCommits(
                repoPath, review.getFromRef(), review.getToRef(), language);
        return Response.ok(diffBlocks);
    }

    /**
     * 手动在 PR 上发布审查结果评论（Markdown）
     */
    @PostMapping("/reviews/{id}/pr-comment")
    public Response<Map<String, Object>> postPrComment(@PathVariable Long id) {
        Review review = reviewService.findById(id)
                .orElseThrow(() -> new BizException(404, "审查不存在: " + id));

        if (review.getPrNumber() == null) {
            throw new BizException("该审查未关联 PR，无法发布评论");
        }

        var project = review.getProject();
        if (project == null || !"GITHUB".equalsIgnoreCase(project.getRepoType())) {
            throw new BizException("仅 GitHub 项目支持发布 PR 评论");
        }

        String markdown = githubService.buildReviewSummaryMarkdown(review);
        String commentUrl = githubService.postPullRequestComment(
                project.getRepoUrl(), review.getPrNumber(), markdown);

        Map<String, Object> result = Map.of(
                "commentUrl", commentUrl,
                "prNumber", review.getPrNumber()
        );
        return Response.ok(result);
    }

    /**
     * SSE 实时推送审查进度
     * <p>
     * 前端通过 EventSource 连接此端点，实时接收审查进度事件。
     * 事件类型：
     * <ul>
     *   <li>{@code connected} — SSE 连接已建立</li>
     *   <li>{@code progress}  — 审查进度更新（每个文件完成后推送）</li>
     *   <li>{@code completed} — 审查完成（关闭连接）</li>
     *   <li>{@code failed}    — 审查失败（关闭连接）</li>
     * </ul>
     */
    @GetMapping(value = "/reviews/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable Long id) {
        return sseService.createEmitter(id);
    }
}
