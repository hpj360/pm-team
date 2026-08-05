package com.redteam.common.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenTelemetryConfig 单元测试 (v5.4)
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>{@code normalizeEndpoint} 自动追加 {@code /v1/traces} 路径</li>
 *   <li>已包含 {@code /v1/traces} 时保持原样</li>
 *   <li>末尾斜杠被规范化处理</li>
 *   <li>带查询参数的端点不被重复追加</li>
 *   <li>OTLP 禁用时 {@code openTelemetry()} 仍返回可用实例（no-op Tracer）</li>
 *   <li>{@code tracer()} Bean 返回非 null Tracer</li>
 * </ol>
 *
 * <p>注：{@code openTelemetry()} 会注册 {@link io.opentelemetry.api.GlobalOpenTelemetry}，
 * 全局单例只能设置一次；后续调用会被 catch 并复用既有实例，测试可重复执行。</p>
 *
 * @author 红方团队
 */
class OpenTelemetryConfigTest {

    @Test
    @DisplayName("testNormalizeEndpoint_AppendPath: 未含 /v1/traces 时自动追加")
    void testNormalizeEndpoint_AppendPath() {
        assertEquals("http://localhost:4318/v1/traces",
                OpenTelemetryConfig.normalizeEndpoint("http://localhost:4318"),
                "应自动追加 /v1/traces");
        assertEquals("http://collector:4317/v1/traces",
                OpenTelemetryConfig.normalizeEndpoint("http://collector:4317"),
                "应自动追加 /v1/traces");
    }

    @Test
    @DisplayName("testNormalizeEndpoint_AlreadyHasPath: 已含 /v1/traces 时保持原样")
    void testNormalizeEndpoint_AlreadyHasPath() {
        String endpoint = "http://localhost:4318/v1/traces";
        assertEquals(endpoint, OpenTelemetryConfig.normalizeEndpoint(endpoint),
                "已含 /v1/traces 时应保持原样");
    }

    @Test
    @DisplayName("testNormalizeEndpoint_TrailingSlash: 末尾斜杠被规范化")
    void testNormalizeEndpoint_TrailingSlash() {
        assertEquals("http://localhost:4318/v1/traces",
                OpenTelemetryConfig.normalizeEndpoint("http://localhost:4318/"),
                "末尾斜杠应被去除后追加路径");
    }

    @Test
    @DisplayName("testNormalizeEndpoint_WithQuery: 带查询参数且含 /v1/traces 不重复追加")
    void testNormalizeEndpoint_WithQuery() {
        String endpoint = "http://localhost:4318/v1/traces?timeout=10s";
        assertEquals(endpoint, OpenTelemetryConfig.normalizeEndpoint(endpoint),
                "带查询参数且含 /v1/traces 时应保持原样");
    }

    @Test
    @DisplayName("testOpenTelemetry_OtlpDisabled: OTLP 禁用时仍返回可用 OpenTelemetry 实例")
    void testOpenTelemetry_OtlpDisabled() {
        OpenTelemetryConfig config = new OpenTelemetryConfig();
        ReflectionTestUtils.setField(config, "otlpEndpoint", "http://localhost:4318");
        ReflectionTestUtils.setField(config, "serviceName", "test-service");
        ReflectionTestUtils.setField(config, "otlpEnabled", false);

        OpenTelemetry otel = assertDoesNotThrow(config::openTelemetry);
        assertNotNull(otel, "OTLP 禁用时 OpenTelemetry 实例仍不应为 null");
        assertNotNull(otel.getPropagators(), "Propagators 不应为 null");
    }

    @Test
    @DisplayName("testOpenTelemetry_OtlpEnabled: OTLP 启用时返回可用 OpenTelemetry 实例")
    void testOpenTelemetry_OtlpEnabled() {
        OpenTelemetryConfig config = new OpenTelemetryConfig();
        // 使用一个不可达端点，构建 exporter 不应抛异常（实际发送在异步线程，测试不触发）
        ReflectionTestUtils.setField(config, "otlpEndpoint", "http://localhost:4318");
        ReflectionTestUtils.setField(config, "serviceName", "test-service");
        ReflectionTestUtils.setField(config, "otlpEnabled", true);

        OpenTelemetry otel = assertDoesNotThrow(config::openTelemetry);
        assertNotNull(otel, "OTLP 启用时 OpenTelemetry 实例不应为 null");
        // Tracer 可用（no-op 或真实）
        Tracer tracer = otel.getTracer("com.redteam.test");
        assertNotNull(tracer, "Tracer 不应为 null");
        // 验证可创建 Span 并结束（不导出，因端点不可达，仅验证 API 可用）
        assertDoesNotThrow(() -> tracer.spanBuilder("test-span").startSpan().end());
    }

    @Test
    @DisplayName("testTracer_Bean: tracer() 返回非 null Tracer")
    void testTracer_Bean() {
        OpenTelemetryConfig config = new OpenTelemetryConfig();
        ReflectionTestUtils.setField(config, "otlpEndpoint", "http://localhost:4318");
        ReflectionTestUtils.setField(config, "serviceName", "test-service");
        ReflectionTestUtils.setField(config, "otlpEnabled", false);
        OpenTelemetry otel = config.openTelemetry();

        Tracer tracer = config.tracer(otel);
        assertNotNull(tracer, "tracer() Bean 不应为 null");
    }

    @Test
    @DisplayName("testOpenTelemetry_EmptyEndpoint: 空端点不抛异常，返回 no-op 实例")
    void testOpenTelemetry_EmptyEndpoint() {
        OpenTelemetryConfig config = new OpenTelemetryConfig();
        ReflectionTestUtils.setField(config, "otlpEndpoint", "");
        ReflectionTestUtils.setField(config, "serviceName", "test-service");
        ReflectionTestUtils.setField(config, "otlpEnabled", true);

        OpenTelemetry otel = assertDoesNotThrow(config::openTelemetry);
        assertNotNull(otel, "空端点时仍应返回可用 OpenTelemetry 实例");
    }
}
