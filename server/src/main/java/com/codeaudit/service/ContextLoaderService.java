package com.codeaudit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Context-aware review service — loads related source files for cross-file analysis.
 * Parses import statements and method calls to identify related files in the repo.
 */
@Service
public class ContextLoaderService {

    private static final Logger log = LoggerFactory.getLogger(ContextLoaderService.class);
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+([\\w.]+)", Pattern.MULTILINE);
    private static final Pattern METHOD_CALL = Pattern.compile("\\b(\\w+)\\s*\\(");

    /**
     * Load context files related to a changed file.
     * Resolves imports within the same repo and loads matching source files.
     *
     * @param repoPath     repository root path
     * @param filePath     path of the changed file (relative to repo root)
     * @param diffContent  the diff content (used to identify method calls)
     * @return map of file path -> file content for context files
     */
    public Map<String, String> loadContext(String repoPath, String filePath, String diffContent) {
        Map<String, String> context = new LinkedHashMap<>();
        if (repoPath == null || filePath == null) return context;

        Path repoRoot = Path.of(repoPath);
        Path sourceDir = findSourceRoot(repoRoot);

        // Load the full current file if it exists
        Path currentFile = repoRoot.resolve(filePath);
        if (Files.exists(currentFile)) {
            try {
                context.put(filePath + " (full)", Files.readString(currentFile));
            } catch (IOException ignored) {}
        }

        // Find imports and try to load those files
        Set<String> importedClasses = extractImports(diffContent);
        for (String className : importedClasses) {
            Path classFile = resolveClassFile(sourceDir, repoRoot, className);
            if (classFile != null && !classFile.equals(currentFile)) {
                try {
                    String content = Files.readString(classFile);
                    String relPath = repoRoot.relativize(classFile).toString();
                    context.put(relPath, content);
                    if (context.size() >= 5) break; // Limit to 5 context files
                } catch (IOException ignored) {}
            }
        }

        log.debug("加载上下文文件: {} 个", context.size());
        return context;
    }

    private Set<String> extractImports(String content) {
        Set<String> classes = new LinkedHashSet<>();
        Matcher m = IMPORT_PATTERN.matcher(content);
        while (m.find()) {
            classes.add(m.group(1));
        }
        return classes;
    }

    private Path findSourceRoot(Path repoRoot) {
        // Common Java source roots
        String[] roots = {"src/main/java", "src/main/kotlin", "src"};
        for (String r : roots) {
            Path p = repoRoot.resolve(r);
            if (Files.isDirectory(p)) return p;
        }
        return repoRoot;
    }

    private Path resolveClassFile(Path sourceDir, Path repoRoot, String className) {
        String path = className.replace('.', '/');
        String[] extensions = {".java", ".kt", ".rs", ".py", ".go", ".ts", ".js", ".c", ".cpp"};
        for (String ext : extensions) {
            Path p = sourceDir.resolve(path + ext);
            if (Files.exists(p)) return p;
            Path pRepo = repoRoot.resolve(path + ext);
            if (Files.exists(pRepo)) return pRepo;
        }
        return null;
    }

    /**
     * Build context snippet for prompt injection
     */
    public String buildContextSnippet(Map<String, String> contextFiles) {
        if (contextFiles.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n--- 相关上下文文件 ---\n");
        for (Map.Entry<String, String> entry : contextFiles.entrySet()) {
            String snippet = entry.getValue().length() > 3000
                    ? entry.getValue().substring(0, 3000) + "\n... (truncated)"
                    : entry.getValue();
            sb.append("\n文件: ").append(entry.getKey()).append("\n```\n").append(snippet).append("\n```\n");
        }
        return sb.toString();
    }
}
