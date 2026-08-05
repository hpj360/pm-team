package com.redteam.analyze.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cuckoo 沙箱动态分析配置属性
 *
 * <p>对应配置前缀 {@code redteam.analyze.cuckoo}，独立于 V2.5 的 {@link SandboxProperties}，
 * 用于 V5.2 动态分析模块（{@code com.redteam.analyze.dynamic}）。</p>
 *
 * <p>降级策略：{@link #enabled} 为 false 或调用异常时，
 * {@code CuckooClient} 返回降级状态，不阻塞主流程。</p>
 *
 * @author 红方团队
 */
@Data
@Component
@ConfigurationProperties(prefix = "redteam.analyze.cuckoo")
public class CuckooProperties {

    /**
     * 是否启用 Cuckoo 动态分析（false 时直接走降级）
     */
    private boolean enabled = true;

    /**
     * Cuckoo Sandbox REST API 基础地址
     */
    private String endpoint = "http://localhost:8090";

    /**
     * API Key（Cuckoo 认证，可空）
     */
    private String apikey = "";

    /**
     * 单次 HTTP 调用超时（毫秒）
     */
    private int timeout = 30000;

    /**
     * 任务状态轮询间隔（毫秒）
     */
    private long pollInterval = 5000L;

    /**
     * 任务最大轮询次数（超过即视为超时降级）
     */
    private int maxPollCount = 60;
}
