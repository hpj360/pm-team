package com.redteam.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 审计日志异步线程池配置
 *
 * <p>提供专用的守护线程池用于异步写入审计日志，避免阻塞业务主线程。
 * 审计日志写入失败不影响业务流程，因此使用守护线程在 JVM 退出时自动结束。</p>
 *
 * @author 红方团队
 */
@Configuration
public class AuditLogAsyncConfig {

    /**
     * 审计日志异步执行器 Bean 名称
     */
    public static final String AUDIT_LOG_EXECUTOR = "auditLogExecutor";

    /**
     * 审计日志专用线程池
     *
     * <p>使用固定大小为 2 的线程池，足够处理审计日志的写入并发；
     * 线程为守护线程，避免阻止 JVM 退出。</p>
     *
     * @return 执行器
     */
    @Bean(AUDIT_LOG_EXECUTOR)
    public Executor auditLogExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "audit-log-async-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newFixedThreadPool(2, factory);
    }
}
