package com.codeaudit.controller;

import com.codeaudit.common.BizException;
import com.codeaudit.common.Response;
import com.codeaudit.entity.Project;
import com.codeaudit.entity.Review;
import com.codeaudit.repository.ProjectRepository;
import com.codeaudit.service.GithubService;
import com.codeaudit.service.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Webhook REST 控制器
 * <p>
 * 接收 GitHub Webhook 事件（Pull Request opened / synchronize），
 * 自动触发 AI 代码审查。
 * <p>
 * 路由设计：
 * <ul>
 *   <li>{@code POST /api/webhook/github} — 接收 GitHub Webhook 事件</li>
 * </ul>
 *
 * @author CodeAudit Team
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final GithubService githubService;
    private final ReviewService reviewService;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    public WebhookController(GithubService githubService, ReviewService reviewService,
                             ProjectRepository projectRepository, ObjectMapper objectMapper) {
        this.githubService = githubService;
        this.reviewService = reviewService;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 接收 GitHub Webhook 事件
     * <p>
     * GitHub 发送 POST 请求，Header 含 X-Hub-Signature-256 用于签名验证。
     * Body 为 JSON，包含 action、pull_request 等字段。
     * <p>
     * 当前支持的 action：
     * <ul>
     *   <li>opened      — PR 新建，触发审查</li>
     *   <li>synchronize — PR 提交新代码，触发审查</li>
     * </ul>
     */
    @PostMapping("/github")
    public Response<String> handleGitHubWebhook(
            @RequestBody String payload,
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {

        if (!githubService.verifyWebhookSignature(payload, signature)) {
            return Response.fail(HttpStatus.FORBIDDEN.value(), "Webhook 签名验证失败");
        }

        if (!"pull_request".equals(eventType)) {
            log.debug("忽略非 PR 事件: {}", eventType);
            return Response.ok("ignored");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String action = root.get("action").asText();

            if (!"opened".equals(action) && !"synchronize".equals(action)) {
                log.debug("忽略非 opened/synchronize 的 PR 事件: {}", action);
                return Response.ok("ignored");
            }

            JsonNode prNode = root.get("pull_request");
            JsonNode repoNode = root.get("repository");

            String repoUrl = repoNode.get("html_url").asText() + ".git";
            int prNumber = prNode.get("number").asInt();
            String prTitle = prNode.get("title").asText();
            String prHtmlUrl = prNode.get("html_url").asText();
            String headRef = prNode.get("head").get("ref").asText();
            String baseRef = prNode.get("base").get("ref").asText();

            List<Project> projects = projectRepository.findAll();
            Optional<Project> matched = projects.stream()
                    .filter(p -> "GITHUB".equalsIgnoreCase(p.getRepoType()))
                    .filter(p -> repoUrl.equalsIgnoreCase(p.getRepoUrl()))
                    .findFirst();

            if (matched.isEmpty()) {
                log.warn("未找到匹配的项目: repoUrl={}", repoUrl);
                return Response.fail(HttpStatus.NOT_FOUND.value(), "未找到匹配的项目: " + repoUrl);
            }

            Project project = matched.get();
            log.info("Webhook 触发审查: project={}, PR=#{}, action={}", project.getName(), prNumber, action);

            Review review = reviewService.create(
                    project.getId(),
                    "PR #" + prNumber + " — " + prTitle,
                    baseRef,
                    headRef,
                    prNumber,
                    prHtmlUrl
            );

            return Response.ok("审查已触发: reviewId=" + review.getId());

        } catch (BizException e) {
            log.warn("Webhook 处理失败: {}", e.getMessage());
            return Response.fail(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Webhook 处理异常", e);
            return Response.fail(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Webhook 处理异常: " + e.getMessage());
        }
    }
}
