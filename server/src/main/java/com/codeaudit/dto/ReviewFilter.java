package com.codeaudit.dto;

import java.util.List;

/**
 * Review filter DTO
 * <p>
 * All fields are optional. If not provided, the filter is not applied.
 *
 * @author CodeAudit Team
 */
public class ReviewFilter {

    /** Include paths matching Ant-style patterns, e.g. ["src/main/java"] */
    private List<String> includePaths;

    /** Exclude paths matching Ant-style patterns, e.g. ["test", "generated"] */
    private List<String> excludePaths;

    /** Include only files with these extensions, e.g. [".java", ".kt"] */
    private List<String> includeExtensions;

    /** Filter commits by author name or email (fuzzy match) */
    private String authorEmail;

    /** Filter commits after this ISO datetime, e.g. "2025-01-01T00:00:00" */
    private String since;

    /** Filter commits before this ISO datetime, e.g. "2025-01-31T23:59:59" */
    private String until;

    public List<String> getIncludePaths() { return includePaths; }
    public void setIncludePaths(List<String> includePaths) { this.includePaths = includePaths; }

    public List<String> getExcludePaths() { return excludePaths; }
    public void setExcludePaths(List<String> excludePaths) { this.excludePaths = excludePaths; }

    public List<String> getIncludeExtensions() { return includeExtensions; }
    public void setIncludeExtensions(List<String> includeExtensions) { this.includeExtensions = includeExtensions; }

    public String getAuthorEmail() { return authorEmail; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }

    public String getSince() { return since; }
    public void setSince(String since) { this.since = since; }

    public String getUntil() { return until; }
    public void setUntil(String until) { this.until = until; }

    /** Returns true if any filter condition is set */
    public boolean hasAnyFilter() {
        return (includePaths != null && !includePaths.isEmpty())
                || (excludePaths != null && !excludePaths.isEmpty())
                || (includeExtensions != null && !includeExtensions.isEmpty())
                || (authorEmail != null && !authorEmail.isBlank())
                || (since != null && !since.isBlank())
                || (until != null && !until.isBlank());
    }
}
