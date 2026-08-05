package com.redteam.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus 指标配置 (v5.4)
 *
 * <p>基于 Micrometer + Prometheus Registry 采集与暴露指标，端点为
 * {@code /actuator/prometheus}（端点暴露由 {@code application-telemetry.yml} 与
 * {@link TelemetryEnvironmentPostProcessor} 配置）。</p>
 *
 * <p>职责：</p>
 * <ul>
 *   <li>为所有指标附加公共标签（service），便于按服务聚合；</li>
 *   <li>注册 JVM/进程绑定指标（GC、内存、线程、CPU）；</li>
 *   <li>配置 HTTP 服务请求指标的百分位直方图，支持 P50/P95/P99 计算；</li>
 *   <li>限制高基数标签，防止指标爆炸。</li>
 * </ul>
 *
 * <p>业务指标（file_parse_total / ai_invoke_total / workflow_approval_duration / kafka_lag）
 * 由 {@link BusinessMetricsRecorder} 注册并记录。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "redteam.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PrometheusMetricsConfig {

    /** HTTP 服务请求指标名（Spring Boot 自动埋点） */
    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";

    @Value("${redteam.telemetry.service-name:${spring.application.name:redteam-service}}")
    private String serviceName;

    /**
     * 公共标签定制器：为所有指标附加 service 标签
     *
     * @return MeterRegistryCustomizer
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer() {
        return registry -> registry.config().commonTags("service", serviceName);
    }

    /**
     * HTTP 请求指标百分位直方图与高基数标签过滤
     *
     * <p>Micrometer 1.12 的 {@link MeterFilter} 未提供 {@code percentileHistogram(String, boolean)}
     * 静态工厂，故通过重写 {@link MeterFilter#configure} 为 {@code http.server.requests} 注入
     * {@link DistributionStatisticConfig}（开启百分位直方图 + P50/P95/P99）；
     * 同时限制 {@code uri} 标签基数不超过 100，防止高基数标签导致指标爆炸。</p>
     *
     * @return MeterRegistryCustomizer
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> httpMetricsCustomizer() {
        return registry -> registry.config()
                .meterFilter(new MeterFilter() {
                    @Override
                    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                        if (HTTP_SERVER_REQUESTS.equals(id.getName())) {
                            return DistributionStatisticConfig.builder()
                                    .percentilesHistogram(true)
                                    .percentiles(0.5, 0.95, 0.99)
                                    .build()
                                    .merge(config);
                        }
                        return config;
                    }
                })
                .meterFilter(MeterFilter.maximumAllowableTags(HTTP_SERVER_REQUESTS, "uri", 100,
                        MeterFilter.deny()));
    }

    /**
     * JVM 内存指标绑定
     *
     * @return JvmMemoryMetrics
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * JVM GC 指标绑定
     *
     * @return JvmGcMetrics
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     * JVM 线程指标绑定
     *
     * @return JvmThreadMetrics
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    /**
     * 处理器（CPU）指标绑定
     *
     * @return ProcessorMetrics
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }
}
