package com.redteam.upload;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 文件上传服务启动类
 *
 * @author 红方团队
 */
@SpringBootApplication
@EnableKafka
@MapperScan("com.redteam.upload.mapper")
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.upload"})
public class UploadServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    文件上传服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8081/api/doc.html");
        System.out.println("==========================================");
    }
}
