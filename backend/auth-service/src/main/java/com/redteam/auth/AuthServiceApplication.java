package com.redteam.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 用户权限服务启动类
 *
 * @author 红方团队
 */
@SpringBootApplication
@MapperScan("com.redteam.auth.mapper")
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.auth"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    用户权限服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8086/api/doc.html");
        System.out.println("==========================================");
    }
}
