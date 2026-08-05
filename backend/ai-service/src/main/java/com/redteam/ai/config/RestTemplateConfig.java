package com.redteam.ai.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 配置
 *
 * <p>为 LLM 客户端提供带超时配置的 RestTemplate Bean。</p>
 *
 * @author 红方团队
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 配置 RestTemplate
     *
     * <p>连接超时 10 秒，读取超时 60 秒（LLM 推理耗时较长）。</p>
     *
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}
