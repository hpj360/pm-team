package com.redteam.profile;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * 目标画像服务启动类
 *
 * @author 红方团队
 */
@SpringBootApplication
@EnableKafka
@MapperScan("com.redteam.profile.mapper")
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.profile"})
public class ProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProfileServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    目标画像服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8085/api/doc.html");
        System.out.println("==========================================");
    }
}
