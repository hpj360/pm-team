package com.redteam.common.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kafka 消费者 Trace 提取拦截器 (v5.4)
 *
 * <p>在消息拉取后、交付业务监听器前，从消息头提取 W3C TraceContext 并写入 MDC
 * （{@code traceId}/{@code spanId}），使消费侧日志能与生产侧 Trace 关联。</p>
 *
 * <p>注意：{@code ConsumerInterceptor.onConsume} 在 poll 线程执行，MDC 仅在该线程
 * 生效；若业务监听器运行于独立线程，需在监听入口调用
 * {@link TraceContextPropagator#applyMdc()} 再次同步（从已恢复的 Context）。</p>
 *
 * <p>启用方式：在 Consumer 配置中加入</p>
 * <pre>{@code
 * interceptor.classes=com.redteam.common.telemetry.TraceKafkaConsumerInterceptor
 * }</pre>
 *
 * @param <K> 消息 key 类型
 * @param <V> 消息 value 类型
 * @author 红方团队
 */
public class TraceKafkaConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

    /** MDC 中 traceId 的 key */
    private static final String MDC_TRACE_ID = LogFieldConstants.MDC_TRACE_ID;

    /** MDC 中 spanId 的 key */
    private static final String MDC_SPAN_ID = LogFieldConstants.MDC_SPAN_ID;

    /** Headers 载体提取器 */
    private static final TextMapGetter<Headers> HEADERS_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers headers) {
            if (headers == null) {
                return List.of();
            }
            List<String> keys = new ArrayList<>();
            for (Header header : headers.toArray()) {
                keys.add(header.key());
            }
            return keys;
        }

        @Override
        public String get(Headers headers, String key) {
            if (headers == null || key == null) {
                return null;
            }
            // Headers.headers(String) 返回 Iterable<Header>，取首个值
            Iterable<Header> hdrs = headers.headers(key);
            if (hdrs == null) {
                return null;
            }
            for (Header h : hdrs) {
                if (h != null) {
                    byte[] value = h.value();
                    return value == null ? null : new String(value, StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    };

    private TextMapPropagator propagator;

    @Override
    public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
        if (records == null || records.isEmpty()) {
            return records;
        }
        try {
            // 取首条消息的 trace 头作为当前批次的上下文（best-effort）
            for (org.apache.kafka.clients.consumer.ConsumerRecord<K, V> record : records) {
                TextMapPropagator p = getPropagator();
                if (p == null || record.headers() == null) {
                    break;
                }
                Context extracted = p.extract(Context.current(), record.headers(), HEADERS_GETTER);
                SpanContext sc = Span.fromContext(extracted).getSpanContext();
                if (sc.isValid()) {
                    MDC.put(MDC_TRACE_ID, sc.getTraceId());
                    MDC.put(MDC_SPAN_ID, sc.getSpanId());
                }
                break;
            }
        } catch (Throwable e) {
            // 提取失败不影响消费
        }
        return records;
    }

    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // 提交后清理 MDC
        try {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
        } catch (Throwable e) {
            // ignore
        }
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // no-op
    }

    private TextMapPropagator getPropagator() {
        if (propagator == null) {
            try {
                propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
            } catch (Throwable e) {
                propagator = null;
            }
        }
        return propagator;
    }
}
