package com.codeaudit.cli;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CodeAudit CLI — command-line tool for triggering code reviews in CI/CD pipelines.
 * <p>
 * Usage: java -jar codeaudit-cli.jar
 *   --server       CodeAudit server URL (e.g. http://localhost:9090)
 *   --project-id   Project ID to review
 *   --from-ref     Source git ref (default: HEAD~1)
 *   --to-ref       Target git ref (default: HEAD)
 *   --timeout      Wait timeout in seconds (default: 300)
 *   --threshold    Max allowed issues, fails exit code if exceeded
 *   --include-paths  Comma-separated include paths (optional)
 *   --author       Filter by author email (optional)
 *   --since        ISO datetime since filter (optional)
 *   --until        ISO datetime until filter (optional)
 *
 * Exit codes:
 *   0 = review completed, issues within threshold
 *   1 = CLI error (invalid args, network error)
 *   2 = review failed on server
 *   3 = review completed, issues exceed threshold
 *
 * @author CodeAudit Team
 */
public class CodeAuditCli {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        CliConfig config = parseArgs(args);
        if (config == null) {
            System.exit(1);
        }

        try {
            // Step 1: Create review
            Long reviewId = createReview(config);
            if (reviewId == null) {
                System.err.println("[ERROR] Failed to create review");
                System.exit(1);
            }
            System.out.println("[INFO] Review created: id=" + reviewId);

            // Step 2: Wait for completion
            Map<String, Object> result = waitForCompletion(config, reviewId);
            if (result == null) {
                System.err.println("[ERROR] Review timed out or failed");
                System.exit(2);
            }

            String status = (String) result.get("status");
            if ("failed".equals(status)) {
                System.err.println("[ERROR] Review failed on server: " + result.get("errorMessage"));
                System.exit(2);
            }

            int totalIssues = result.get("totalIssues") != null ? ((Number) result.get("totalIssues")).intValue() : 0;
            int highCount = result.get("highCount") != null ? ((Number) result.get("highCount")).intValue() : 0;
            int mediumCount = result.get("mediumCount") != null ? ((Number) result.get("mediumCount")).intValue() : 0;
            int lowCount = result.get("lowCount") != null ? ((Number) result.get("lowCount")).intValue() : 0;

            System.out.println("=====================================");
            System.out.println("  CodeAudit Review Complete");
            System.out.println("  Status: " + status);
            System.out.println("  Total Issues: " + totalIssues);
            System.out.println("  HIGH: " + highCount + "  MEDIUM: " + mediumCount + "  LOW: " + lowCount);
            System.out.println("  Duration: " + result.get("durationMs") + "ms");
            System.out.println("=====================================");

            if (config.threshold > 0 && totalIssues > config.threshold) {
                System.err.println("[FAIL] Issues (" + totalIssues + ") exceed threshold (" + config.threshold + ")");
                System.exit(3);
            }
            System.out.println("[OK] Review passed, issues within threshold");
            System.exit(0);

        } catch (Exception e) {
            System.err.println("[ERROR] CLI execution failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static CliConfig parseArgs(String[] args) {
        CliConfig config = new CliConfig();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--server": config.server = args[++i]; break;
                case "--project-id": config.projectId = Long.parseLong(args[++i]); break;
                case "--from-ref": config.fromRef = args[++i]; break;
                case "--to-ref": config.toRef = args[++i]; break;
                case "--timeout": config.timeout = Integer.parseInt(args[++i]); break;
                case "--threshold": config.threshold = Integer.parseInt(args[++i]); break;
                case "--include-paths": config.includePaths = args[++i]; break;
                case "--author": config.author = args[++i]; break;
                case "--since": config.since = args[++i]; break;
                case "--until": config.until = args[++i]; break;
                default:
                    System.err.println("Unknown option: " + args[i]);
                    printUsage();
                    return null;
            }
        }
        if (config.server == null || config.projectId == null) {
            System.err.println("Missing required options: --server and --project-id");
            printUsage();
            return null;
        }
        return config;
    }

    private static Long createReview(CliConfig config) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "CodeAudit CLI Review");
        body.put("fromRef", config.fromRef != null ? config.fromRef : "HEAD~1");
        body.put("toRef", config.toRef != null ? config.toRef : "HEAD");

        Map<String, Object> filters = new LinkedHashMap<>();
        if (config.includePaths != null && !config.includePaths.isBlank()) {
            filters.put("includePaths", config.includePaths.split(","));
        }
        if (config.author != null && !config.author.isBlank()) {
            filters.put("authorEmail", config.author);
        }
        if (config.since != null && !config.since.isBlank()) {
            filters.put("since", config.since);
        }
        if (config.until != null && !config.until.isBlank()) {
            filters.put("until", config.until);
        }
        if (!filters.isEmpty()) {
            body.put("filters", filters);
        }

        String json = MAPPER.writeValueAsString(body);
        String url = config.server + "/api/projects/" + config.projectId + "/reviews";

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
        System.err.println("[ERROR] Create review HTTP " + response.statusCode() + ": " + response.body());
        return null;
    }

    private static Map<String, Object> waitForCompletion(CliConfig config, Long reviewId) throws IOException, InterruptedException {
        String url = config.server + "/api/reviews/" + reviewId;
        HttpClient client = HttpClient.newHttpClient();
        long deadline = System.currentTimeMillis() + config.timeout * 1000L;

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
            System.out.printf("[PROGRESS] %s — %d/%d files reviewed\r", status, reviewed, total);
            TimeUnit.SECONDS.sleep(3);
        }
        return null;
    }

    private static void printUsage() {
        System.out.println("""
            Usage: java -jar codeaudit-cli.jar \\
              --server <URL> \\
              --project-id <ID> \\
              [--from-ref <REF>] \\
              [--to-ref <REF>] \\
              [--timeout <SECONDS>] \\
              [--threshold <MAX_ISSUES>] \\
              [--include-paths <PATHS>] \\
              [--author <EMAIL>] \\
              [--since <ISO_DATETIME>] \\
              [--until <ISO_DATETIME>]
            
            Exit codes:
              0 = review passed
              1 = CLI error
              2 = review failed
              3 = issues exceed threshold
            """);
    }

    static class CliConfig {
        String server;
        Long projectId;
        String fromRef = "HEAD~1";
        String toRef = "HEAD";
        int timeout = 300;
        int threshold = 0;
        String includePaths;
        String author;
        String since;
        String until;
    }
}
