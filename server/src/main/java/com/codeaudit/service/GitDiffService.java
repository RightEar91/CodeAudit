package com.codeaudit.service;

import com.codeaudit.common.BizException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

            // 3. 校验引用拓扑顺序：toRef 不应是 fromRef 的祖先（避免逆向 diff）
            try (RevWalk topoWalk = new RevWalk(repository)) {
                RevCommit fromCommitParsed = topoWalk.parseCommit(fromId);
                RevCommit toCommitParsed = topoWalk.parseCommit(toId);
                if (topoWalk.isMergedInto(toCommitParsed, fromCommitParsed)) {
                    throw new BizException("源引用（fromRef）不应比目标引用（toRef）更新，请交换两者顺序");
                }
            }

            // 4. 构建 diff 输出器（使用 RevWalk 解析 commit，避免 parseCommit 缓存泄漏）
            try (ObjectReader reader = repository.newObjectReader();
                 RevWalk revWalk = new RevWalk(repository);
                 ByteArrayOutputStream out = new ByteArrayOutputStream();
                 DiffFormatter formatter = new DiffFormatter(out)) {

                formatter.setRepository(repository);

                // 5. 构建 oldTree（源）和 newTree（目标）
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                RevCommit fromCommitObj = revWalk.parseCommit(fromId);
                oldTreeIter.reset(reader, fromCommitObj.getTree().getId());

                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                RevCommit toCommitObj = revWalk.parseCommit(toId);
                newTreeIter.reset(reader, toCommitObj.getTree().getId());

                // 6. 获取变更文件列表
                List<DiffEntry> diffs = formatter.scan(oldTreeIter, newTreeIter);

                // 7. 逐个文件提取 diff 内容
                for (DiffEntry entry : diffs) {
                    // 根据语言过滤文件类型
                    if (!shouldIncludeFile(entry.getNewPath(), entry.getOldPath(), language)) {
                        continue;
                    }

                    formatter.format(entry);
                    String diffContent = out.toString(StandardCharsets.UTF_8);
                    out.reset();

                    String filePath = getFilePath(entry);
                    diffContent = cleanDiffHeader(diffContent, filePath);

                    DiffBlock block = new DiffBlock(
                        filePath,
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
     * 提取 DiffEntry 的实际文件路径
     * <p>
     * ADD 类型: oldPath 为 /dev/null，应取 newPath
     * DELETE 类型: newPath 为 /dev/null，应取 oldPath
     * MODIFY/RENAME 类型: 取 newPath
     */
    private String getFilePath(DiffEntry entry) {
        String newPath = entry.getNewPath();
        String oldPath = entry.getOldPath();
        boolean isDevNull = "/dev/null".equals(newPath) || "/dev/null".equals(oldPath);
        if (!isDevNull) {
            return newPath != null ? newPath : oldPath;
        }
        return "/dev/null".equals(newPath) ? oldPath : newPath;
    }

    /**
     * 清理 diff 头部中的 /dev/null 引用，使用实际文件路径替代
     */
    private String cleanDiffHeader(String diffContent, String filePath) {
        return diffContent
                .replace("--- /dev/null", "--- a/" + filePath)
                .replace("+++ /dev/null", "+++ b/" + filePath);
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
     * 按 commit 逐一提取 diff，按文件路径去重（保留最新变更）
     * <p>
     * 用于作者/时间段过滤场景：先按条件筛选 commit 列表，
     * 再对每个 commit 提取其 parent 之间的 diff。
     * 同一文件多次变更时，后出现的 DiffBlock 覆盖前者。
     *
     * @param repoPath  本地 Git 仓库绝对路径
     * @param commits   待提取 diff 的 RevCommit 列表
     * @param language  项目编程语言
     * @return 去重后的 DiffBlock 列表
     */
    public List<DiffBlock> extractDiffForCommits(String repoPath, List<RevCommit> commits, String language) {
        Map<String, DiffBlock> fileMap = new LinkedHashMap<>();

        for (RevCommit commit : commits) {
            List<DiffBlock> commitDiffs = extractDiffForSingleCommit(repoPath, commit, language);
            for (DiffBlock block : commitDiffs) {
                fileMap.put(block.filePath(), block);
            }
        }

        return new ArrayList<>(fileMap.values());
    }

    /**
     * 提取单个 commit 与其父 commit 之间的 diff
     */
    private List<DiffBlock> extractDiffForSingleCommit(String repoPath, RevCommit commit, String language) {
        List<DiffBlock> diffBlocks = new ArrayList<>();
        if (commit.getParentCount() == 0) {
            return diffBlocks;
        }

        File repoDir = new File(repoPath);
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .build();
             ObjectReader reader = repository.newObjectReader();
             RevWalk revWalk = new RevWalk(repository);
             ByteArrayOutputStream out = new ByteArrayOutputStream();
             DiffFormatter formatter = new DiffFormatter(out)) {

            formatter.setRepository(repository);

            RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, parent.getTree().getId());

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree().getId());

            List<DiffEntry> diffs = formatter.scan(oldTreeIter, newTreeIter);

            for (DiffEntry entry : diffs) {
                if (!shouldIncludeFile(entry.getNewPath(), entry.getOldPath(), language)) {
                    continue;
                }

                formatter.format(entry);
                String diffContent = out.toString(StandardCharsets.UTF_8);
                out.reset();

                String filePath = getFilePath(entry);
                diffContent = cleanDiffHeader(diffContent, filePath);

                DiffBlock block = new DiffBlock(
                        filePath,
                        entry.getChangeType().name(),
                        countAddedLines(diffContent),
                        countRemovedLines(diffContent),
                        diffContent
                );
                diffBlocks.add(block);
            }
        } catch (IOException e) {
            log.warn("提取 commit {} 的 diff 失败: {}", commit.getId().abbreviate(7).name(), e.getMessage());
        }

        return diffBlocks;
    }

    /**
     * 根据语言决定是否包含该文件
     */
    private boolean shouldIncludeFile(String newPath, String oldPath, String language) {
        String lang = (language != null && !language.isBlank()) ? language.toLowerCase() : "java";
        return switch (lang) {
            case "java" -> newPath.endsWith(".java") || oldPath.endsWith(".java");
            case "python" -> newPath.endsWith(".py") || oldPath.endsWith(".py");
            case "go" -> newPath.endsWith(".go") || oldPath.endsWith(".go");
            case "javascript" ->
                    newPath.endsWith(".js") || oldPath.endsWith(".js") ||
                    newPath.endsWith(".jsx") || oldPath.endsWith(".jsx");
            case "typescript" ->
                    newPath.endsWith(".ts") || oldPath.endsWith(".ts") ||
                    newPath.endsWith(".tsx") || oldPath.endsWith(".tsx");
            case "kotlin" ->
                    newPath.endsWith(".kt") || oldPath.endsWith(".kt") ||
                    newPath.endsWith(".kts") || oldPath.endsWith(".kts");
            case "rust" -> newPath.endsWith(".rs") || oldPath.endsWith(".rs");
            case "c", "c++", "cpp" ->
                    newPath.endsWith(".c") || oldPath.endsWith(".c") ||
                    newPath.endsWith(".cpp") || oldPath.endsWith(".cpp") ||
                    newPath.endsWith(".cc") || oldPath.endsWith(".cc") ||
                    newPath.endsWith(".cxx") || oldPath.endsWith(".cxx") ||
                    newPath.endsWith(".h") || oldPath.endsWith(".h") ||
                    newPath.endsWith(".hpp") || oldPath.endsWith(".hpp");
            default -> true;
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
