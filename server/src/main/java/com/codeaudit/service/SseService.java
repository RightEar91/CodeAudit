package com.codeaudit.service;

import com.codeaudit.event.ReviewProgressEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE 实时推送服务
 * <p>
 * 管理前端与后端之间的 Server-Sent Events 连接，
 * 替代原来的轮询模式，实时推送审查进度。
 * <p>
 * 用法：
 * <ol>
 *   <li>前端调用 GET /api/reviews/{id}/stream 建立 SSE 连接</li>
 *   <li>ReviewEngine 每完成一个文件后发布 ReviewProgressEvent</li>
 *   <li>本服务监听事件，推送给对应 reviewId 的 SseEmitter</li>
 *   <li>审查完成/失败时自动关闭连接</li>
 * </ol>
 * <p>
 * 心跳机制：每 15 秒发送一次心跳事件，防止云端 API 长时间无进度时
 * 浏览器 / 反向代理因超时断开 SSE 连接。
 *
 * @author CodeAudit Team
 */
@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);

    /** SSE 连接超时时间（30 分钟） */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    /** 心跳间隔（15 秒），低于大多数代理/浏览器的超时阈值 */
    private static final long HEARTBEAT_INTERVAL_SEC = 15;

    /** 按 reviewId 存储活跃的 SSE 连接 */
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 按 reviewId 存储心跳定时任务，用于在连接关闭时取消 */
    private final Map<Long, ScheduledFuture<?>> heartbeatFutures = new ConcurrentHashMap<>();

    /** 心跳调度器，单线程即可 */
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /**
     * 为指定 reviewId 创建 SSE 连接
     *
     * @param reviewId 审查 ID
     * @return SseEmitter 实例，由 Controller 返回给前端
     */
    public SseEmitter createEmitter(Long reviewId) {
        SseEmitter existing = emitters.remove(reviewId);
        if (existing != null) {
            cancelHeartbeat(reviewId);
            try { existing.complete(); } catch (Exception ignored) {}
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> cleanup(reviewId, emitter, "正常"));
        emitter.onTimeout(() -> cleanup(reviewId, emitter, "超时"));
        emitter.onError(e -> cleanup(reviewId, emitter, "异常: " + e.getMessage()));

        emitters.put(reviewId, emitter);

        startHeartbeat(reviewId, emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"reviewId\":" + reviewId + ",\"message\":\"SSE 连接已建立\"}"));
        } catch (IOException e) {
            cleanup(reviewId, emitter, "初始消息发送失败");
        }

        log.info("SSE 连接已建立: reviewId={}", reviewId);
        return emitter;
    }

    /**
     * 清理资源：移除 emitter + 取消心跳
     */
    private void cleanup(Long reviewId, SseEmitter emitter, String reason) {
        SseEmitter current = emitters.remove(reviewId);
        if (current != null) {
            cancelHeartbeat(reviewId);
            log.debug("SSE 连接关闭({}): reviewId={}", reason, reviewId);
        }
    }

    /**
     * 启动心跳定时任务，定期发送心跳事件保持连接活跃
     * <p>
     * 云端 API 审查耗时较长时，两次进度事件之间可能有 30-60 秒的空白期，
     * 心跳可防止代理/浏览器因无数据传输而断开连接。
     * 使用 named event 而非 comment 行，确保代理不会跳过。
     */
    private void startHeartbeat(Long reviewId, SseEmitter emitter) {
        ScheduledFuture<?> future = heartbeatScheduler.scheduleAtFixedRate(() -> {
            SseEmitter current = emitters.get(reviewId);
            if (current != emitter) {
                return;
            }
            try {
                current.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(""));
            } catch (IOException e) {
                cleanup(reviewId, emitter, "心跳发送失败");
            }
        }, HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);

        heartbeatFutures.put(reviewId, future);
    }

    /**
     * 取消心跳定时任务
     */
    private void cancelHeartbeat(Long reviewId) {
        ScheduledFuture<?> future = heartbeatFutures.remove(reviewId);
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 监听审查进度事件，推送给对应的 SSE 客户端
     * <p>
     * 不使用 @Async：emitter.send() 本身很快，同步执行可保证事件有序推送，
     * 且避免 @Async 默认 SimpleAsyncTaskExecutor 无限创建线程的问题。
     */
    @EventListener
    public void onReviewProgress(ReviewProgressEvent event) {
        Long reviewId = event.getReviewId();
        SseEmitter emitter = emitters.get(reviewId);
        if (emitter == null) {
            return;
        }

        try {
            String json = buildProgressJson(event);
            emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .data(json));
            log.debug("SSE 推送进度: reviewId={}, {}/{}", reviewId,
                    event.getReviewedFiles(), event.getTotalFiles());

            if ("completed".equals(event.getType()) || "failed".equals(event.getType())) {
                emitter.complete();
                cleanup(reviewId, emitter, "审查结束");
                log.info("SSE 连接已关闭(审查结束): reviewId={}", reviewId);
            }
        } catch (IOException e) {
            cleanup(reviewId, emitter, "消息发送失败");
            log.warn("SSE 消息发送失败: reviewId={}, error={}", reviewId, e.getMessage());
        }
    }

    /**
     * 构建进度事件的 JSON 字符串
     */
    private String buildProgressJson(ReviewProgressEvent event) {
        return String.format(
                "{\"reviewId\":%d,\"reviewedFiles\":%d,\"totalFiles\":%d," +
                "\"currentFile\":\"%s\",\"status\":\"%s\"," +
                "\"highCount\":%d,\"mediumCount\":%d,\"lowCount\":%d," +
                "\"durationMs\":%d,\"errorMessage\":\"%s\"}",
                event.getReviewId(),
                event.getReviewedFiles(),
                event.getTotalFiles(),
                escapeJson(event.getCurrentFile() != null ? event.getCurrentFile() : ""),
                event.getStatus() != null ? event.getStatus() : "",
                event.getHighCount(),
                event.getMediumCount(),
                event.getLowCount(),
                event.getDurationMs(),
                escapeJson(event.getErrorMessage() != null ? event.getErrorMessage() : "")
        );
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 移除指定 reviewId 的 SSE 连接
     */
    public void removeEmitter(Long reviewId) {
        cleanup(reviewId, null, "外部移除");
    }
}
