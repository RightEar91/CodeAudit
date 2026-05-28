package com.codeaudit.service;

import com.codeaudit.dto.StatisticsDTO;
import com.codeaudit.entity.Review;
import com.codeaudit.repository.IssueRepository;
import com.codeaudit.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private IssueRepository issueRepository;

    @InjectMocks
    private StatisticsService statisticsService;

    @Test
    void shouldBuildIssueDensityTrend() {
        Review r1 = buildCompletedReview(LocalDateTime.now().minusDays(1), 5, 3);
        Review r2 = buildCompletedReview(LocalDateTime.now().minusDays(2), 0, 2);
        when(reviewRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Arrays.asList(r1, r2));
        mockEmptyIssueCounts();

        StatisticsDTO stats = statisticsService.getProjectStatistics(1L, null);

        assertEquals(2, stats.getIssueDensityTrend().size());
        assertEquals(5, stats.getIssueDensityTrend().get(0).getIssueCount());
        assertEquals(0, stats.getIssueDensityTrend().get(1).getIssueCount());
    }

    @Test
    void shouldFilterTrendByDays() {
        Review recent = buildCompletedReview(LocalDateTime.now().minusDays(1), 3, 1);
        Review old = buildCompletedReview(LocalDateTime.now().minusDays(10), 10, 5);
        when(reviewRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Arrays.asList(recent, old));
        mockEmptyIssueCounts();

        StatisticsDTO stats = statisticsService.getProjectStatistics(1L, 3);

        assertEquals(1, stats.getIssueDensityTrend().size());
        assertEquals(3, stats.getIssueDensityTrend().get(0).getIssueCount());
    }

    @Test
    void shouldExcludeNonCompletedReviewsFromTrend() {
        Review completed = buildCompletedReview(LocalDateTime.now(), 5, 2);
        Review processing = Review.builder().id(2L).status("processing")
                .createdAt(LocalDateTime.now()).totalIssues(10).totalFiles(5).build();
        when(reviewRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Arrays.asList(completed, processing));
        mockEmptyIssueCounts();

        StatisticsDTO stats = statisticsService.getProjectStatistics(1L, null);

        assertEquals(1, stats.getIssueDensityTrend().size());
    }

    @Test
    void shouldHandleEmptyReviews() {
        when(reviewRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());
        mockEmptyIssueCounts();

        StatisticsDTO stats = statisticsService.getProjectStatistics(1L, null);

        assertTrue(stats.getIssueDensityTrend().isEmpty());
    }

    @Test
    void shouldBuildFixRate() {
        when(reviewRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(issueRepository.countByProjectIdGroupByStatus(1L)).thenReturn(Arrays.asList(
                new Object[]{"resolved", 7},
                new Object[]{"open", 3},
                new Object[]{"ignored", 2}
        ));
        mockEmptyIssueCategoryAndAuthorCounts();

        StatisticsDTO stats = statisticsService.getProjectStatistics(1L, null);

        assertEquals(7, stats.getFixRate().getResolved());
        assertEquals(3, stats.getFixRate().getOpen());
        assertEquals(2, stats.getFixRate().getIgnored());
        assertEquals(70.0, stats.getFixRate().getRate(), 0.1);
    }

    @Test
    void shouldHandleZeroFixRate() {
        when(reviewRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());
        when(issueRepository.countByProjectIdGroupByStatus(1L)).thenReturn(Collections.emptyList());
        mockEmptyIssueCategoryAndAuthorCounts();

        StatisticsDTO stats = statisticsService.getProjectStatistics(1L, null);

        assertEquals(0, stats.getFixRate().getResolved());
        assertEquals(0.0, stats.getFixRate().getRate(), 0.1);
    }

    @Test
    void shouldBuildSeverityDistribution() {
        when(reviewRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());
        mockEmptyIssueCounts();
        when(issueRepository.countByProjectIdGroupBySeverity(1L)).thenReturn(Arrays.asList(
                new Object[]{"HIGH", 3},
                new Object[]{"MEDIUM", 5},
                new Object[]{"LOW", 2}
        ));
        when(issueRepository.countByProjectIdGroupByCategory(1L)).thenReturn(Collections.emptyList());
        when(issueRepository.countByProjectIdGroupByAuthor(1L)).thenReturn(Collections.emptyList());

        StatisticsDTO stats = statisticsService.getProjectStatistics(1L, null);

        assertEquals(3, stats.getSeverityDistribution().size());
        assertTrue(stats.getSeverityDistribution().stream().anyMatch(s -> s.getSeverity().equals("HIGH") && s.getCount() == 3));
    }

    @Test
    void shouldBuildTopAuthors() {
        when(reviewRepository.findByProjectIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());
        mockEmptyIssueCategoryAndAuthorCounts();
        when(issueRepository.countByProjectIdGroupByAuthor(1L)).thenReturn(Arrays.asList(
                new Object[]{"Alice", "alice@test.com", 12},
                new Object[]{"Bob", "bob@test.com", 5}
        ));

        StatisticsDTO stats = statisticsService.getProjectStatistics(1L, null);

        assertEquals(2, stats.getTopAuthors().size());
        assertEquals("Alice", stats.getTopAuthors().get(0).getAuthorName());
        assertEquals(12, stats.getTopAuthors().get(0).getIssueCount());
    }

    private Review buildCompletedReview(LocalDateTime time, int issues, int files) {
        return Review.builder().id(1L).status("completed")
                .createdAt(time).totalIssues(issues).totalFiles(files).build();
    }

    private void mockEmptyIssueCounts() {
        when(issueRepository.countByProjectIdGroupByStatus(anyLong())).thenReturn(Collections.emptyList());
        when(issueRepository.countByProjectIdGroupBySeverity(anyLong())).thenReturn(Collections.emptyList());
        when(issueRepository.countByProjectIdGroupByCategory(anyLong())).thenReturn(Collections.emptyList());
        when(issueRepository.countByProjectIdGroupByAuthor(anyLong())).thenReturn(Collections.emptyList());
    }

    private void mockEmptyIssueCategoryAndAuthorCounts() {
        when(issueRepository.countByProjectIdGroupBySeverity(anyLong())).thenReturn(Collections.emptyList());
        when(issueRepository.countByProjectIdGroupByCategory(anyLong())).thenReturn(Collections.emptyList());
        when(issueRepository.countByProjectIdGroupByAuthor(anyLong())).thenReturn(Collections.emptyList());
    }
}
