package com.redteam.search;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 检索服务启动类
 *
 * @author 红方团队
 */
@SpringBootApplication
@EnableKafka
@MapperScan("com.redteam.search.mapper")
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.search"})
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    检索服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8083/api/doc.html");
        System.out.println("==========================================");
    }
}
