package com.codeaudit.dto;

import java.util.List;

/**
 * Statistics response DTO
 *
 * @author CodeAudit Team
 */
public class StatisticsDTO {

    /** Issue density trend over time: count of issues per review grouped by date */
    private List<TrendPoint> issueDensityTrend;

    /** Fix rate: percentage of resolved issues */
    private FixRate fixRate;

    /** Severity distribution: HIGH / MEDIUM / LOW counts */
    private List<SeverityItem> severityDistribution;

    /** Category distribution: SECURITY / PERFORMANCE / STYLE / BUG */
    private List<CategoryItem> categoryDistribution;

    /** Author issue count ranking */
    private List<AuthorStat> topAuthors;

    public static class TrendPoint {
        private String date;
        private int issueCount;
        private int reviewCount;
        private int fileCount;

        public TrendPoint() {}
        public TrendPoint(String date, int issueCount, int reviewCount, int fileCount) {
            this.date = date; this.issueCount = issueCount; this.reviewCount = reviewCount; this.fileCount = fileCount;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public int getIssueCount() { return issueCount; }
        public void setIssueCount(int issueCount) { this.issueCount = issueCount; }
        public int getReviewCount() { return reviewCount; }
        public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
        public int getFileCount() { return fileCount; }
        public void setFileCount(int fileCount) { this.fileCount = fileCount; }
    }

    public static class FixRate {
        private int resolved;
        private int open;
        private int ignored;
        private double rate;

        public FixRate() {}
        public FixRate(int resolved, int open, int ignored, double rate) {
            this.resolved = resolved; this.open = open; this.ignored = ignored; this.rate = rate;
        }

        public int getResolved() { return resolved; }
        public void setResolved(int resolved) { this.resolved = resolved; }
        public int getOpen() { return open; }
        public void setOpen(int open) { this.open = open; }
        public int getIgnored() { return ignored; }
        public void setIgnored(int ignored) { this.ignored = ignored; }
        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }
    }

    public static class SeverityItem {
        private String severity;
        private int count;

        public SeverityItem() {}
        public SeverityItem(String severity, int count) { this.severity = severity; this.count = count; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class CategoryItem {
        private String category;
        private int count;

        public CategoryItem() {}
        public CategoryItem(String category, int count) { this.category = category; this.count = count; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class AuthorStat {
        private String authorName;
        private String authorEmail;
        private int issueCount;

        public AuthorStat() {}
        public AuthorStat(String authorName, String authorEmail, int issueCount) {
            this.authorName = authorName; this.authorEmail = authorEmail; this.issueCount = issueCount;
        }

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }
        public String getAuthorEmail() { return authorEmail; }
        public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }
        public int getIssueCount() { return issueCount; }
        public void setIssueCount(int issueCount) { this.issueCount = issueCount; }
    }

    public List<TrendPoint> getIssueDensityTrend() { return issueDensityTrend; }
    public void setIssueDensityTrend(List<TrendPoint> issueDensityTrend) { this.issueDensityTrend = issueDensityTrend; }

    public FixRate getFixRate() { return fixRate; }
    public void setFixRate(FixRate fixRate) { this.fixRate = fixRate; }

    public List<SeverityItem> getSeverityDistribution() { return severityDistribution; }
    public void setSeverityDistribution(List<SeverityItem> severityDistribution) { this.severityDistribution = severityDistribution; }

    public List<CategoryItem> getCategoryDistribution() { return categoryDistribution; }
    public void setCategoryDistribution(List<CategoryItem> categoryDistribution) { this.categoryDistribution = categoryDistribution; }

    public List<AuthorStat> getTopAuthors() { return topAuthors; }
    public void setTopAuthors(List<AuthorStat> topAuthors) { this.topAuthors = topAuthors; }
}
