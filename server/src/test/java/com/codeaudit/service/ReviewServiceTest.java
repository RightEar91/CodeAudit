package com.codeaudit.service;

import com.codeaudit.common.BizException;
import com.codeaudit.entity.Project;
import com.codeaudit.entity.Review;
import com.codeaudit.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReviewService 单元测试
 *
 * @author CodeAudit Team
 */
@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private GitDiffService gitDiffService;

    @Mock
    private GitService gitService;

    @Mock
    private ReviewEngine reviewEngine;

    @Mock
    private DiffFilterService diffFilterService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void create_shouldSucceed_whenProjectExists() {
        Project project = Project.builder()
                .id(1L)
                .name("TestProject")
                .repoPath("/fake/repo")
                .language("Java")
                .build();

        Review review = Review.builder()
                .id(10L)
                .project(project)
                .title("Code Review")
                .fromRef("HEAD~1")
                .toRef("HEAD")
                .status("pending")
                .totalFiles(0)
                .build();

        when(projectService.findById(1L)).thenReturn(Optional.of(project));
        when(gitService.resolveRepoPath(project)).thenReturn("/fake/repo");
        when(reviewRepository.save(any())).thenReturn(review);
        when(gitService.getFilteredDiffBlocks(any(), any(), any(), any(), isNull()))
                .thenReturn(Collections.emptyList());
        when(diffFilterService.applyFilters(any(), isNull())).thenReturn(Collections.emptyList());

        Review result = reviewService.create(1L, "Code Review", null, null);

        assertNotNull(result);
        assertEquals("processing", result.getStatus());
        verify(reviewRepository, times(2)).save(any());
        verify(gitService).getFilteredDiffBlocks(eq("/fake/repo"), any(), any(), any(), isNull());
    }

    @Test
    void create_shouldThrowBizException_whenProjectNotFound() {
        when(projectService.findById(99L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> reviewService.create(99L, "Test", null, null));

        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("项目不存在"));
    }

    @Test
    void cancel_shouldSucceed_whenStatusIsPending() {
        Review review = Review.builder()
                .id(1L)
                .status("pending")
                .build();

        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        reviewService.cancel(1L);

        assertEquals("failed", review.getStatus());
        assertEquals("用户手动取消", review.getErrorMessage());
        verify(reviewRepository).save(review);
    }

    @Test
    void cancel_shouldSucceed_whenStatusIsProcessing() {
        Review review = Review.builder()
                .id(1L)
                .status("processing")
                .build();

        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        reviewService.cancel(1L);

        assertEquals("failed", review.getStatus());
        verify(reviewRepository).save(review);
    }

    @Test
    void cancel_shouldThrowBizException_whenStatusIsCompleted() {
        Review review = Review.builder()
                .id(1L)
                .status("completed")
                .build();

        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        BizException ex = assertThrows(BizException.class, () -> reviewService.cancel(1L));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("当前状态不可取消"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void cancel_shouldThrowBizException_whenReviewNotFound() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> reviewService.cancel(99L));

        assertEquals(404, ex.getCode());
        assertTrue(ex.getMessage().contains("审查不存在"));
    }

    @Test
    void delete_shouldDelegateToRepository() {
        reviewService.delete(1L);
        verify(reviewRepository).deleteById(1L);
    }

    @Test
    void findById_shouldReturnReview_whenExists() {
        Review review = Review.builder().id(1L).status("completed").build();
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        Optional<Review> result = reviewService.findById(1L);
        assertTrue(result.isPresent());
        assertEquals("completed", result.get().getStatus());
    }
}
