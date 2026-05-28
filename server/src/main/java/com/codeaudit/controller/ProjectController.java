package com.codeaudit.controller;

import com.codeaudit.common.BizException;
import com.codeaudit.common.Response;
import com.codeaudit.dto.BranchInfo;
import com.codeaudit.dto.CommitInfo;
import com.codeaudit.dto.DiffBlock;
import com.codeaudit.entity.Project;
import com.codeaudit.service.GitService;
import com.codeaudit.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目管理 REST 控制器
 * <p>
 * 提供项目的 CRUD 接口，添加时会校验 Git 仓库路径有效性。
 * 所有响应统一封装为 {@link Response} 格式。
 * <p>
 * 路由设计：
 * <ul>
 *   <li>{@code GET    /api/projects}     — 项目列表（支持按名称搜索）</li>
 *   <li>{@code GET    /api/projects/:id} — 项目详情</li>
 *   <li>{@code POST   /api/projects}     — 添加项目（校验 Git 路径）</li>
 *   <li>{@code PUT    /api/projects/:id} — 更新项目</li>
 *   <li>{@code DELETE /api/projects/:id} — 删除项目（级联删除审查记录）</li>
 * </ul>
 *
 * @author CodeAudit Team
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final GitService gitService;

    public ProjectController(ProjectService projectService, GitService gitService) {
        this.projectService = projectService;
        this.gitService = gitService;
    }

    /**
     * 获取项目列表，支持按名称模糊搜索和分页
     * <p>
     * 分页参数示例：?page=0&size=20&sort=createdAt,desc
     */
    @GetMapping
    public Response<Page<Project>> list(@RequestParam(required = false) String name, Pageable pageable) {
        if (name != null && !name.isBlank()) {
            Page<Project> projects = projectService.searchByName(name, pageable);
            return Response.ok(projects);
        }
        Page<Project> projects = projectService.listAll(pageable);
        return Response.ok(projects);
    }

    /**
     * 按 ID 查询项目详情
     *
     * @throws BizException 项目不存在时由全局异常处理器拦截（404）
     */
    @GetMapping("/{id}")
    public Response<Project> getById(@PathVariable Long id) {
        Project project = projectService.findById(id)
                .orElseThrow(() -> new BizException(404, "项目不存在: " + id));
        return Response.ok(project);
    }

    /**
     * 添加项目（校验 Git 仓库路径有效性）
     * <p>
     * 请求体示例：
     * <pre>{@code
     * {
     *   "name": "MyProject",
     *   "repoPath": "E:/projects/my-app",
     *   "language": "Java"
     * }
     * }</pre>
     */
    @PostMapping
    public Response<Project> create(@Valid @RequestBody Project project) {
        Project created = projectService.create(project);
        return Response.created(created);
    }

    /**
     * 更新项目信息（仅更新传入的非空字段）
     */
    @PutMapping("/{id}")
    public Response<Project> update(@PathVariable Long id, @RequestBody Project project) {
        Project updated = projectService.update(id, project);
        return Response.ok(updated);
    }

    /**
     * 删除项目，级联删除关联的审查记录和问题
     */
    @DeleteMapping("/{id}")
    public Response<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Response.ok();
    }

    /**
     * 获取项目的分支列表
     */
    @GetMapping("/{id}/branches")
    public Response<List<BranchInfo>> getBranches(@PathVariable Long id) {
        Project project = projectService.findById(id)
                .orElseThrow(() -> new BizException(404, "项目不存在: " + id));
        String repoPath = gitService.resolveRepoPath(project);
        List<BranchInfo> branches = gitService.listBranches(repoPath);
        return Response.ok(branches);
    }

    /**
     * 获取项目的最近 commit 列表（默认最多 30 条）
     */
    @GetMapping("/{id}/commits")
    public Response<List<CommitInfo>> getCommits(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "30") int count) {
        Project project = projectService.findById(id)
                .orElseThrow(() -> new BizException(404, "项目不存在: " + id));
        String repoPath = gitService.resolveRepoPath(project);
        List<CommitInfo> commits = gitService.listRecentCommits(repoPath, count);
        return Response.ok(commits);
    }

    /**
     * 预览两个引用之间的变更文件列表
     *
     * @param fromRef 源引用（默认 HEAD~1）
     * @param toRef   目标引用（默认 HEAD）
     */
    @GetMapping("/{id}/diff-preview")
    public Response<List<DiffBlock>> previewDiff(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "HEAD~1") String fromRef,
                                                  @RequestParam(defaultValue = "HEAD") String toRef) {
        Project project = projectService.findById(id)
                .orElseThrow(() -> new BizException(404, "项目不存在: " + id));
        String language = project.getLanguage() != null ? project.getLanguage() : "Java";
        String repoPath = gitService.resolveRepoPath(project);
        List<DiffBlock> diffBlocks = gitService.previewDiff(repoPath, fromRef, toRef, language);
        return Response.ok(diffBlocks);
    }
}
