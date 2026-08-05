package com.redteam.analyze.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * TAXII 2.1 Server 配置属性
 *
 * <p>对应配置前缀 {@code taxii}，封装启用状态、认证方式与凭证。</p>
 *
 * <p>支持的认证方式：</p>
 * <ul>
 *   <li>basic：HTTP Basic Auth（Authorization: Basic base64(user:pass)）</li>
 *   <li>apikey：API Key（X-API-Key 请求头）</li>
 *   <li>none：不校验</li>
 * </ul>
 *
 * @author 红方团队
 */
@Data
@Component
@ConfigurationProperties(prefix = "taxii")
public class TaxiiProperties {

    /**
     * 是否启用 TAXII Server
     */
    private boolean enabled = true;

    /**
     * 认证方式：basic / apikey / none
     */
    private String authType = "basic";

    /**
     * Basic Auth 用户名
     */
    private String username = "taxii";

    /**
     * Basic Auth 密码
     */
    private String password = "taxii123";

    /**
     * API Key（auth-type=apikey 时校验 X-API-Key 请求头）
     */
    private String apiKey = "";
}
