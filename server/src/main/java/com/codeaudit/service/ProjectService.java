package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Project;
import com.codeaudit.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * 项目管理服务
 * <p>
 * 提供项目的 CRUD 操作，支持两种仓库类型：
 * <ul>
 *   <li><b>LOCAL</b> — 本地 Git 仓库，校验路径是否存在及是否为有效 Git 仓库</li>
 *   <li><b>GITHUB</b> — GitHub 远程仓库，校验 URL 格式及 Token 有效性，并自动 clone 到本地缓存</li>
 * </ul>
 * 所有业务校验失败均抛出 {@link BizException}，由全局异常处理器统一转换为 Response 响应。
 *
 * @author CodeAudit Team
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final GithubService githubService;

    public ProjectService(ProjectRepository projectRepository, GithubService githubService) {
        this.projectRepository = projectRepository;
        this.githubService = githubService;
    }

    /**
     * 获取所有已接入的项目列表
     */
    public List<Project> listAll() {
        return projectRepository.findAll();
    }

    /**
     * 分页获取项目列表
     */
    public Page<Project> listAll(Pageable pageable) {
        return projectRepository.findAll(pageable);
    }

    /**
     * 按 ID 查询项目
     */
    public Optional<Project> findById(Long id) {
        return projectRepository.findById(id);
    }

    /**
     * 按名称模糊搜索项目，不区分大小写
     */
    public List<Project> searchByName(String name) {
        return projectRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * 按名称模糊搜索项目（分页）
     */
    public Page<Project> searchByName(String name, Pageable pageable) {
        return projectRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    /**
     * 添加新项目
     * <p>
     * 根据 repoType 执行不同的校验逻辑：
     * <ul>
     *   <li>LOCAL  — 校验本地路径有效性并检查是否重复</li>
     *   <li>GITHUB — 校验 URL 格式及 Token，然后 clone 到本地缓存并写入 repoPath</li>
     * </ul>
     *
     * @param project 项目实体（name / repoPath 或 repoUrl 必填）
     * @return 保存后的项目实体（含自动生成的 ID，repoPath 已指向本地路径）
     * @throws BizException 路径无效或已存在时抛出（400）
     */
    @Transactional
    public Project create(Project project) {
        String repoType = project.getRepoType() != null ? project.getRepoType() : "LOCAL";

        if ("GITHUB".equalsIgnoreCase(repoType)) {
            project.setRepoType("GITHUB");
            String url = project.getRepoUrl();
            if (url == null || url.isBlank()) {
                throw new BizException("GitHub 仓库地址不能为空");
            }
            githubService.validateRepository(url, githubService.getGithubToken());
            if (projectRepository.existsByRepoUrl(url)) {
                throw new BizException("该 GitHub 仓库已添加：" + url);
            }
            project = projectRepository.save(project);

            String localPath = githubService.cloneRepository(project);
            project.setRepoPath(localPath);
            project = projectRepository.save(project);

            log.info("添加 GitHub 项目: {} ({}), 缓存路径: {}", project.getName(), url, localPath);
        } else {
            project.setRepoType("LOCAL");
            validateLocalRepoPath(project.getRepoPath());
            if (projectRepository.existsByRepoPath(project.getRepoPath())) {
                throw new BizException("该仓库路径已添加：" + project.getRepoPath());
            }
            project = projectRepository.save(project);
            log.info("添加本地项目: {} ({})", project.getName(), project.getRepoPath());
        }

        return project;
    }

    /**
     * 更新项目信息（名称/路径/语言）
     * <p>
     * 仅更新传入的非空字段，路径变更时会重新校验。
     *
     * @param id      项目 ID
     * @param updated 包含待更新字段的项目对象
     * @return 更新后的项目实体
     * @throws BizException 项目不存在（404）或路径无效（400）
     */
    @Transactional
    public Project update(Long id, Project updated) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new BizException(404, "项目不存在: " + id));
        if (updated.getName() != null) {
            project.setName(updated.getName());
        }
        if (updated.getRepoPath() != null) {
            validateLocalRepoPath(updated.getRepoPath());
            if (!updated.getRepoPath().equals(project.getRepoPath())
                    && projectRepository.existsByRepoPath(updated.getRepoPath())) {
                throw new BizException("该仓库路径已被其他项目使用：" + updated.getRepoPath());
            }
            project.setRepoPath(updated.getRepoPath());
        }
        if (updated.getLanguage() != null) {
            project.setLanguage(updated.getLanguage());
        }
        log.info("更新项目: {} (ID={})", project.getName(), id);
        return projectRepository.save(project);
    }

    /**
     * 删除项目（级联删除关联的审查记录和问题）
     *
     * @param id 项目 ID
     * @throws BizException 项目不存在时抛出（404）
     */
    @Transactional
    public void delete(Long id) {
        if (!projectRepository.existsById(id)) {
            throw new BizException(404, "项目不存在: " + id);
        }
        log.info("删除项目: ID={}", id);
        projectRepository.deleteById(id);
    }

    /**
     * 校验本地仓库路径有效性：
     * <ol>
     *   <li>路径指向的目录必须存在</li>
     *   <li>目录下必须包含 .git 子目录</li>
     * </ol>
     *
     * @throws BizException 路径无效时抛出（400）
     */
    private void validateLocalRepoPath(String repoPath) {
        if (repoPath == null || repoPath.isBlank()) {
            throw new BizException("仓库路径不能为空");
        }
        File repoDir = new File(repoPath);
        if (!repoDir.exists() || !repoDir.isDirectory()) {
            throw new BizException("仓库路径不存在或不是目录：" + repoPath);
        }
        File gitDir = new File(repoDir, ".git");
        if (!gitDir.exists() || !gitDir.isDirectory()) {
            throw new BizException("路径不是有效的 Git 仓库（缺少 .git 目录）：" + repoPath);
        }
    }
}
