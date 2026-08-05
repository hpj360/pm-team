package com.redteam.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestTemplate;

/**
 * 可观测性体系自动配置入口 (v5.4)
 *
 * <p>平台可观测性的统一装配入口，条件加载 OpenTelemetry（Trace）+ Prometheus（Metrics）
 * + 统一 JSON 日志 + 告警通知组件。当 {@code redteam.telemetry.enabled=true}（默认开启）时，
 * 以下组件自动生效：</p>
 *
 * <ul>
 *   <li>{@link OpenTelemetryConfig}：OTel SDK + OTLP Span Exporter + Tracer Bean</li>
 *   <li>{@link TraceContextPropagator}：HTTP/Kafka 跨服务 Trace 传播</li>
 *   <li>{@link PrometheusMetricsConfig}：Micrometer + Prometheus + JVM 指标</li>
 *   <li>{@link UnifiedLogConfig}：统一 JSON 日志 MDC 服务名字段</li>
 *   <li>{@link BusinessMetricsRecorder}：业务指标记录器</li>
 *   <li>{@link AlertNotifier}：飞书 Webhook 告警通知</li>
 * </ul>
 *
 * <p>设计说明：common 模块被所有服务以 {@code @ComponentScan("com.redteam.common")} 扫描，
 * 故各 {@code @Configuration} 通过组件扫描加载；本类作为聚合入口 {@link Import} 各子配置，
 * 并装配两个 Plain Old Bean（Recorder/Notifier）以便单元测试脱离 Spring 容器构造。</p>
 *
 * <p>关闭方式：{@code redteam.telemetry.enabled=false} 即可整体禁用可观测性装配。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "redteam.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        OpenTelemetryConfig.class,
        TraceContextPropagator.class,
        PrometheusMetricsConfig.class,
        UnifiedLogConfig.class
})
public class TelemetryAutoConfiguration {

    /**
     * 业务指标记录器
     *
     * <p>优先使用容器中的 {@link MeterRegistry}（Prometheus Registry）；若未装配则降级为
     * {@link SimpleMeterRegistry}，保证 Recorder 始终可用，不影响业务调用。</p>
     *
     * @param registryProvider MeterRegistry 提供者
     * @return BusinessMetricsRecorder
     */
    @Bean
    @ConditionalOnMissingBean(BusinessMetricsRecorder.class)
    public BusinessMetricsRecorder businessMetricsRecorder(ObjectProvider<MeterRegistry> registryProvider) {
        MeterRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            log.warn("未发现 MeterRegistry，BusinessMetricsRecorder 降级使用 SimpleMeterRegistry");
            registry = new SimpleMeterRegistry();
        }
        return new BusinessMetricsRecorder(registry);
    }

    /**
     * 告警通知器
     *
     * <p>内部创建专用 {@link RestTemplate} 调用飞书 Webhook，避免污染服务的 RestTemplate Bean 空间。</p>
     *
     * @param webhook 全局默认飞书 Webhook 地址
     * @param enabled 是否启用告警发送
     * @return AlertNotifier
     */
    @Bean
    @ConditionalOnMissingBean(AlertNotifier.class)
    public AlertNotifier alertNotifier(
            @Value("${redteam.telemetry.alert.feishu.webhook:}") String webhook,
            @Value("${redteam.telemetry.alert.enabled:true}") boolean enabled) {
        RestTemplate restTemplate = new RestTemplate();
        log.info("告警通知器已装配: enabled={}, webhookConfigured={}", enabled, webhook != null && !webhook.isBlank());
        return new AlertNotifier(restTemplate, webhook, enabled);
    }
}
