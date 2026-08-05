package com.redteam.common.telemetry;

/**
 * 统一日志标准字段名常量 (v5.4)
 *
 * <p>定义 JSON 结构化日志输出的标准字段名，供 {@code logback-spring.xml} 中的
 * {@code LogstashEncoder} 与业务侧 MDC 写入共同使用，保证日志采集端（Loki/ELK）
 * 字段一致，便于检索与关联 Trace。</p>
 *
 * <p>标准字段约定：</p>
 * <ul>
 *   <li>{@link #TIMESTAMP}：事件时间戳（ISO-8601，UTC）</li>
 *   <li>{@link #LEVEL}：日志级别（INFO/WARN/ERROR 等）</li>
 *   <li>{@link #TRACE_ID}：分布式 Trace ID，关联 OTel/Jaeger</li>
 *   <li>{@link #SPAN_ID}：Span ID，定位具体调用片段</li>
 *   <li>{@link #SERVICE}：服务名（spring.application.name）</li>
 *   <li>{@link #MESSAGE}：日志正文</li>
 *   <li>{@link #LOGGER}：日志器名称（通常为类全名）</li>
 *   <li>{@link #THREAD}：线程名</li>
 *   <li>{@link #FIELDS}：业务扩展字段集合（MDC 中除标准字段外的内容）</li>
 * </ul>
 *
 * @author 红方团队
 */
public final class LogFieldConstants {

    private LogFieldConstants() {
    }

    /** 时间戳字段名 */
    public static final String TIMESTAMP = "@timestamp";

    /** 日志级别字段名 */
    public static final String LEVEL = "level";

    /** Trace ID 字段名（与 W3C TraceContext / OTel 约定一致） */
    public static final String TRACE_ID = "traceId";

    /** Span ID 字段名 */
    public static final String SPAN_ID = "spanId";

    /** 服务名字段名 */
    public static final String SERVICE = "service";

    /** 日志正文字段名 */
    public static final String MESSAGE = "msg";

    /** 日志器名称字段名 */
    public static final String LOGGER = "logger";

    /** 线程名字段名 */
    public static final String THREAD = "thread";

    /** 业务扩展字段集合名 */
    public static final String FIELDS = "fields";

    /** MDC 中存放 traceId 的 key（与 {@link #TRACE_ID} 一致） */
    public static final String MDC_TRACE_ID = TRACE_ID;

    /** MDC 中存放 spanId 的 key */
    public static final String MDC_SPAN_ID = SPAN_ID;

    /** MDC 中存放服务名的 key */
    public static final String MDC_SERVICE = SERVICE;
}
