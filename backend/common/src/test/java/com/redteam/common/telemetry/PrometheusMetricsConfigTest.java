package com.redteam.common.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PrometheusMetricsConfig 单元测试 (v5.4)
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>{@code commonTagsCustomizer} 为所有指标附加 service 公共标签</li>
 *   <li>{@code httpMetricsCustomizer} 注册百分位直方图与高基数标签过滤（不抛异常）</li>
 *   <li>JVM/处理器指标 Binder Bean 正确创建并可绑定到 Registry</li>
 *   <li>所有 Binder 绑定后 Registry 中出现 JVM 相关指标</li>
 * </ol>
 *
 * @author 红方团队
 */
class PrometheusMetricsConfigTest {

    private PrometheusMetricsConfig config;

    @BeforeEach
    void setUp() {
        config = new PrometheusMetricsConfig();
        ReflectionTestUtils.setField(config, "serviceName", "test-service");
    }

    @Test
    @DisplayName("testCommonTagsCustomizer: 为 Registry 附加 service 公共标签")
    void testCommonTagsCustomizer() {
        MeterRegistryCustomizer<MeterRegistry> customizer = config.commonTagsCustomizer();
        assertNotNull(customizer, "commonTagsCustomizer 不应为 null");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        customizer.customize(registry);
        registry.counter("test.metric").increment();

        assertEquals("test-service",
                registry.find("test.metric").counter().getId().getTag("service"),
                "所有指标应携带 service=test-service 公共标签");
    }

    @Test
    @DisplayName("testHttpMetricsCustomizer: 注册百分位直方图与高基数过滤不抛异常")
    void testHttpMetricsCustomizer() {
        MeterRegistryCustomizer<MeterRegistry> customizer = config.httpMetricsCustomizer();
        assertNotNull(customizer, "httpMetricsCustomizer 不应为 null");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        assertDoesNotThrow(() -> customizer.customize(registry), "应用 HTTP 指标定制器不应抛异常");

        // 注册一个 http.server.requests 指标，验证百分位直方图配置生效（不抛异常即可）
        assertDoesNotThrow(() -> registry.timer("http.server.requests", "uri", "/api/test")
                .record(java.time.Duration.ofMillis(100)));
    }

    @Test
    @DisplayName("testJvmMemoryMetrics_Bean: JvmMemoryMetrics 可创建并绑定")
    void testJvmMemoryMetrics_Bean() {
        JvmMemoryMetrics binder = config.jvmMemoryMetrics();
        assertNotNull(binder, "JvmMemoryMetrics Bean 不应为 null");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);
        assertTrue(registry.getMeters().stream()
                        .anyMatch(m -> m.getId().getName().startsWith("jvm.memory")),
                "绑定后 Registry 应包含 jvm.memory.* 指标");
    }

    @Test
    @DisplayName("testJvmGcMetrics_Bean: JvmGcMetrics 可创建并绑定")
    void testJvmGcMetrics_Bean() {
        JvmGcMetrics binder = config.jvmGcMetrics();
        assertNotNull(binder, "JvmGcMetrics Bean 不应为 null");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);
        // GC 指标名以 jvm.gc 开头
        assertTrue(registry.getMeters().stream()
                        .anyMatch(m -> m.getId().getName().startsWith("jvm.gc")),
                "绑定后 Registry 应包含 jvm.gc.* 指标");
    }

    @Test
    @DisplayName("testJvmThreadMetrics_Bean: JvmThreadMetrics 可创建并绑定")
    void testJvmThreadMetrics_Bean() {
        JvmThreadMetrics binder = config.jvmThreadMetrics();
        assertNotNull(binder, "JvmThreadMetrics Bean 不应为 null");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);
        assertTrue(registry.getMeters().stream()
                        .anyMatch(m -> m.getId().getName().startsWith("jvm.threads")),
                "绑定后 Registry 应包含 jvm.threads.* 指标");
    }

    @Test
    @DisplayName("testProcessorMetrics_Bean: ProcessorMetrics 可创建并绑定")
    void testProcessorMetrics_Bean() {
        ProcessorMetrics binder = config.processorMetrics();
        assertNotNull(binder, "ProcessorMetrics Bean 不应为 null");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        binder.bindTo(registry);
        assertTrue(registry.getMeters().stream()
                        .anyMatch(m -> m.getId().getName().startsWith("system.cpu")
                                || m.getId().getName().startsWith("process.cpu")),
                "绑定后 Registry 应包含 system.cpu.* 或 process.cpu.* 指标");
    }

    @Test
    @DisplayName("testAllBinders_AreMeterBinder: 所有 JVM/Processor Binder 实现 MeterBinder")
    void testAllBinders_AreMeterBinder() {
        assertInstanceOf(MeterBinder.class, config.jvmMemoryMetrics());
        assertInstanceOf(MeterBinder.class, config.jvmGcMetrics());
        assertInstanceOf(MeterBinder.class, config.jvmThreadMetrics());
        assertInstanceOf(MeterBinder.class, config.processorMetrics());
    }
}
