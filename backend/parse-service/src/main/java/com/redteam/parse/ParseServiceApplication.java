package com.redteam.parse;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 文件解析服务启动类
 *
 * @author 红方团队
 */
@SpringBootApplication
@EnableKafka
@MapperScan("com.redteam.parse.mapper")
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.parse"})
public class ParseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParseServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    文件解析服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8082/api/doc.html");
        System.out.println("==========================================");
    }
}
