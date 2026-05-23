package com.codeaudit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * <p>
 * 启用 Spring 的 @Async 支持，并配置有界线程池用于 AI 审查任务。
 * 线程池参数：
 * <ul>
 *   <li>corePoolSize = 2  — 核心线程数，保证并发审查能力</li>
 *   <li>maxPoolSize = 5   — 最大线程数，防止资源耗尽</li>
 *   <li>queueCapacity = 100 — 任务队列上限，超限后由调用线程自己执行</li>
 * </ul>
 *
 * @author CodeAudit Team
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "reviewExecutor")
    public Executor reviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("review-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
