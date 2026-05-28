package com.codeaudit.service;

import com.codeaudit.dto.BranchInfo;
import com.codeaudit.dto.CommitInfo;
import com.codeaudit.dto.DiffBlock;
import com.codeaudit.dto.ReviewFilter;
import com.codeaudit.entity.Project;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final GitDiffService gitDiffService;
    private final GithubService githubService;

    public GitService(GitDiffService gitDiffService, GithubService githubService) {
        this.gitDiffService = gitDiffService;
        this.githubService = githubService;
    }

    /**
     * 根据仓库类型解析有效的本地路径
     * <p>
     * LOCAL 类型直接返回 repoPath；GITHUB 类型会先 fetch 确保本地缓存最新。
     */
    public String resolveRepoPath(Project project) {
        if ("GITHUB".equalsIgnoreCase(project.getRepoType())) {
            return githubService.getOrRefreshLocalPath(project);
        }
        return project.getRepoPath();
    }

    public List<BranchInfo> listBranches(String repoPath) {
        List<BranchInfo> branches = new ArrayList<>();
        File repoDir = new File(repoPath);

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .build()) {

            List<Ref> refs = repository.getRefDatabase().getRefsByPrefix("refs/heads/");
            for (Ref ref : refs) {
                String branchName = ref.getName().substring("refs/heads/".length());
                boolean isHead = branchName.equals(getHeadBranch(repository));
                branches.add(new BranchInfo(branchName, isHead));
            }
        } catch (IOException e) {
            log.error("获取分支列表失败: {}", repoPath, e);
            throw new RuntimeException("获取分支列表失败: " + e.getMessage(), e);
        }

        branches.sort(Comparator.comparing(BranchInfo::isHead).reversed()
                .thenComparing(BranchInfo::name));

        return branches;
    }

    public List<CommitInfo> listRecentCommits(String repoPath, int maxCount) {
        List<CommitInfo> commits = new ArrayList<>();
        File repoDir = new File(repoPath);

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .build();
             RevWalk revWalk = new RevWalk(repository)) {

            ObjectId headId = repository.resolve("HEAD");
            if (headId == null) {
                return commits;
            }

            revWalk.markStart(revWalk.parseCommit(headId));

            int count = 0;
            for (RevCommit commit : revWalk) {
                if (count >= maxCount) {
                    break;
                }
                String shortHash = commit.getId().abbreviate(7).name();
                String message = commit.getShortMessage();
                commits.add(new CommitInfo(commit.getId().getName(), shortHash, message));
                count++;
            }
        } catch (IOException e) {
            log.error("获取 commit 列表失败: {}", repoPath, e);
            throw new RuntimeException("获取 commit 列表失败: " + e.getMessage(), e);
        }

        return commits;
    }

    public List<DiffBlock> previewDiff(String repoPath, String fromRef, String toRef, String language) {
        return gitDiffService.extractDiffBetweenCommits(repoPath, fromRef, toRef, language);
    }

    /**
     * 获取过滤后的 diff（支持作者/时间过滤）
     * <p>
     * 当 ReviewFilter 包含 authorEmail 或 since/until 时，
     * 先通过 git log 筛选符合条件的 commit，再逐一提取每个 commit 的 diff。
     * 当无作者/时间过滤条件时，等效于 previewDiff。
     *
     * @param repoPath 本地仓库路径
     * @param fromRef  源引用
     * @param toRef    目标引用
     * @param language 项目语言
     * @param filter   过滤条件
     * @return 过滤后的 DiffBlock 列表
     */
    public List<DiffBlock> getFilteredDiffBlocks(String repoPath, String fromRef, String toRef,
                                                  String language, ReviewFilter filter) {
        boolean needCommitFilter = filter != null
                && ((filter.getAuthorEmail() != null && !filter.getAuthorEmail().isBlank())
                || (filter.getSince() != null && !filter.getSince().isBlank())
                || (filter.getUntil() != null && !filter.getUntil().isBlank()));

        if (!needCommitFilter) {
            return gitDiffService.extractDiffBetweenCommits(repoPath, fromRef, toRef, language);
        }

        List<RevCommit> filteredCommits = filterCommits(repoPath, fromRef, toRef, filter);
        if (filteredCommits.isEmpty()) {
            return List.of();
        }

        log.info("按作者/时间过滤后剩余 {} 个 commit", filteredCommits.size());
        return gitDiffService.extractDiffForCommits(repoPath, filteredCommits, language);
    }

    /**
     * 获取两个引用之间的 commit 列表，并按作者/时间过滤
     */
    private List<RevCommit> filterCommits(String repoPath, String fromRef, String toRef, ReviewFilter filter) {
        List<RevCommit> result = new ArrayList<>();
        File repoDir = new File(repoPath);

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(repoDir, ".git"))
                .readEnvironment()
                .build();
             RevWalk revWalk = new RevWalk(repository)) {

            ObjectId fromId = repository.resolve(fromRef);
            ObjectId toId = repository.resolve(toRef);
            if (fromId == null || toId == null) {
                log.warn("无法解析引用: from={}, to={}", fromRef, toRef);
                return result;
            }

            revWalk.markStart(revWalk.parseCommit(toId));
            revWalk.markUninteresting(revWalk.parseCommit(fromId));

            for (RevCommit commit : revWalk) {
                if (!matchAuthor(commit, filter.getAuthorEmail())) {
                    continue;
                }
                if (!matchTimeRange(commit, filter.getSince(), filter.getUntil())) {
                    continue;
                }
                result.add(commit);
            }
        } catch (IOException e) {
            log.error("过滤 commit 失败: {}", repoPath, e);
            throw new RuntimeException("过滤 commit 失败: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 检查 commit 作者是否匹配（支持姓名和邮箱模糊匹配）
     */
    private boolean matchAuthor(RevCommit commit, String authorEmail) {
        if (authorEmail == null || authorEmail.isBlank()) {
            return true;
        }
        String author = commit.getAuthorIdent().getName();
        String email = commit.getAuthorIdent().getEmailAddress();
        String lower = authorEmail.toLowerCase();
        return (author != null && author.toLowerCase().contains(lower))
                || (email != null && email.toLowerCase().contains(lower));
    }

    /**
     * 检查 commit 时间是否在指定范围内
     */
    private boolean matchTimeRange(RevCommit commit, String since, String until) {
        Instant commitTime = Instant.ofEpochSecond(commit.getCommitTime());
        LocalDateTime commitDateTime = commitTime.atZone(ZoneId.systemDefault()).toLocalDateTime();

        if (since != null && !since.isBlank()) {
            LocalDateTime sinceTime = LocalDateTime.parse(since, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (commitDateTime.isBefore(sinceTime)) {
                return false;
            }
        }
        if (until != null && !until.isBlank()) {
            LocalDateTime untilTime = LocalDateTime.parse(until, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (commitDateTime.isAfter(untilTime)) {
                return false;
            }
        }
        return true;
    }

    private String getHeadBranch(Repository repository) throws IOException {
        String fullRef = repository.getFullBranch();
        if (fullRef != null && fullRef.startsWith("refs/heads/")) {
            return fullRef.substring("refs/heads/".length());
        }
        return "";
    }
}
