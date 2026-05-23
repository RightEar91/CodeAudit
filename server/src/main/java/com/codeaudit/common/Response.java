package com.codeaudit.common;

import lombok.Getter;

/**
 * 统一 REST API 响应包装类
 * <p>
 * 所有 Controller 返回此类型，前端可统一解析 {@code code / message / data} 三段式结构。
 * 对象由静态工厂方法创建，不可变。
 * <p>
 * 使用示例：
 * <pre>{@code
 * // 成功返回数据
 * return Response.ok(projectList);
 * // 成功无数据
 * return Response.ok();
 * // 业务失败
 * return Response.fail(400, "仓库路径不存在");
 * }</pre>
 *
 * @param <T> 响应数据类型
 * @author CodeAudit Team
 */
@Getter
public class Response<T> {

    /**
     * 状态码（200 成功，4xx 客户端错误，5xx 服务端错误）
     */
    private int code;

    /**
     * 提示信息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    private Response() {
    }

    private Response(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     */
    public static <T> Response<T> ok(T data) {
        return new Response<>(200, "success", data);
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Response<T> ok() {
        return new Response<>(200, "success", null);
    }

    /**
     * 创建成功响应（201 Created，用于 POST 创建资源）
     */
    public static <T> Response<T> created(T data) {
        return new Response<>(201, "success", data);
    }

    /**
     * 业务失败响应
     *
     * @param code    业务状态码
     * @param message 错误提示
     */
    public static <T> Response<T> fail(int code, String message) {
        return new Response<>(code, message, null);
    }

    /**
     * 业务失败响应（默认 400）
     */
    public static <T> Response<T> fail(String message) {
        return new Response<>(400, message, null);
    }

    // ==================== 常用状态码常量 ====================

    /**
     * 成功
     */
    public static final int CODE_OK = 200;
    /**
     * 已创建
     */
    public static final int CODE_CREATED = 201;
    /**
     * 请求参数错误
     */
    public static final int CODE_BAD_REQUEST = 400;
    /**
     * 未找到资源
     */
    public static final int CODE_NOT_FOUND = 404;
    /**
     * 服务端内部错误
     */
    public static final int CODE_INTERNAL_ERROR = 500;
}
