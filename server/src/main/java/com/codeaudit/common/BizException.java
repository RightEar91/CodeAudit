package com.codeaudit.common;

import lombok.Getter;

/**
 * 业务异常 — 携带状态码和错误信息
 * <p>
 * Service 层抛出此异常后由 {@link com.codeaudit.config.GlobalExceptionHandler} 统一捕获
 * 并转换为 {@link Response} 响应，避免 Controller 层到处写 try-catch。
 * <p>
 * 使用示例：
 * <pre>{@code
 * throw new BizException(404, "项目不存在: " + id);
 * throw new BizException("该仓库路径已添加");
 * }</pre>
 *
 * @author CodeAudit Team
 */
@Getter
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码
     */
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this(400, message);
    }

}
