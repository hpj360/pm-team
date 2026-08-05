package com.redteam.common.telemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Kafka 生产者 Trace 注入拦截器 (v5.4)
 *
 * <p>在消息发送前将当前 W3C TraceContext 注入 Kafka 消息头（{@code traceparent}），
 * 实现跨服务、跨消息的 Trace 串联。Kafka 客户端实例化本类（非 Spring 管理），
 * 因此通过 {@link GlobalOpenTelemetry} 获取全局 OTel 实例。</p>
 *
 * <p>启用方式：在 Producer 配置中加入</p>
 * <pre>{@code
 * interceptor.classes=com.redteam.common.telemetry.TraceKafkaProducerInterceptor
 * }</pre>
 *
 * @param <K> 消息 key 类型
 * @param <V> 消息 value 类型
 * @author 红方团队
 */
public class TraceKafkaProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {

    /** Headers 载体注入器 */
    private static final TextMapSetter<Headers> HEADERS_SETTER =
            (headers, key, value) -> {
                if (headers != null && key != null && value != null) {
                    headers.remove(key).add(key, value.getBytes(StandardCharsets.UTF_8));
                }
            };

    private TextMapPropagator propagator;

    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (record == null) {
            return record;
        }
        try {
            TextMapPropagator p = getPropagator();
            if (p != null) {
                Headers headers = record.headers();
                p.inject(Context.current(), headers, HEADERS_SETTER);
            }
        } catch (Throwable e) {
            // 注入失败不影响消息发送
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // no-op
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
