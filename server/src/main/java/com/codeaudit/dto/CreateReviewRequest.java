package com.codeaudit.dto;

/**
 * 创建审查请求 DTO
 * <p>
 * 前端/API 调用方创建审查时传入的请求体映射。
 * filters 字段可选，传入后可按目录/文件类型/作者/时间段过滤审查范围。
 *
 * @author CodeAudit Team
 */
public class CreateReviewRequest {

    private String title;
    private String fromRef;
    private String toRef;

    /** 可选的过滤条件 */
    private ReviewFilter filters;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFromRef() { return fromRef; }
    public void setFromRef(String fromRef) { this.fromRef = fromRef; }

    public String getToRef() { return toRef; }
    public void setToRef(String toRef) { this.toRef = toRef; }

    public ReviewFilter getFilters() { return filters; }
    public void setFilters(ReviewFilter filters) { this.filters = filters; }
}
