package com.redteam.analyze.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 沙箱服务配置属性
 *
 * <p>对应配置前缀 {@code redteam.analyze.sandbox}，封装 Cuckoo 沙箱 API 地址、超时、认证等参数。</p>
 *
 * @author 红方团队
 */
@Data
@Component
@ConfigurationProperties(prefix = "redteam.analyze.sandbox")
public class SandboxProperties {

    /**
     * 是否启用沙箱（false 时直接走降级）
     */
    private boolean enabled = true;

    /**
     * 沙箱 API 基础地址
     */
    private String apiUrl = "http://localhost:8090";

    /**
     * API Key（Cuckoo 认证）
     */
    private String apiKey = "";

    /**
     * 连接超时（毫秒）
     */
    private int connectTimeoutMs = 5000;

    /**
     * 读取超时（毫秒）
     */
    private int readTimeoutMs = 30000;

    /**
     * 任务轮询间隔（毫秒）
     */
    private long pollIntervalMs = 5000L;

    /**
     * 任务最大轮询次数
     */
    private int maxPollCount = 60;
}
