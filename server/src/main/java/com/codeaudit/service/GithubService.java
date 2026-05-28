package com.codeaudit.service;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Project;
import com.codeaudit.entity.Review;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * GitHub 远程仓库服务
 * <p>
 * 负责将 GitHub 仓库 clone 到本地缓存目录，后续所有 Git 操作
 * 均通过本地缓存进行，与本地仓库模式保持一致。
 * <p>
 * 缓存目录结构：{@code {cloneBaseDir}/{projectId}/}
 *
 * @author CodeAudit Team
 */
@Service
public class GithubService {

    private static final Logger log = LoggerFactory.getLogger(GithubService.class);
    private static final Pattern GITHUB_URL_PATTERN =
            Pattern.compile("^https?://github\\.com/[\\w.-]+/[\\w.-]+(\\.git)?$");

    @Value("${codeaudit.github.token:}")
    private String githubToken;

    @Value("${codeaudit.github.webhook-secret:}")
    private String webhookSecret;

    @Value("${codeaudit.github.clone-base-dir:#{systemProperties['user.home'] + '/.codeaudit/repos'}}")
    private String cloneBaseDir;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final boolean sslVerify;

    public GithubService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                         @Value("${codeaudit.github.ssl-verify:true}") boolean sslVerify) {
        this.objectMapper = objectMapper;
        this.sslVerify = sslVerify;
        this.restClient = sslVerify ? restClientBuilder.build() : buildTrustAllRestClient();
    }

    /**
     * 构建信任所有证书的 RestClient（仅用于内网代理/自签名证书环境）
     */
    private static RestClient buildTrustAllRestClient() {
        SSLContext sslContext = createTrustAllSslContext();
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        return RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    /**
     * 创建信任所有证书的 SSLContext
     */
    private static SSLContext createTrustAllSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            } }, new java.security.SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("创建 TLS 信任全部连接失败", e);
        }
    }

    /**
     * 获取 GitHub Token（当前配置值）
     */
    public String getGithubToken() {
        return githubToken;
    }

    /**
     * 更新 GitHub Token（运行时修改，重启后还原）
     */
    public void setGithubToken(String token) {
        this.githubToken = token;
        log.info("GitHub Token 已更新");
    }

    /**
     * 验证 GitHub URL 格式并校验 Token 是否有效
     *
     * @param url   GitHub 仓库 URL
     * @param token GitHub Personal Access Token
     * @throws BizException URL 格式不正确或 Token 无效时抛出
     */
    public void validateRepository(String url, String token) {
        if (url == null || url.isBlank()) {
            throw new BizException("GitHub 仓库地址不能为空");
        }
        if (token == null || token.isBlank()) {
            throw new BizException("GitHub Token 未配置，请在系统设置中填写");
        }

        if (!GITHUB_URL_PATTERN.matcher(url.trim()).matches()) {
            throw new BizException("GitHub 仓库地址格式不正确，示例: https://github.com/user/repo.git");
        }

        try {
            String apiUrl = buildApiUrl(url);
            restClient.get()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> { throw new BizException("GitHub 仓库不存在或无权限访问: " + url); })
                    .onStatus(status -> status.value() == 401,
                            (req, res) -> { throw new BizException("GitHub Token 无效或已过期"); })
                    .toBodilessEntity();
            log.info("GitHub 仓库验证通过: {}", url);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            log.warn("GitHub API 调用异常: {}", msg);
            String tip = msg.contains("PKIX") || msg.contains("certificate")
                    ? " — 如使用内网代理，请在 application.yml 中设置 codeaudit.github.ssl-verify=false"
                    : "";
            throw new BizException("无法访问 GitHub，请检查网络连接: " + msg + tip);
        }
    }

    /**
     * 将 GitHub 仓库 clone 到本地缓存目录
     *
     * @param project 项目实体（含 repoUrl 和 githubToken）
     * @return 本地缓存目录的绝对路径
     * @throws BizException clone 失败时抛出
     */
    public String cloneRepository(Project project) {
        String url = project.getRepoUrl();
        String token = githubToken != null ? githubToken : "";

        Path cachePath = getCachePath(project.getId());
        File cacheDir = cachePath.toFile();

        if (cacheDir.exists()) {
            log.info("缓存目录已存在，删除后重新 clone: {}", cacheDir);
            deleteDirectory(cacheDir);
        }

        try {
            Files.createDirectories(cachePath);
            log.info("开始 clone GitHub 仓库: {} -> {}, sslVerify={}", url, cachePath, sslVerify);

            SSLSocketFactory originalSslFactory = null;
            HostnameVerifier originalHostnameVerifier = null;
            if (!sslVerify) {
                SSLContext trustAllCtx = createTrustAllSslContext();
                originalSslFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
                originalHostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
                HttpsURLConnection.setDefaultSSLSocketFactory(trustAllCtx.getSocketFactory());
                HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            }

            try {
                Git.cloneRepository()
                        .setURI(url.trim())
                        .setDirectory(cacheDir)
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                        .setCloneAllBranches(false)
                        .call()
                        .close();
            } finally {
                if (!sslVerify && originalSslFactory != null) {
                    HttpsURLConnection.setDefaultSSLSocketFactory(originalSslFactory);
                    HttpsURLConnection.setDefaultHostnameVerifier(originalHostnameVerifier);
                }
            }

            log.info("GitHub 仓库 clone 完成: {}", cachePath);
        } catch (GitAPIException e) {
            log.error("clone GitHub 仓库失败: {}", e.getMessage(), e);
            deleteDirectory(cacheDir);
            throw new BizException("clone GitHub 仓库失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("clone GitHub 仓库失败: {}", e.getMessage(), e);
            deleteDirectory(cacheDir);
            throw new BizException("clone GitHub 仓库失败: " + e.getMessage());
        }

        return cachePath.toAbsolutePath().toString();
    }

    /**
     * 获取或验证本地缓存路径
     * <p>
     * 若缓存不存在则重新 clone，否则执行 fetch 获取最新变更。
     *
     * @param project 项目实体
     * @return 本地缓存目录的绝对路径
     */
    public String getOrRefreshLocalPath(Project project) {
        Path cachePath = getCachePath(project.getId());
        File cacheDir = cachePath.toFile();

        if (!cacheDir.exists() || !new File(cacheDir, ".git").exists()) {
            return cloneRepository(project);
        }

        try {
            try (Git git = Git.open(cacheDir)) {
                git.fetch()
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(githubToken, ""))
                        .call();
                log.debug("GitHub 仓库 fetch 完成: {}", project.getRepoUrl());
            }
        } catch (Exception e) {
            log.warn("GitHub 仓库 fetch 失败，使用本地缓存: {}", e.getMessage());
        }

        return cachePath.toAbsolutePath().toString();
    }

    /**
     * 根据 projectId 获取本地缓存路径
     */
    private Path getCachePath(Long projectId) {
        return Path.of(cloneBaseDir, String.valueOf(projectId));
    }

    /**
     * 将 GitHub URL 转换为 API URL
     * <p>
     * https://github.com/user/repo.git -> https://api.github.com/repos/user/repo
     */
    private String buildApiUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith(".git")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        String path = trimmed.replaceFirst("^https?://github\\.com/", "");
        return "https://api.github.com/repos/" + path;
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }

    // ==================== PR 相关 API ====================

    /**
     * 验证 GitHub Webhook 签名
     *
     * @param payload      原始请求体
     * @param signatureHeader X-Hub-Signature-256 请求头
     * @return true 表示签名有效
     */
    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Webhook Secret 未配置，跳过签名验证");
            return true;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = "sha256=" + HexFormat.of().formatHex(hash);
            return expected.equals(signatureHeader);
        } catch (Exception e) {
            log.error("Webhook 签名验证异常", e);
            return false;
        }
    }

    /**
     * 获取 PR 基本信息
     *
     * @param repoUrl GitHub 仓库 URL
     * @param prNumber PR 编号
     * @return PR 信息（标题、源分支、目标分支等）
     */
    public Map<String, Object> getPullRequestInfo(String repoUrl, int prNumber) {
        String apiUrl = buildApiUrl(repoUrl) + "/pulls/" + prNumber;
        try {
            String body = restClient.get()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + githubToken)
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("title", node.get("title").asText());
            info.put("headRef", node.get("head").get("ref").asText());
            info.put("baseRef", node.get("base").get("ref").asText());
            info.put("htmlUrl", node.get("html_url").asText());
            return info;
        } catch (Exception e) {
            log.error("获取 PR 信息失败: repo={}, pr={}", repoUrl, prNumber, e);
            throw new BizException("获取 PR 信息失败: " + e.getMessage());
        }
    }

    /**
     * 在 PR 下发布审查结果评论
     *
     * @param repoUrl  GitHub 仓库 URL
     * @param prNumber PR 编号
     * @param body     评论内容（Markdown）
     * @return 评论 URL
     */
    public String postPullRequestComment(String repoUrl, int prNumber, String body) {
        String apiUrl = buildApiUrl(repoUrl) + "/issues/" + prNumber + "/comments";
        try {
            Map<String, String> requestBody = Map.of("body", body);

            String response = restClient.post()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + githubToken)
                    .header("Accept", "application/vnd.github+json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);
            String commentUrl = node.get("html_url").asText();
            log.info("PR 评论已发布: {}", commentUrl);
            return commentUrl;
        } catch (Exception e) {
            log.error("发布 PR 评论失败: repo={}, pr={}", repoUrl, prNumber, e);
            throw new BizException("发布 PR 评论失败: " + e.getMessage());
        }
    }

    /**
     * 构建审查结果摘要 Markdown
     *
     * @param review 审查实体
     * @return Markdown 格式的审查摘要
     */
    public String buildReviewSummaryMarkdown(Review review) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🤖 AI 代码审查结果\n\n");
        sb.append("**审查 ID**: #").append(review.getId()).append("\n");
        sb.append("**耗时**: ").append(review.getDurationMs()).append("ms\n\n");

        sb.append("### 📊 问题统计\n\n");
        sb.append("| 严重程度 | 数量 |\n");
        sb.append("|---------|------|\n");
        sb.append("| 🔴 HIGH | ").append(review.getHighCount()).append(" |\n");
        sb.append("| 🟡 MEDIUM | ").append(review.getMediumCount()).append(" |\n");
        sb.append("| 🟢 LOW | ").append(review.getLowCount()).append(" |\n");
        sb.append("| **合计** | **").append(review.getTotalIssues()).append("** |\n");

        if (review.getTotalIssues() == 0) {
            sb.append("\n✅ 未发现问题，代码质量良好！\n");
        } else {
            sb.append("\n---\n");
            sb.append("*详细问题请查看 CodeAudit 审查详情页面*\n");
        }

        sb.append("\n> 由 CodeAudit AI 代码审查平台自动生成");
        return sb.toString();
    }

    /**
     * 获取 Webhook Secret（用于 Settings 页面展示）
     */
    public String getWebhookSecret() {
        return webhookSecret;
    }
}
