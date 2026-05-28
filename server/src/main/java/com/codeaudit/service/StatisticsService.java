package com.codeaudit.service;

import com.codeaudit.dto.StatisticsDTO;
import com.codeaudit.entity.Review;
import com.codeaudit.repository.IssueRepository;
import com.codeaudit.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Statistics service for trend reports: issue density, fix rate, severity/category distribution
 *
 * @author CodeAudit Team
 */
@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);

    private final ReviewRepository reviewRepository;
    private final IssueRepository issueRepository;

    public StatisticsService(ReviewRepository reviewRepository, IssueRepository issueRepository) {
        this.reviewRepository = reviewRepository;
        this.issueRepository = issueRepository;
    }

    /**
     * Build full statistics for a project
     */
    public StatisticsDTO getProjectStatistics(Long projectId, Integer days) {
        StatisticsDTO stats = new StatisticsDTO();

        List<Review> reviews = reviewRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        if (days != null && days > 0) {
            LocalDate cutoff = LocalDate.now().minusDays(days);
            reviews = reviews.stream()
                    .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().toLocalDate().isAfter(cutoff))
                    .toList();
        }

        stats.setIssueDensityTrend(buildTrend(reviews));
        stats.setFixRate(buildFixRate(projectId));
        stats.setSeverityDistribution(buildSeverityDist(projectId));
        stats.setCategoryDistribution(buildCategoryDist(projectId));
        stats.setTopAuthors(buildAuthorRanking(projectId));

        return stats;
    }

    /**
     * Issue density trend: issues/reviews/files per date
     */
    private List<StatisticsDTO.TrendPoint> buildTrend(List<Review> reviews) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<StatisticsDTO.TrendPoint> points = new ArrayList<>();

        for (Review r : reviews) {
            if (!"completed".equals(r.getStatus())) continue;
            String date = r.getCreatedAt() != null ? r.getCreatedAt().format(fmt) : "-";
            points.add(new StatisticsDTO.TrendPoint(
                    date,
                    r.getTotalIssues() != null ? r.getTotalIssues() : 0,
                    1,
                    r.getTotalFiles() != null ? r.getTotalFiles() : 0
            ));
        }

        return points;
    }

    /**
     * Fix rate: resolved / (resolved + open)
     */
    private StatisticsDTO.FixRate buildFixRate(Long projectId) {
        List<Object[]> statusRows = issueRepository.countByProjectIdGroupByStatus(projectId);
        int resolved = 0, open = 0, ignored = 0;
        for (Object[] row : statusRows) {
            String st = (String) row[0];
            int cnt = ((Number) row[1]).intValue();
            switch (st) {
                case "resolved" -> resolved = cnt;
                case "open" -> open = cnt;
                case "ignored" -> ignored = cnt;
            }
        }
        int total = resolved + open;
        double rate = total > 0 ? (double) resolved / total * 100 : 0;
        return new StatisticsDTO.FixRate(resolved, open, ignored, Math.round(rate * 10.0) / 10.0);
    }

    /**
     * Severity distribution
     */
    private List<StatisticsDTO.SeverityItem> buildSeverityDist(Long projectId) {
        List<Object[]> rows = issueRepository.countByProjectIdGroupBySeverity(projectId);
        List<StatisticsDTO.SeverityItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new StatisticsDTO.SeverityItem((String) row[0], ((Number) row[1]).intValue()));
        }
        return items;
    }

    /**
     * Category distribution
     */
    private List<StatisticsDTO.CategoryItem> buildCategoryDist(Long projectId) {
        List<Object[]> rows = issueRepository.countByProjectIdGroupByCategory(projectId);
        List<StatisticsDTO.CategoryItem> items = new ArrayList<>();
        for (Object[] row : rows) {
            items.add(new StatisticsDTO.CategoryItem((String) row[0], ((Number) row[1]).intValue()));
        }
        return items;
    }

    /**
     * Top authors by issue count
     */
    private List<StatisticsDTO.AuthorStat> buildAuthorRanking(Long projectId) {
        List<Object[]> rows = issueRepository.countByProjectIdGroupByAuthor(projectId);
        List<StatisticsDTO.AuthorStat> top = new ArrayList<>();
        int limit = Math.min(rows.size(), 10);
        for (int i = 0; i < limit; i++) {
            Object[] row = rows.get(i);
            String name = (String) row[0];
            String email = (String) row[1];
            int count = ((Number) row[2]).intValue();
            top.add(new StatisticsDTO.AuthorStat(name, email, count));
        }
        return top;
    }
}
