package com.codeaudit.service;

import com.codeaudit.dto.DiffBlock;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Git Diff 提取服务 — 基于 JGit 操作本地 Git 仓库
 * <p>
 * 核心功能：
 * <ul>
 *   <li>提取任意两个 commit / 分支之间的 diff</li>
 *   <li>自动过滤仅保留 .java 文件变更</li>
 *   <li>返回结构化的 DiffBlock 列表供审查引擎消费</li>
 * </ul>
 * <p>
 * 实现原理：
 * 通过 JGit 的 DiffFormatter 对两个 commit 对应的 Tree 进行逐文件 diff，
 * 并将 unified diff 输出转为 DiffBlock 结构体。
 *
 * @author CodeAudit Team
 */
@Service
public class GitDiffService {

    private static final Logger log = LoggerFactory.getLogger(GitDiffService.class);

    /**
     * 提取两个 Git 引用之间的 diff，仅保留 Java 文件
     *
     * @param repoPath   本地 Git 仓库绝对路径
     * @param fromCommit 源引用（分支名 / commit hash / HEAD~1 等）
     * @param toCommit   目标引用（分支名 / commit hash / HEAD 等）
     * @return 每个变更 Java 文件的 DiffBlock 列表，无变更时返回空列表
     * @throws RuntimeException 仓库路径无效或 Git 操作失败时抛出
     */
    public List<DiffBlock> extractDiffBetweenCommits(String repoPath, String fromCommit, String toCommit, String language) {
        List<DiffBlock> diffBlocks = new ArrayList<>();
        File repoDir = new File(repoPath);

        // 1. 打开 Git 仓库
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .build()) {

            // 2. 解析两个 commit 的 ObjectId
            ObjectId fromId = resolveCommit(repository, fromCommit);
            ObjectId toId = resolveCommit(repository, toCommit);

            // 3. 构建 diff 输出器（使用 RevWalk 解析 commit，避免 parseCommit 缓存泄漏）
            try (ObjectReader reader = repository.newObjectReader();
                 RevWalk revWalk = new RevWalk(repository);
                 ByteArrayOutputStream out = new ByteArrayOutputStream();
                 DiffFormatter formatter = new DiffFormatter(out)) {

                formatter.setRepository(repository);

                // 4. 构建 oldTree（源）和 newTree（目标）
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                RevCommit fromCommitObj = revWalk.parseCommit(fromId);
                oldTreeIter.reset(reader, fromCommitObj.getTree().getId());

                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                RevCommit toCommitObj = revWalk.parseCommit(toId);
                newTreeIter.reset(reader, toCommitObj.getTree().getId());

                // 5. 获取变更文件列表
                List<DiffEntry> diffs = formatter.scan(oldTreeIter, newTreeIter);

                // 6. 逐个文件提取 diff 内容
                for (DiffEntry entry : diffs) {
                    // 根据语言过滤文件类型
                    if (!shouldIncludeFile(entry.getNewPath(), entry.getOldPath(), language)) {
                        continue;
                    }

                    formatter.format(entry);
                    String diffContent = out.toString(StandardCharsets.UTF_8);
                    out.reset();

                    // 7. 封装为 DiffBlock 结构
                    DiffBlock block = new DiffBlock(
                        entry.getNewPath() != null ? entry.getNewPath() : entry.getOldPath(),
                        entry.getChangeType().name(),
                        countAddedLines(diffContent),
                        countRemovedLines(diffContent),
                        diffContent
                    );
                    diffBlocks.add(block);
                }
            }
        } catch (IOException e) {
            log.error("Git diff 提取失败，仓库路径: {}，错误: {}", repoPath, e.getMessage(), e);
            throw new RuntimeException("Git diff 提取失败: " + e.getMessage(), e);
        }

        log.info("成功提取 {} 个{}文件变更 ({}..{})", diffBlocks.size(),
                (language != null && !language.isBlank()) ? language : "Java",
                fromCommit, toCommit);
        return diffBlocks;
    }

    /**
     * 提取最新一次 commit 的 diff（HEAD~1..HEAD）的便捷方法
     *
     * @param repoPath 本地 Git 仓库绝对路径
     * @param language 项目编程语言
     * @return 最新 commit 中所有变更文件的 DiffBlock 列表
     */
    public List<DiffBlock> extractDiffForLatestCommit(String repoPath, String language) {
        return extractDiffBetweenCommits(repoPath, "HEAD~1", "HEAD", language);
    }

    /**
     * 根据语言决定是否包含该文件
     */
    private boolean shouldIncludeFile(String newPath, String oldPath, String language) {
        if (language == null || language.isBlank()) {
            return newPath.endsWith(".java") || oldPath.endsWith(".java");
        }
        return switch (language.toLowerCase()) {
            case "java" -> newPath.endsWith(".java") || oldPath.endsWith(".java");
            case "python" -> newPath.endsWith(".py") || oldPath.endsWith(".py");
            case "go" -> newPath.endsWith(".go") || oldPath.endsWith(".go");
            case "javascript" ->
                    newPath.endsWith(".js") || oldPath.endsWith(".js") ||
                    newPath.endsWith(".jsx") || oldPath.endsWith(".jsx");
            case "typescript" ->
                    newPath.endsWith(".ts") || oldPath.endsWith(".ts") ||
                    newPath.endsWith(".tsx") || oldPath.endsWith(".tsx");
            case "c", "c++" ->
                    newPath.endsWith(".c") || oldPath.endsWith(".c") ||
                    newPath.endsWith(".cpp") || oldPath.endsWith(".cpp") ||
                    newPath.endsWith(".h") || oldPath.endsWith(".h");
            default -> true; // 未知语言不过滤，审查所有文件
        };
    }

    /**
     * 将 Git 引用（分支名/commit hash/HEAD~N）解析为 ObjectId
     */
    private ObjectId resolveCommit(Repository repository, String commitRef) throws IOException {
        ObjectId id = repository.resolve(commitRef);
        if (id == null) {
            throw new IOException("无法解析 Git 引用: " + commitRef);
        }
        return id;
    }

    /**
     * 统计 diff 内容中新增行数（以 + 开头但不以 +++ 开头的行）
     */
    private int countAddedLines(String diffContent) {
        return (int) diffContent.lines()
                .filter(line -> line.startsWith("+") && !line.startsWith("+++"))
                .count();
    }

    /**
     * 统计 diff 内容中删除行数（以 - 开头但不以 --- 开头的行）
     */
    private int countRemovedLines(String diffContent) {
        return (int) diffContent.lines()
                .filter(line -> line.startsWith("-") && !line.startsWith("---"))
                .count();
    }
}
