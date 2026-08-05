package com.redteam.common.telemetry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警规则定义 (v5.4)
 *
 * <p>描述一条 Prometheus 告警规则的元信息：PromQL 表达式、触发持续时长、严重等级、
 * 飞书 Webhook 地址及附加标签/注解。{@link AlertNotifier} 根据此对象组装飞书卡片
 * 并触达对应通道。</p>
 *
 * <p>内置规则（{@link #builtinRules()}）：</p>
 * <ul>
 *   <li>{@code error_rate_high}：5xx 请求速率 &gt; 0.05，P1</li>
 *   <li>{@code ai_degraded}：AI 降级调用 &gt; 0，P1</li>
 *   <li>{@code rate_limit_triggered}：限流触发 &gt; 100，P2</li>
 *   <li>{@code kafka_lag_high}：Kafka 积压 &gt; 10000，P1</li>
 * </ul>
 *
 * @author 红方团队
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    /** 规则名（唯一标识，与 Prometheus alert name 对应） */
    private String name;

    /** PromQL 表达式 */
    private String expr;

    /** 触发持续时长（PromQL for 子句），如 "5m" */
    private String duration;

    /** 严重等级 */
    private AlertSeverity severity;

    /** 飞书 Webhook 地址（为空时使用全局默认） */
    private String feishuWebhook;

    /** 规则摘要 */
    private String summary;

    /** 规则描述 */
    private String description;

    /** 附加标签 */
    @Builder.Default
    private Map<String, String> labels = new LinkedHashMap<>();

    /** 附加注解 */
    @Builder.Default
    private Map<String, String> annotations = new LinkedHashMap<>();

    /**
     * 构造内置告警规则集合
     *
     * @return 不可变内置规则映射（name -&gt; rule）
     */
    public static Map<String, AlertRule> builtinRules() {
        Map<String, AlertRule> rules = new LinkedHashMap<>();
        rules.put("error_rate_high", AlertRule.builder()
                .name("error_rate_high")
                .expr("rate(http_server_requests_seconds_count{status=~\"5..\"}[5m]) > 0.05")
                .duration("5m")
                .severity(AlertSeverity.P1)
                .summary("HTTP 5xx 错误率过高")
                .description("近 5 分钟 5xx 请求速率超过 0.05，可能存在服务异常")
                .labels(labelMap("category", "http", "service", "gateway"))
                .build());
        rules.put("ai_degraded", AlertRule.builder()
                .name("ai_degraded")
                .expr("ai_invoke_total{result=\"degraded\"} > 0")
                .duration("1m")
                .severity(AlertSeverity.P1)
                .summary("AI 服务出现降级调用")
                .description("AI 调用出现降级（degraded），需检查模型服务可用性")
                .labels(labelMap("category", "ai", "service", "ai-service"))
                .build());
        rules.put("rate_limit_triggered", AlertRule.builder()
                .name("rate_limit_triggered")
                .expr("rate_limited_total > 100")
                .duration("2m")
                .severity(AlertSeverity.P2)
                .summary("限流频繁触发")
                .description("近 2 分钟限流触发次数超过 100，需关注流量或容量")
                .labels(labelMap("category", "ratelimit"))
                .build());
        rules.put("kafka_lag_high", AlertRule.builder()
                .name("kafka_lag_high")
                .expr("kafka_lag > 10000")
                .duration("5m")
                .severity(AlertSeverity.P1)
                .summary("Kafka 消费积压过高")
                .description("Kafka 积压消息数超过 10000 且持续 5 分钟，消费者可能阻塞")
                .labels(labelMap("category", "kafka"))
                .build());
        return Collections.unmodifiableMap(rules);
    }

    /**
     * 构造标签 Map（key-value 交替参数）
     *
     * @param kv key/value 交替数组
     * @return 标签 Map
     */
    private static Map<String, String> labelMap(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}
