package com.redteam.report;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 报告生成服务启动类
 *
 * <p>负责报告模板渲染、PDF/Word/HTML 多格式导出，监听 {@code task.completed} 事件自动生成任务总结报告。</p>
 *
 * <ul>
 *   <li>端口：8092</li>
 *   <li>数据库：PostgreSQL (redteam_report)</li>
 *   <li>缓存：Redis</li>
 *   <li>消息：Kafka (redteam.task.events)</li>
 *   <li>模板：Thymeleaf</li>
 *   <li>导出：iText (PDF) + Apache POI (Word)</li>
 * </ul>
 *
 * @author 红方团队
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
@EnableScheduling
@MapperScan("com.redteam.report.mapper")
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.report"})
public class ReportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    报告生成服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8092/api/doc.html");
        System.out.println("==========================================");
    }

    /**
     * 报告生成专用异步线程池
     *
     * <p>用于 {@code @Async("reportTaskExecutor")} 异步生成 PDF/Word/HTML 报告，
     * 避免阻塞主请求线程。拒绝策略采用 CallerRunsPolicy，防止任务丢失。</p>
     *
     * @return 线程池执行器
     */
    @Bean("reportTaskExecutor")
    public Executor reportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("report-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
