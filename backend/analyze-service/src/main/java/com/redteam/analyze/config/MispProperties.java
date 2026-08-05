package com.redteam.analyze.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MISP（Malware Information Sharing Platform）集成配置属性
 *
 * <p>对应配置前缀 {@code misp}，封装 MISP 服务端地址、API Key、超时、
 * Webhook 签名密钥与定时同步/拉取的 Cron 表达式。</p>
 *
 * <p>当 {@link #enabled} 为 false 时，所有同步方法将静默返回，不抛异常，
 * 用于开发/测试环境或 MISP 不可用场景的优雅降级。</p>
 *
 * @author 红方团队
 */
@Data
@Component
@ConfigurationProperties(prefix = "misp")
public class MispProperties {

    /**
     * 是否启用 MISP 集成
     */
    private boolean enabled = false;

    /**
     * MISP 服务端地址（如 http://localhost:8081）
     */
    private String endpoint = "http://localhost:8081";

    /**
     * MISP API Key（用于 Authorization 请求头）
     */
    private String apikey = "";

    /**
     * HTTP 超时时间（毫秒）
     */
    private int timeout = 10000;

    /**
     * Webhook 签名校验密钥（HMAC-SHA256）
     *
     * <p>为空时表示不校验签名（仅用于内网测试环境）。</p>
     */
    private String webhookSecret = "";

    /**
     * IOC 推送至 MISP 的定时 Cron 表达式（默认每日 02:00）
     */
    private String syncCron = "0 0 2 * * ?";

    /**
     * 从 MISP 拉取事件的定时 Cron 表达式（默认每小时）
     */
    private String pullCron = "0 0 * * * ?";
}
