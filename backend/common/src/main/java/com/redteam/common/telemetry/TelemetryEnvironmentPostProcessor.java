package com.redteam.common.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 可观测性环境默认值后置处理器 (v5.4)
 *
 * <p>在 Spring Boot 环境准备阶段注入 actuator/Prometheus 端点的默认配置，使服务无需额外配置
 * 即可暴露 {@code /actuator/prometheus}。所有默认值以最低优先级（{@code addLast}）注入，
 * 服务自身的 application.yml、命令行参数、环境变量均可覆盖。</p>
 *
 * <p>注入的默认值（仅当未显式配置时生效）：</p>
 * <ul>
 *   <li>{@code management.endpoints.web.exposure.include} = {@code health,info,prometheus,metrics}</li>
 *   <li>{@code management.endpoint.prometheus.enabled} = {@code true}</li>
 *   <li>{@code management.metrics.export.prometheus.enabled} = {@code true}</li>
 *   <li>{@code management.metrics.distribution.percentiles-histogram.http.server.requests} = {@code true}</li>
 * </ul>
 *
 * <p>当 {@code redteam.telemetry.enabled=false} 时跳过注入，完全不改变服务行为。</p>
 *
 * <p>注册方式：通过 {@code META-INF/spring.factories} 以
 * {@code org.springframework.boot.env.EnvironmentPostProcessor} SPI 加载。</p>
 *
 * @author 红方团队
 */
public class TelemetryEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 默认属性源名称 */
    private static final String PROPERTY_SOURCE_NAME = "redteamTelemetryDefaults";

    /** 默认暴露的 actuator 端点 */
    private static final String DEFAULT_EXPOSURE = "health,info,prometheus,metrics";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 主开关关闭时不注入任何默认值
        String enabled = environment.getProperty("redteam.telemetry.enabled", "true");
        if (!"true".equalsIgnoreCase(enabled)) {
            return;
        }

        Map<String, Object> defaults = new LinkedHashMap<>();

        if (environment.getProperty("management.endpoints.web.exposure.include") == null) {
            defaults.put("management.endpoints.web.exposure.include", DEFAULT_EXPOSURE);
        }
        if (environment.getProperty("management.endpoint.prometheus.enabled") == null) {
            defaults.put("management.endpoint.prometheus.enabled", "true");
        }
        if (environment.getProperty("management.metrics.export.prometheus.enabled") == null) {
            defaults.put("management.metrics.export.prometheus.enabled", "true");
        }
        if (environment.getProperty("management.metrics.distribution.percentiles-histogram.http.server.requests") == null) {
            defaults.put("management.metrics.distribution.percentiles-histogram.http.server.requests", "true");
        }
        if (environment.getProperty("management.endpoints.web.base-path") == null) {
            defaults.put("management.endpoints.web.base-path", "/actuator");
        }

        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }
}
