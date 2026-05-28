package com.codeaudit.event;

/**
 * 审查进度事件
 * <p>
 * ReviewEngine 每完成一个文件的审查后发布此事件，
 * SseService 监听到事件后通过 SSE 推送给前端。
 *
 * @author CodeAudit Team
 */
public class ReviewProgressEvent {

    private final Long reviewId;

    /** 已完成审查的文件数 */
    private final int reviewedFiles;

    /** 待审查的文件总数 */
    private final int totalFiles;

    /** 当前刚审查完的文件路径 */
    private final String currentFile;

    /**
     * 事件类型：
     * <ul>
     *   <li>{@code progress}  — 审查进行中（每个文件完成后发送）</li>
     *   <li>{@code completed}  — 审查全部完成</li>
     *   <li>{@code failed}     — 审查失败</li>
     * </ul>
     */
    private final String type;

    /** 审查状态（completed/failed 时有效） */
    private final String status;

    /** 高危问题数（completed 时有效） */
    private final int highCount;

    /** 中危问题数（completed 时有效） */
    private final int mediumCount;

    /** 低危问题数（completed 时有效） */
    private final int lowCount;

    /** 耗时毫秒（completed 时有效） */
    private final long durationMs;

    /** 失败原因（failed 时有效） */
    private final String errorMessage;

    public ReviewProgressEvent(Long reviewId, int reviewedFiles, int totalFiles,
                               String currentFile, String type, String status,
                               int highCount, int mediumCount, int lowCount,
                               long durationMs, String errorMessage) {
        this.reviewId = reviewId;
        this.reviewedFiles = reviewedFiles;
        this.totalFiles = totalFiles;
        this.currentFile = currentFile;
        this.type = type;
        this.status = status;
        this.highCount = highCount;
        this.mediumCount = mediumCount;
        this.lowCount = lowCount;
        this.durationMs = durationMs;
        this.errorMessage = errorMessage;
    }

    public Long getReviewId() { return reviewId; }
    public int getReviewedFiles() { return reviewedFiles; }
    public int getTotalFiles() { return totalFiles; }
    public String getCurrentFile() { return currentFile; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public int getHighCount() { return highCount; }
    public int getMediumCount() { return mediumCount; }
    public int getLowCount() { return lowCount; }
    public long getDurationMs() { return durationMs; }
    public String getErrorMessage() { return errorMessage; }
}
