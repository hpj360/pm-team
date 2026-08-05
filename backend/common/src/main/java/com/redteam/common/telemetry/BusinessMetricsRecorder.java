package com.redteam.common.telemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务指标记录器 (v5.4)
 *
 * <p>封装 {@link MeterRegistry}，对外提供业务语义化的指标记录方法，屏蔽 Micrometer API 细节。
 * 各服务通过 {@code @Autowired BusinessMetricsRecorder} 注入使用。</p>
 *
 * <p>内置指标：</p>
 * <ul>
 *   <li>{@code file_parse_total}（Counter，标签 status/fileType）：文件解析次数</li>
 *   <li>{@code ai_invoke_total}（Counter，标签 service/result[success/fail/degraded]）：AI 调用次数</li>
 *   <li>{@code workflow_approval_duration}（Timer，标签 stage）：工作流审批耗时</li>
 *   <li>{@code kafka_lag}（Gauge，标签 topic）：Kafka 消费积压</li>
 * </ul>
 *
 * <p>所有记录方法均吞掉异常，确保指标采集失败不影响业务流程。</p>
 *
 * @author 红方团队
 */
@Slf4j
public class BusinessMetricsRecorder {

    /** 文件解析总次数指标名 */
    public static final String METRIC_FILE_PARSE_TOTAL = "file_parse_total";

    /** AI 调用总次数指标名 */
    public static final String METRIC_AI_INVOKE_TOTAL = "ai_invoke_total";

    /** 工作流审批耗时指标名 */
    public static final String METRIC_WORKFLOW_APPROVAL_DURATION = "workflow_approval_duration";

    /** Kafka 积压指标名 */
    public static final String METRIC_KAFKA_LAG = "kafka_lag";

    /** AI 调用结果：成功 */
    public static final String AI_RESULT_SUCCESS = "success";

    /** AI 调用结果：失败 */
    public static final String AI_RESULT_FAIL = "fail";

    /** AI 调用结果：降级 */
    public static final String AI_RESULT_DEGRADED = "degraded";

    private final MeterRegistry meterRegistry;

    /** Kafka 积压值持有器（topic -&gt; AtomicLong），用于 Gauge 引用 */
    private final ConcurrentMap<String, AtomicLong> kafkaLagHolders = new ConcurrentHashMap<>();

    public BusinessMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录文件解析次数
     *
     * @param status   解析状态（如 success/fail）
     * @param fileType 文件类型（如 pe/pdf/elf）
     */
    public void recordFileParse(String status, String fileType) {
        try {
            Counter counter = Counter.builder(METRIC_FILE_PARSE_TOTAL)
                    .tags(Tags.of("status", nullSafe(status), "fileType", nullSafe(fileType)))
                    .description("文件解析总次数")
                    .register(meterRegistry);
            counter.increment();
        } catch (Throwable e) {
            log.debug("记录 file_parse_total 失败: status={}, fileType={}", status, fileType, e);
        }
    }

    /**
     * 记录 AI 调用次数
     *
     * @param service AI 服务标识（如 ner/summary/threat）
     * @param result  调用结果（success/fail/degraded）
     */
    public void recordAiInvoke(String service, String result) {
        try {
            Counter counter = Counter.builder(METRIC_AI_INVOKE_TOTAL)
                    .tags(Tags.of("service", nullSafe(service), "result", nullSafe(result)))
                    .description("AI 服务调用总次数")
                    .register(meterRegistry);
            counter.increment();
        } catch (Throwable e) {
            log.debug("记录 ai_invoke_total 失败: service={}, result={}", service, result, e);
        }
    }

    /**
     * 记录工作流审批耗时
     *
     * @param durationMs 耗时（毫秒）
     */
    public void recordWorkflowApproval(long durationMs) {
        recordWorkflowApproval(durationMs, "overall");
    }

    /**
     * 记录工作流审批耗时（带阶段标签）
     *
     * @param durationMs 耗时（毫秒）
     * @param stage      阶段（如 submit/approve/overall）
     */
    public void recordWorkflowApproval(long durationMs, String stage) {
        try {
            Timer timer = Timer.builder(METRIC_WORKFLOW_APPROVAL_DURATION)
                    .tags(Tags.of("stage", nullSafe(stage)))
                    .description("工作流审批耗时")
                    .register(meterRegistry);
            timer.record(java.time.Duration.ofMillis(durationMs));
        } catch (Throwable e) {
            log.debug("记录 workflow_approval_duration 失败: durationMs={}, stage={}", durationMs, stage, e);
        }
    }

    /**
     * 记录 Kafka 消费积压
     *
     * @param topic 主题
     * @param lag   积压消息数
     */
    public void recordKafkaLag(String topic, long lag) {
        try {
            AtomicLong holder = kafkaLagHolders.computeIfAbsent(nullSafe(topic), t -> {
                AtomicLong h = new AtomicLong(lag);
                meterRegistry.gauge(METRIC_KAFKA_LAG, Tags.of("topic", t), h);
                return h;
            });
            holder.set(lag);
        } catch (Throwable e) {
            log.debug("记录 kafka_lag 失败: topic={}, lag={}", topic, lag, e);
        }
    }

    /**
     * 获取当前 Kafka 积压值（供测试与查询使用）
     *
     * @param topic 主题
     * @return 积压值，未记录过返回 0
     */
    public long getKafkaLag(String topic) {
        AtomicLong holder = kafkaLagHolders.get(nullSafe(topic));
        return holder == null ? 0L : holder.get();
    }

    private static String nullSafe(String s) {
        return s == null ? "unknown" : s;
    }
}
