package com.redteam.common.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceContextPropagator 单元测试 (v5.4)
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>无活跃 Span 时 {@code getCurrentTraceId/getCurrentSpanId} 返回 null</li>
 *   <li>活跃 Span 时正确返回 traceId/spanId</li>
 *   <li>inject/extract Map 载体往返：注入 traceparent 头并可提取回 Context</li>
 *   <li>null 载体兜底（inject/extract 不抛异常）</li>
 *   <li>applyMdc/clearMdc 正确写入与清理 MDC</li>
 *   <li>{@link TraceContextPropagator#traceparentHeader()} 返回 W3C 头名</li>
 * </ol>
 *
 * @author 红方团队
 */
class TraceContextPropagatorTest {

    private OpenTelemetrySdk sdk;
    private Tracer tracer;
    private TraceContextPropagator propagator;

    @BeforeEach
    void setUp() {
        // 构造一个最小可用的 OpenTelemetrySdk，仅装配 W3C 传播器
        sdk = OpenTelemetrySdk.builder()
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
        tracer = sdk.getTracer("com.redteam.test", "5.4");
        propagator = new TraceContextPropagator();
        ReflectionTestUtils.setField(propagator, "openTelemetry", sdk);
        ReflectionTestUtils.setField(propagator, "serviceName", "test-service");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        sdk.close();
    }

    @Test
    @DisplayName("testGetCurrentTraceId_NoActiveSpan: 无活跃 Span 返回 null")
    void testGetCurrentTraceId_NoActiveSpan() {
        assertNull(propagator.getCurrentTraceId(), "无活跃 Span 时 traceId 应为 null");
        assertNull(propagator.getCurrentSpanId(), "无活跃 Span 时 spanId 应为 null");
    }

    @Test
    @DisplayName("testGetCurrentTraceId_WithSpan: 活跃 Span 时返回有效 ID")
    void testGetCurrentTraceId_WithSpan() {
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder("test-span").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            String traceId = propagator.getCurrentTraceId();
            String spanId = propagator.getCurrentSpanId();
            assertNotNull(traceId, "活跃 Span 时 traceId 不应为 null");
            assertNotNull(spanId, "活跃 Span 时 spanId 不应为 null");
            assertEquals(32, traceId.length(), "W3C traceId 应为 32 位 16 进制");
            assertEquals(16, spanId.length(), "W3C spanId 应为 16 位 16 进制");
            assertFalse(traceId.matches("0+"), "traceId 不应全 0");
            assertFalse(spanId.matches("0+"), "spanId 不应全 0");
        } finally {
            span.end();
        }
    }

    @Test
    @DisplayName("testInjectExtract_Map: inject 注入 traceparent 头，extract 提取回 Context")
    void testInjectExtract_Map() {
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder("round-trip").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            Map<String, String> carrier = new HashMap<>();
            propagator.inject(carrier);

            String traceparent = carrier.get(TraceContextPropagator.traceparentHeader());
            assertNotNull(traceparent, "注入后 carrier 应包含 traceparent 头");
            assertTrue(traceparent.startsWith("00-"), "W3C traceparent 应以 00- 开头");

            // 提取出的 Context 应使 current span 携带原 traceId
            Context extracted = propagator.extract(carrier);
            io.opentelemetry.api.trace.SpanContext extractedCtx =
                    io.opentelemetry.api.trace.Span.fromContext(extracted).getSpanContext();
            assertTrue(extractedCtx.isValid(), "提取出的 SpanContext 应有效");
            assertEquals(span.getSpanContext().getTraceId(), extractedCtx.getTraceId(),
                    "提取出的 traceId 应与原 Span 一致");
        } finally {
            span.end();
        }
    }

    @Test
    @DisplayName("testInjectExtract_NullCarrier: null 载体不抛异常")
    void testInjectExtract_NullCarrier() {
        assertDoesNotThrow(() -> propagator.inject(null), "inject(null) 不应抛异常");
        Context ctx = assertDoesNotThrow(() -> propagator.extract(null), "extract(null) 不应抛异常");
        assertNotNull(ctx, "extract(null) 应返回非 null Context");
    }

    @Test
    @DisplayName("testApplyAndClearMdc: applyMdc 写入 traceId/spanId，clearMdc 清理")
    void testApplyAndClearMdc() {
        io.opentelemetry.api.trace.Span span = tracer.spanBuilder("mdc-span").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            propagator.applyMdc();
            assertEquals(span.getSpanContext().getTraceId(), MDC.get(LogFieldConstants.MDC_TRACE_ID),
                    "MDC traceId 应与 Span traceId 一致");
            assertEquals(span.getSpanContext().getSpanId(), MDC.get(LogFieldConstants.MDC_SPAN_ID),
                    "MDC spanId 应与 Span spanId 一致");

            propagator.clearMdc();
            assertNull(MDC.get(LogFieldConstants.MDC_TRACE_ID), "clearMdc 后 traceId 应被清理");
            assertNull(MDC.get(LogFieldConstants.MDC_SPAN_ID), "clearMdc 后 spanId 应被清理");
        } finally {
            span.end();
        }
    }

    @Test
    @DisplayName("testApplyMdc_NoActiveSpan: 无活跃 Span 时 applyMdc 不写入")
    void testApplyMdc_NoActiveSpan() {
        propagator.applyMdc();
        assertNull(MDC.get(LogFieldConstants.MDC_TRACE_ID), "无活跃 Span 时不应写入 traceId");
        assertNull(MDC.get(LogFieldConstants.MDC_SPAN_ID), "无活跃 Span 时不应写入 spanId");
    }

    @Test
    @DisplayName("testTraceparentHeader: 返回 W3C 标准头名")
    void testTraceparentHeader() {
        assertEquals("traceparent", TraceContextPropagator.traceparentHeader(),
                "W3C TraceContext 头名应为 traceparent");
    }
}
