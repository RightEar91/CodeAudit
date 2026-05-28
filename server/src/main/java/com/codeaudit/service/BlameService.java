package com.codeaudit.service;

import com.codeaudit.entity.Issue;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * Git Blame 服务 — 基于 JGit BlameCommand 将 Issue 关联到代码作者
 * <p>
 * 审查完成后调用，对每个 Issue 的 filePath + lineNumber 执行 git blame，
 * 填充其 authorName 和 authorEmail 字段。
 *
 * @author CodeAudit Team
 */
@Service
public class BlameService {

    private static final Logger log = LoggerFactory.getLogger(BlameService.class);

    /**
     * 对 Issue 列表执行 git blame，关联作者信息
     * <p>
     * 按文件分组批量处理，同一文件只执行一次 blame，
     * 然后对每个 Issue 的 lineNumber 查表填充。
     *
     * @param repoPath 本地 Git 仓库绝对路径
     * @param toRef    目标引用（blame 使用的 commit）
     * @param issues   待填充作者信息的 Issue 列表
     */
    public void fillBlameInfo(String repoPath, String toRef, List<Issue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }

        File repoDir = new File(repoPath);
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .build()) {

            ObjectId commitId = repository.resolve(toRef);
            if (commitId == null) {
                log.warn("无法解析引用 {}，跳过 blame", toRef);
                return;
            }

            issues.stream()
                    .filter(issue -> issue.getFilePath() != null && issue.getLineNumber() != null)
                    .forEach(issue -> fillSingleIssue(repository, commitId, issue));

        } catch (Exception e) {
            log.error("Blame 仓库打开失败: {}", repoPath, e);
        }
    }

    /**
     * 对单个 Issue 执行 blame
     */
    private void fillSingleIssue(Repository repository, ObjectId commitId, Issue issue) {
        try (Git git = new Git(repository)) {
            BlameResult blameResult = git.blame()
                    .setFilePath(issue.getFilePath())
                    .setStartCommit(commitId)
                    .call();

            if (blameResult == null) {
                log.debug("文件 {} 无 blame 结果", issue.getFilePath());
                return;
            }

            int lineNumber = issue.getLineNumber();
            if (lineNumber <= 0 || lineNumber > blameResult.getResultContents().size()) {
                log.debug("行号 {} 超出文件 {} 范围", lineNumber, issue.getFilePath());
                return;
            }

            PersonIdent author = blameResult.getSourceAuthor(lineNumber - 1);
            if (author != null) {
                issue.setAuthorName(author.getName());
                issue.setAuthorEmail(author.getEmailAddress());
            }
        } catch (Exception e) {
            log.warn("Blame 文件 {} 失败: {}", issue.getFilePath(), e.getMessage());
        }
    }
}
