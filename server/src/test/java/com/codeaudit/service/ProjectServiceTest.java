package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Project;
import com.codeaudit.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProjectService 单元测试
 *
 * @author CodeAudit Team
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    @TempDir
    Path tempDir;

    private String validRepoPath;

    @BeforeEach
    void setUp() throws Exception {
        // 创建一个模拟的 Git 仓库目录（包含 .git 子目录）
        Files.createDirectory(tempDir.resolve(".git"));
        validRepoPath = tempDir.toString();
    }

    @Test
    void create_shouldSucceed_whenRepoPathValid() {
        Project project = Project.builder()
                .name("TestProject")
                .repoPath(validRepoPath)
                .language("Java")
                .build();

        when(projectRepository.existsByRepoPath(validRepoPath)).thenReturn(false);
        when(projectRepository.save(project)).thenReturn(project);

        Project result = projectService.create(project);

        assertNotNull(result);
        assertEquals("TestProject", result.getName());
        verify(projectRepository).save(project);
    }

    @Test
    void create_shouldThrowBizException_whenRepoPathInvalid() {
        Project project = Project.builder()
                .name("BadProject")
                .repoPath("/nonexistent/path")
                .build();

        BizException ex = assertThrows(BizException.class, () -> projectService.create(project));
        assertTrue(ex.getMessage().contains("仓库路径不存在"));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void create_shouldThrowBizException_whenRepoPathDuplicate() {
        Project project = Project.builder()
                .name("DupProject")
                .repoPath(validRepoPath)
                .build();

        when(projectRepository.existsByRepoPath(validRepoPath)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> projectService.create(project));
        assertTrue(ex.getMessage().contains("已添加"));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void update_shouldSucceed_whenRepoPathChangedToUnique() throws Exception {
        Path otherTempDir = Files.createTempDirectory("other-repo");
        Files.createDirectory(otherTempDir.resolve(".git"));

        Project existing = Project.builder()
                .id(1L)
                .name("OldName")
                .repoPath(validRepoPath)
                .build();

        Project updated = Project.builder()
                .repoPath(otherTempDir.toString())
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectRepository.existsByRepoPath(otherTempDir.toString())).thenReturn(false);
        when(projectRepository.save(any())).thenReturn(existing);

        Project result = projectService.update(1L, updated);

        assertNotNull(result);
        verify(projectRepository).save(existing);
    }

    @Test
    void update_shouldThrowBizException_whenNewRepoPathDuplicate() throws Exception {
        Path otherTempDir = Files.createTempDirectory("other-repo");
        Files.createDirectory(otherTempDir.resolve(".git"));

        Project existing = Project.builder()
                .id(1L)
                .name("OldName")
                .repoPath(validRepoPath)
                .build();

        Project updated = Project.builder()
                .repoPath(otherTempDir.toString())
                .build();

        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(projectRepository.existsByRepoPath(otherTempDir.toString())).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> projectService.update(1L, updated));
        assertTrue(ex.getMessage().contains("已被其他项目使用"));
    }

    @Test
    void delete_shouldThrowBizException_whenProjectNotFound() {
        when(projectRepository.existsById(99L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> projectService.delete(99L));
        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("项目不存在"));
    }
}
