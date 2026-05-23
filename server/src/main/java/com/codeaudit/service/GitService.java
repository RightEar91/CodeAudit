package com.codeaudit.service;

import com.codeaudit.dto.BranchInfo;
import com.codeaudit.dto.CommitInfo;
import com.codeaudit.dto.DiffBlock;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class GitService {

    private static final Logger log = LoggerFactory.getLogger(GitService.class);

    private final GitDiffService gitDiffService;

    public GitService(GitDiffService gitDiffService) {
        this.gitDiffService = gitDiffService;
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

    private String getHeadBranch(Repository repository) throws IOException {
        String fullRef = repository.getFullBranch();
        if (fullRef != null && fullRef.startsWith("refs/heads/")) {
            return fullRef.substring("refs/heads/".length());
        }
        return "";
    }
}
