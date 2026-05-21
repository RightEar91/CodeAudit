package com.codeaudit.config;

import com.codeaudit.common.BizException;
import com.codeaudit.common.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 统一拦截所有 Controller 抛出的异常，转换为标准的 {@link Response} 响应。
 * Controller 层无需再写 try-catch，专注业务编排即可。
 * 所有 handler 统一返回裸 {@link Response} + {@link ResponseStatus} 决定 HTTP 状态码。
 * <p>
 * 异常映射关系：
 * <ul>
 *   <li>{@link BizException}          → 取其内置 code（映射 HTTP 状态码）+ message</li>
 *   <li>{@link IllegalArgumentException}  → 400 Bad Request</li>
 *   <li>{@link MethodArgumentNotValidException} → 400（参数校验失败）</li>
 *   <li>{@link Exception}                 → 500 Internal Server Error</li>
 * </ul>
 *
 * @author CodeAudit Team
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常 — 根据异常中的 code 决定 HTTP 状态码
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Response.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数非法异常（Service 层抛出的 IllegalArgumentException）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Response.fail(Response.CODE_BAD_REQUEST, e.getMessage());
    }

    /**
     * 请求体校验失败（@Valid 触发的 MethodArgumentNotValidException）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", msg);
        return Response.fail(Response.CODE_BAD_REQUEST, msg);
    }

    /**
     * 兜底异常 — 未知错误统一返回 500
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Response<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Response.fail(Response.CODE_INTERNAL_ERROR, "服务器内部错误: " + e.getMessage());
    }
}
