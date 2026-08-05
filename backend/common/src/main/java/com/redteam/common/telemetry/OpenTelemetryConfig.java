package com.redteam.common.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * OpenTelemetry SDK 配置 (v5.4)
 *
 * <p>装配 OTel SDK：构建 {@link SdkTracerProvider}，挂载 {@link BatchSpanProcessor} +
 * {@link OtlpHttpSpanExporter}，将 Span 数据经 OTLP/HTTP 上报至 Collector/Jaeger；
 * 并注册为 {@link GlobalOpenTelemetry}，供非 Spring 管理的组件（如 Kafka 拦截器）使用。</p>
 *
 * <p>跨服务 Trace 传播采用 W3C TraceContext（{@code traceparent}/{@code tracestate} 头），
 * 由 {@link TraceContextPropagator} 负责在 HTTP/Kafka 边界注入与提取。</p>
 *
 * <p>配置项：</p>
 * <ul>
 *   <li>{@code redteam.telemetry.otlp.endpoint}：OTLP/HTTP 端点，默认 {@code http://localhost:4317}</li>
 *   <li>{@code redteam.telemetry.service-name}：服务名，默认取 {@code spring.application.name}</li>
 *   <li>{@code redteam.telemetry.otlp.enabled}：是否启用 OTLP 上报，默认 {@code true}</li>
 * </ul>
 *
 * <p>容错：当未配置 endpoint 或上报不可用时，Tracer 仍以 no-op 方式可用，不影响业务；
 * BatchSpanProcessor 异步上报，失败仅记录日志。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "redteam.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenTelemetryConfig {

    /** OTLP/HTTP Trace 路径后缀 */
    private static final String OTLP_TRACES_PATH = "/v1/traces";

    /** 默认 OTLP 端点（与平台约定一致，可通过配置覆盖为 Collector HTTP 端口 4318） */
    private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";

    /** 默认服务名兜底 */
    private static final String DEFAULT_SERVICE_NAME = "redteam-service";

    /** Tracer 仪表化 scope 名称 */
    private static final String INSTRUMENTATION_SCOPE = "com.redteam";

    /** Tracer 仪表化版本 */
    private static final String INSTRUMENTATION_VERSION = "5.4";

    @Value("${redteam.telemetry.otlp.endpoint:" + DEFAULT_OTLP_ENDPOINT + "}")
    private String otlpEndpoint;

    @Value("${redteam.telemetry.service-name:${spring.application.name:" + DEFAULT_SERVICE_NAME + "}}")
    private String serviceName;

    @Value("${redteam.telemetry.otlp.enabled:true}")
    private boolean otlpEnabled;

    /**
     * 装配 {@link OpenTelemetry} 实例并注册为全局
     *
     * <p>使用 {@code destroyMethod = "close"} 在容器销毁时刷新 BatchSpanProcessor 缓冲区，
     * 避免丢失末尾 Span。</p>
     *
     * @return OpenTelemetry 实例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(OpenTelemetry.class)
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"), serviceName)));

        SdkTracerProviderBuilder providerBuilder = SdkTracerProvider.builder()
                .setResource(resource);

        SpanExporter exporter = buildExporter();
        if (exporter != null) {
            providerBuilder.addSpanProcessor(BatchSpanProcessor.builder(exporter)
                    .setScheduleDelay(Duration.ofSeconds(5))
                    .build());
            log.info("OTel OTLP Span Exporter 已启用: endpoint={}, service={}", otlpEndpoint, serviceName);
        } else {
            log.warn("OTel OTLP Span Exporter 未启用，Tracer 以 no-op 方式运行（redteam.telemetry.otlp.enabled=false 或 endpoint 为空）");
        }

        SdkTracerProvider tracerProvider = providerBuilder.build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();

        // 注册全局，供 Kafka 拦截器等非 Spring 组件获取
        try {
            GlobalOpenTelemetry.set(sdk);
        } catch (IllegalStateException e) {
            // 全局已设置（如 Agent 已注入），复用既有全局实例
            log.debug("GlobalOpenTelemetry 已存在，复用既有实例: {}", e.getMessage());
            return GlobalOpenTelemetry.get();
        }
        return sdk;
    }

    /**
     * 装配 Tracer Bean，供各服务 {@code @Autowired Tracer} 使用
     *
     * @param openTelemetry OpenTelemetry 实例
     * @return Tracer 实例
     */
    @Bean
    @ConditionalOnMissingBean(Tracer.class)
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(INSTRUMENTATION_SCOPE, INSTRUMENTATION_VERSION);
    }

    /**
     * 构建 OTLP HTTP Span Exporter
     *
     * <p>端点规范化：若未包含 {@code /v1/traces} 路径则自动追加，兼容配置只填 host:port 的场景。</p>
     *
     * @return SpanExporter，禁用时返回 null
     */
    private SpanExporter buildExporter() {
        if (!otlpEnabled || otlpEndpoint == null || otlpEndpoint.isBlank()) {
            return null;
        }
        String endpoint = normalizeEndpoint(otlpEndpoint.trim());
        try {
            return OtlpHttpSpanExporter.builder()
                    .setEndpoint(endpoint)
                    .setTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (Throwable e) {
            log.error("构建 OtlpHttpSpanExporter 失败: endpoint={}", endpoint, e);
            return null;
        }
    }

    /**
     * 规范化 OTLP/HTTP 端点：确保包含 {@code /v1/traces} 路径
     *
     * @param endpoint 原始端点
     * @return 规范化后的端点
     */
    static String normalizeEndpoint(String endpoint) {
        if (endpoint.endsWith(OTLP_TRACES_PATH) || endpoint.contains(OTLP_TRACES_PATH + "?")) {
            return endpoint;
        }
        // 去除末尾斜杠后追加路径
        String trimmed = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return trimmed + OTLP_TRACES_PATH;
    }
}
