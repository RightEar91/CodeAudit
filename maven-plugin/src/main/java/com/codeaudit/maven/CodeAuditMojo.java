package com.codeaudit.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CodeAudit Maven Plugin — triggers code review during Maven build lifecycle.
 * <p>
 * Bind to the verify phase to run review after tests. If issues exceed the
 * configured threshold, the build fails.
 *
 * <pre>
 * {@code
 * <plugin>
 *   <groupId>com.codeaudit</groupId>
 *   <artifactId>codeaudit-maven-plugin</artifactId>
 *   <version>0.1.0</version>
 *   <executions>
 *     <execution>
 *       <phase>verify</phase>
 *       <goals><goal>review</goal></goals>
 *     </execution>
 *   </executions>
 *   <configuration>
 *     <server>http://localhost:9090</server>
 *     <projectId>1</projectId>
 *     <fromRef>HEAD~1</fromRef>
 *     <toRef>HEAD</toRef>
 *     <threshold>10</threshold>
 *     <timeout>300</timeout>
 *   </configuration>
 * </plugin>
 * }
 * </pre>
 *
 * @author CodeAudit Team
 */
@Mojo(name = "review")
public class CodeAuditMojo extends AbstractMojo {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** CodeAudit server URL */
    @Parameter(property = "codeaudit.server", required = true)
    private String server;

    /** Project ID registered in CodeAudit */
    @Parameter(property = "codeaudit.projectId", required = true)
    private Long projectId;

    /** Source git ref */
    @Parameter(property = "codeaudit.fromRef", defaultValue = "HEAD~1")
    private String fromRef;

    /** Target git ref */
    @Parameter(property = "codeaudit.toRef", defaultValue = "HEAD")
    private String toRef;

    /** Maximum allowed issues before failing the build (0 = no limit) */
    @Parameter(property = "codeaudit.threshold", defaultValue = "0")
    private int threshold;

    /** Timeout in seconds */
    @Parameter(property = "codeaudit.timeout", defaultValue = "300")
    private int timeout;

    /** Comma-separated include paths */
    @Parameter(property = "codeaudit.includePaths")
    private String includePaths;

    /** Filter by author email */
    @Parameter(property = "codeaudit.author")
    private String author;

    /** ISO datetime since filter */
    @Parameter(property = "codeaudit.since")
    private String since;

    /** ISO datetime until filter */
    @Parameter(property = "codeaudit.until")
    private String until;

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("CodeAudit review starting...");
        getLog().info("  Server: " + server);
        getLog().info("  Project: " + projectId);
        getLog().info("  Range: " + fromRef + ".." + toRef);

        try {
            Long reviewId = createReview();
            if (reviewId == null) {
                throw new MojoExecutionException("Failed to create review");
            }
            getLog().info("Review created: id=" + reviewId);

            Map<String, Object> result = waitForCompletion(reviewId);
            if (result == null) {
                throw new MojoExecutionException("Review timed out");
            }

            String status = (String) result.get("status");
            if ("failed".equals(status)) {
                throw new MojoExecutionException("Review failed: " + result.get("errorMessage"));
            }

            int totalIssues = result.get("totalIssues") != null ? ((Number) result.get("totalIssues")).intValue() : 0;
            int highCount = result.get("highCount") != null ? ((Number) result.get("highCount")).intValue() : 0;
            int mediumCount = result.get("mediumCount") != null ? ((Number) result.get("mediumCount")).intValue() : 0;
            int lowCount = result.get("lowCount") != null ? ((Number) result.get("lowCount")).intValue() : 0;

            getLog().info("=====================================");
            getLog().info("  CodeAudit Review Complete");
            getLog().info("  Total Issues: " + totalIssues);
            getLog().info("  HIGH: " + highCount + "  MEDIUM: " + mediumCount + "  LOW: " + lowCount);
            getLog().info("=====================================");

            if (threshold > 0 && totalIssues > threshold) {
                throw new MojoExecutionException(
                        "Issues (" + totalIssues + ") exceed threshold (" + threshold + "), build failed");
            }
            getLog().info("Review passed: " + totalIssues + " issues, within threshold of " + threshold);

        } catch (Exception e) {
            if (e instanceof MojoExecutionException) throw (MojoExecutionException) e;
            throw new MojoExecutionException("CodeAudit review failed", e);
        }
    }

    private Long createReview() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "CodeAudit Maven Plugin Review");
        body.put("fromRef", fromRef);
        body.put("toRef", toRef);

        Map<String, Object> filters = new LinkedHashMap<>();
        if (includePaths != null && !includePaths.isBlank()) {
            filters.put("includePaths", includePaths.split(","));
        }
        if (author != null && !author.isBlank()) {
            filters.put("authorEmail", author);
        }
        if (since != null && !since.isBlank()) {
            filters.put("since", since);
        }
        if (until != null && !until.isBlank()) {
            filters.put("until", until);
        }
        if (!filters.isEmpty()) {
            body.put("filters", filters);
        }

        String json = MAPPER.writeValueAsString(body);
        String url = server + "/api/projects/" + projectId + "/reviews";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            Map<String, Object> result = MAPPER.readValue(response.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            return ((Number) data.get("id")).longValue();
        }
        getLog().error("HTTP " + response.statusCode() + ": " + response.body());
        return null;
    }

    private Map<String, Object> waitForCompletion(Long reviewId) throws Exception {
        String url = server + "/api/reviews/" + reviewId;
        HttpClient client = HttpClient.newHttpClient();
        long deadline = System.currentTimeMillis() + timeout * 1000L;

        while (System.currentTimeMillis() < deadline) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> result = MAPPER.readValue(response.body(), Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String status = (String) data.get("status");

            if ("completed".equals(status) || "failed".equals(status)) {
                return data;
            }

            int reviewed = data.get("reviewedFiles") != null ? ((Number) data.get("reviewedFiles")).intValue() : 0;
            int total = data.get("totalFiles") != null ? ((Number) data.get("totalFiles")).intValue() : 0;
            getLog().info("Progress: " + reviewed + "/" + total + " files (" + status + ")");
            TimeUnit.SECONDS.sleep(3);
        }
        return null;
    }
}
