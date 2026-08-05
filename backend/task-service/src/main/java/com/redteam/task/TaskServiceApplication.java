package com.redteam.task;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 任务管理服务启动类
 * <p>
 * 红方业务核心服务，负责任务的全生命周期管理，
 * 包括任务创建、状态流转、事件发布等。
 * </p>
 *
 * @author 红方团队
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@MapperScan("com.redteam.task.mapper")
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.task"})
public class TaskServiceApplication {

    /**
     * 服务启动入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(TaskServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    任务管理服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8090/doc.html");
        System.out.println("==========================================");
    }
}
