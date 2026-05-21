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
 * 提供项目的 CRUD 操作，并在添加/更新项目时校验 Git 仓库路径的有效性。
 * 校验规则：
 * <ul>
 *   <li>路径必须存在且为目录</li>
 *   <li>路径下必须包含 .git 子目录（即必须是有效的 Git 仓库）</li>
 *   <li>不允许重复添加同一路径</li>
 * </ul>
 * <p>
 * 所有业务校验失败均抛出 {@link BizException}，由全局异常处理器统一转换为 Response 响应。
 *
 * @author CodeAudit Team
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
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
     * 执行前会校验仓库路径有效性并检查是否重复。
     *
     * @param project 项目实体（name / repoPath 必填）
     * @return 保存后的项目实体（含自动生成的 ID）
     * @throws BizException 路径无效或已存在时抛出（400）
     */
    @Transactional
    public Project create(Project project) {
        validateRepoPath(project.getRepoPath());
        if (projectRepository.existsByRepoPath(project.getRepoPath())) {
            throw new BizException("该仓库路径已添加：" + project.getRepoPath());
        }
        log.info("添加项目: {} ({})", project.getName(), project.getRepoPath());
        return projectRepository.save(project);
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
            validateRepoPath(updated.getRepoPath());
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
     * 校验仓库路径有效性：
     * <ol>
     *   <li>路径指向的目录必须存在</li>
     *   <li>目录下必须包含 .git 子目录</li>
     * </ol>
     *
     * @throws BizException 路径无效时抛出（400）
     */
    private void validateRepoPath(String repoPath) {
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
