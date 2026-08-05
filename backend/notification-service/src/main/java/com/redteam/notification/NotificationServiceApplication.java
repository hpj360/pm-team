package com.redteam.notification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 通知告警服务启动类
 *
 * <p>提供站内信、邮件、IM 多通道通知能力，订阅 Kafka 事件自动产生告警。</p>
 *
 * @author 红方团队
 */
@SpringBootApplication
@EnableKafka
@MapperScan("com.redteam.notification.mapper")
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.notification"})
public class NotificationServiceApplication {

    /**
     * 服务启动入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    通知告警服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8091/api/doc.html");
        System.out.println("==========================================");
    }
}
