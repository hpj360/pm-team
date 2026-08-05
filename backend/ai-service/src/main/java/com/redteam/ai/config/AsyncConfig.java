package com.redteam.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 *
 * <p>为 V5.1 AI Agent 自主分析任务提供异步执行线程池。
 * Agent 任务为 IO 密集型（多次调用 LLM + 外部服务），使用较大线程数。</p>
 *
 * @author 红方团队
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Agent 任务异步执行器
     *
     * <p>核心线程数 4，最大线程数 16，队列容量 64，拒绝策略由调用方处理。</p>
     *
     * @return Agent 任务线程池
     */
    @Bean("agentTaskExecutor")
    public Executor agentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("agent-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
