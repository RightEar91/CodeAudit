package com.codeaudit.controller;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codeaudit.common.BizException;
import com.codeaudit.common.Response;
import com.codeaudit.entity.Issue;
import com.codeaudit.entity.Review;
import com.codeaudit.service.IssueService;
import com.codeaudit.service.ReviewService;

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

    public ReviewController(ReviewService reviewService, IssueService issueService) {
        this.reviewService = reviewService;
        this.issueService = issueService;
    }

    /**
     * 查询某项目的所有审查记录，按创建时间降序（支持分页）
     * <p>
     * 分页参数示例：?page=0&size=20&sort=createdAt,desc
     */
    @GetMapping("/projects/{projectId}/reviews")
    @Transactional(readOnly=true)
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
    @Transactional(readOnly = true)
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
                                         @RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "Code Review");
        String fromRef = body.get("fromRef");
        String toRef = body.get("toRef");
        Review review = reviewService.create(projectId, title, fromRef, toRef);
        return Response.created(review);
    }

    /**
     * 取消进行中的审查（仅 pending/processing 状态可取消）
     */
    @PostMapping("/reviews/{id}/cancel")
    public Response<Void> cancel(@PathVariable Long id) {
        reviewService.cancel(id);
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
}
