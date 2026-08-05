package com.redteam.common.telemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trace 上下文传播器 (v5.4)
 *
 * <p>负责跨进程/跨消息的 W3C TraceContext 传播，覆盖三类边界：</p>
 * <ol>
 *   <li><b>HTTP 入站</b>：Servlet Filter 提取 {@code traceparent} 头，设为当前 Context
 *       并写入 MDC（{@code traceId}/{@code spanId}），使业务日志自动关联 Trace；</li>
 *   <li><b>HTTP 出站</b>：{@link RestTemplateCustomizer} 向所有 RestTemplate 注入拦截器，
 *       将当前 Context 注入 {@code traceparent} 头，实现下游服务串联；</li>
 *   <li><b>Kafka</b>：由 {@link TraceKafkaProducerInterceptor}/{@link TraceKafkaConsumerInterceptor}
 *       在消息收发时注入/提取 traceId（仅当 classpath 存在 kafka-clients 时生效）。</li>
 * </ol>
 *
 * <p>同时提供工具方法 {@link #getCurrentTraceId()} 供业务侧获取当前 Trace ID。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "redteam.telemetry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TraceContextPropagator {

    /** MDC 中 traceId 的 key */
    private static final String MDC_TRACE_ID = LogFieldConstants.MDC_TRACE_ID;

    /** MDC 中 spanId 的 key */
    private static final String MDC_SPAN_ID = LogFieldConstants.MDC_SPAN_ID;

    /** W3C TraceContext 头名 */
    private static final String TRACEPARENT_HEADER = "traceparent";

    @Autowired
    private OpenTelemetry openTelemetry;

    @Value("${redteam.telemetry.service-name:${spring.application.name:redteam-service}}")
    private String serviceName;

    // ==================== 工具方法 ====================

    /**
     * 获取当前 Trace ID
     *
     * @return 16 进制 Trace ID 字符串，无活跃上下文时返回 {@code null}
     */
    public String getCurrentTraceId() {
        SpanContext ctx = Span.current().getSpanContext();
        return ctx.isValid() ? ctx.getTraceId() : null;
    }

    /**
     * 获取当前 Span ID
     *
     * @return 16 进制 Span ID 字符串，无活跃上下文时返回 {@code null}
     */
    public String getCurrentSpanId() {
        SpanContext ctx = Span.current().getSpanContext();
        return ctx.isValid() ? ctx.getSpanId() : null;
    }

    /**
     * 将当前 Trace Context 注入到 Map 载体（可用于任意 HTTP 客户端请求头）
     *
     * @param carrier 请求头载体
     */
    public void inject(Map<String, String> carrier) {
        if (carrier == null) {
            return;
        }
        textMapPropagator().inject(Context.current(), carrier, MAP_SETTER);
    }

    /**
     * 从 Map 载体提取 Trace Context
     *
     * @param carrier 请求头载体
     * @return 提取出的 Context，无有效上下文时返回当前 Context
     */
    public Context extract(Map<String, String> carrier) {
        if (carrier == null) {
            return Context.current();
        }
        return textMapPropagator().extract(Context.current(), carrier, MAP_GETTER);
    }

    /**
     * 把当前 traceId/spanId 写入 MDC（供业务代码主动调用，如异步任务入口）
     */
    public void applyMdc() {
        String traceId = getCurrentTraceId();
        if (traceId != null) {
            MDC.put(MDC_TRACE_ID, traceId);
        }
        String spanId = getCurrentSpanId();
        if (spanId != null) {
            MDC.put(MDC_SPAN_ID, spanId);
        }
    }

    /**
     * 清除 MDC 中的 traceId/spanId
     */
    public void clearMdc() {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
    }

    // ==================== HTTP 入站 Filter ====================

    /**
     * HTTP 入站 Trace 提取过滤器
     *
     * <p>从请求头提取 W3C TraceContext，设为当前 Context 并写入 MDC；
     * 请求结束后清理 MDC，避免线程池复用导致的 Trace 串号。</p>
     *
     * @return Servlet Filter
     */
    @Bean
    public org.springframework.web.filter.OncePerRequestFilter traceContextFilter() {
        return new org.springframework.web.filter.OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            jakarta.servlet.http.HttpServletResponse response,
                                            FilterChain filterChain) {
                Context extracted;
                try {
                    extracted = textMapPropagator().extract(Context.current(), request, REQUEST_GETTER);
                } catch (Throwable e) {
                    log.debug("提取 Trace 上下文失败，按无上下文处理: {}", e.getMessage());
                    extracted = Context.current();
                }
                try (Scope ignored = extracted.makeCurrent()) {
                    applyMdc();
                    filterChain.doFilter(request, response);
                } catch (Throwable e) {
                    log.error("Trace 过滤器执行异常", e);
                    throw new RuntimeException(e);
                } finally {
                    clearMdc();
                }
            }
        };
    }

    // ==================== HTTP 出站 RestTemplate 拦截器 ====================

    /**
     * RestTemplate 定制器：向所有 RestTemplate 注入 Trace 头
     *
     * @return RestTemplateCustomizer
     */
    @Bean
    public RestTemplateCustomizer traceRestTemplateCustomizer() {
        return restTemplate -> restTemplate.getInterceptors().add(0, traceInterceptor());
    }

    /**
     * 构造 HTTP 出站拦截器：将当前 Context 注入请求头
     *
     * @return ClientHttpRequestInterceptor
     */
    ClientHttpRequestInterceptor traceInterceptor() {
        return (request, body, execution) -> {
            try {
                HttpHeaders headers = request.getHeaders();
                textMapPropagator().inject(Context.current(), headers, HEADER_SETTER);
            } catch (Throwable e) {
                log.debug("注入 Trace 头失败: {}", e.getMessage());
            }
            return execution.execute(request, body);
        };
    }

    // ==================== TextMap Setter/Getter ====================

    private TextMapPropagator textMapPropagator() {
        return openTelemetry.getPropagators().getTextMapPropagator();
    }

    /** Map 载体注入器 */
    private static final TextMapSetter<Map<String, String>> MAP_SETTER =
            (carrier, key, value) -> {
                if (carrier != null && key != null && value != null) {
                    carrier.put(key, value);
                }
            };

    /** Map 载体提取器 */
    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier == null ? Collections.emptyList() : carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    /** HttpHeaders 注入器 */
    private static final TextMapSetter<HttpHeaders> HEADER_SETTER =
            (headers, key, value) -> {
                if (headers != null && key != null && value != null) {
                    headers.set(key, value);
                }
            };

    /** HttpServletRequest 提取器 */
    private static final TextMapGetter<HttpServletRequest> REQUEST_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest request) {
            if (request == null) {
                return Collections.emptyList();
            }
            Enumeration<String> names = request.getHeaderNames();
            if (names == null) {
                return Collections.emptyList();
            }
            java.util.List<String> list = new java.util.ArrayList<>();
            while (names.hasMoreElements()) {
                list.add(names.nextElement());
            }
            return list;
        }

        @Override
        public String get(HttpServletRequest request, String key) {
            return request == null ? null : request.getHeader(key);
        }
    };

    /**
     * 获取 W3C traceparent 头名（供测试与文档使用）
     *
     * @return 头名
     */
    public static String traceparentHeader() {
        return TRACEPARENT_HEADER;
    }
}
