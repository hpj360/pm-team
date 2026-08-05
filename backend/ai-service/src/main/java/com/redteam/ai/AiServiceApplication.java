package com.redteam.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * AI 服务启动类
 *
 * <p>封装 LLM 调用，支持 Ollama 本地模式与远程 OpenAI 兼容 API 模式。</p>
 *
 * <ul>
 *   <li>端口：8093</li>
 *   <li>默认 Provider：ollama（可通过 llm.provider 切换为 remote）</li>
 * </ul>
 *
 * <p>注：本项目采用 Istio Service Mesh，未使用 Spring Cloud Discovery，故不加 {@code @EnableDiscoveryClient}；
 * v4.1.1 起新增威胁摘要持久化能力，扫描 common 模块公共配置与 Mapper。</p>
 *
 * @author 红方团队
 */
@SpringBootApplication
@MapperScan(basePackages = {"com.redteam.common.mapper", "com.redteam.ai.mapper"})
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.ai"})
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    AI 服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8093/api/doc.html");
        System.out.println("==========================================");
    }
}
