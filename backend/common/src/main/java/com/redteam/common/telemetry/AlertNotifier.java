package com.redteam.common.telemetry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警通知器 (v5.4)
 *
 * <p>基于飞书自定义机器人 Webhook 推送告警卡片，按 {@link AlertSeverity} 分级触达：</p>
 * <ul>
 *   <li>P0 致命：预留电话通道 + 飞书红色加急卡片 + @all（记录电话占位日志）</li>
 *   <li>P1 严重：飞书红/橙色加急卡片 + @all</li>
 *   <li>P2 一般：飞书蓝色普通卡片</li>
 * </ul>
 *
 * <p>直接使用 {@link RestTemplate} 调用飞书 webhook，不依赖 notification-service，
 * 避免告警链路对业务通知服务的强依赖。发送失败仅记录日志，不抛异常。</p>
 *
 * @author 红方团队
 */
@Slf4j
public class AlertNotifier {

    /** 飞书 interactive 卡片消息类型 */
    private static final String MSG_TYPE_INTERACTIVE = "interactive";

    /** 加急 @all 文本（lark_md） */
    private static final String AT_ALL = "<at user_id=\"all\">所有人</at>";

    private final RestTemplate restTemplate;

    /** 全局默认飞书 Webhook 地址 */
    private final String defaultWebhook;

    /** 是否启用告警发送（false 时仅记录日志） */
    private final boolean enabled;

    public AlertNotifier(RestTemplate restTemplate, String defaultWebhook, boolean enabled) {
        this.restTemplate = restTemplate;
        this.defaultWebhook = defaultWebhook;
        this.enabled = enabled;
    }

    /**
     * 发送告警
     *
     * @param rule        告警规则
     * @param labels      附加标签（可为 null）
     * @param annotations 附加注解（可为 null）
     */
    public void sendAlert(AlertRule rule, Map<String, String> labels, Map<String, String> annotations) {
        if (rule == null) {
            return;
        }
        if (!enabled) {
            log.warn("告警通知已禁用（redteam.telemetry.alert.enabled=false），跳过发送: rule={}", rule.getName());
            return;
        }
        AlertSeverity severity = rule.getSeverity() == null ? AlertSeverity.P2 : rule.getSeverity();

        // P0 预留电话通道
        if (severity == AlertSeverity.P0) {
            log.error("[告警-P0-电话预留] rule={}, summary={}", rule.getName(), rule.getSummary());
            // TODO: 接入电话告警平台（如阿里云语音通知），当前预留
        }

        String webhook = resolveWebhook(rule);
        if (webhook == null || webhook.isBlank()) {
            log.warn("飞书 Webhook 未配置，告警仅记录日志: rule={}, severity={}", rule.getName(), severity.getCode());
            logAlert(rule, severity, labels, annotations);
            return;
        }

        Map<String, Object> payload = buildFeishuCard(rule, severity, labels, annotations);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(webhook, entity, String.class);
            log.info("飞书告警已发送: rule={}, severity={}, webhook={}", rule.getName(), severity.getCode(), webhook);
        } catch (Throwable e) {
            log.error("发送飞书告警失败: rule={}, webhook={}", rule.getName(), webhook, e);
        }
    }

    /**
     * 解析 Webhook 地址：规则级 &gt; 全局默认
     *
     * @param rule 告警规则
     * @return Webhook 地址
     */
    private String resolveWebhook(AlertRule rule) {
        String hook = rule.getFeishuWebhook();
        if (hook != null && !hook.isBlank()) {
            return hook;
        }
        return defaultWebhook;
    }

    /**
     * 构造飞书互动卡片消息体
     *
     * @param rule        规则
     * @param severity    级别
     * @param labels      标签
     * @param annotations 注解
     * @return 飞书消息体
     */
    Map<String, Object> buildFeishuCard(AlertRule rule, AlertSeverity severity,
                                        Map<String, String> labels, Map<String, String> annotations) {
        String title = String.format("[%s][%s] %s", severity.getCode(), rule.getName(), rule.getSummary());

        StringBuilder content = new StringBuilder();
        content.append("**级别**: ").append(severity.getCode()).append(" ").append(severity.getLabel()).append("\n");
        if (rule.getDescription() != null) {
            content.append("**描述**: ").append(rule.getDescription()).append("\n");
        }
        if (rule.getExpr() != null) {
            content.append("**表达式**: `").append(rule.getExpr()).append("`\n");
        }
        if (rule.getDuration() != null) {
            content.append("**持续**: ").append(rule.getDuration()).append("\n");
        }
        if (labels != null && !labels.isEmpty()) {
            content.append("**标签**: ").append(joinMap(labels)).append("\n");
        }
        if (annotations != null && !annotations.isEmpty()) {
            content.append("**注解**: ").append(joinMap(annotations)).append("\n");
        }
        if (severity.isUrgent()) {
            content.append("\n").append(AT_ALL);
        }

        Map<String, Object> titleObj = new LinkedHashMap<>();
        titleObj.put("tag", "plain_text");
        titleObj.put("content", title);

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("title", titleObj);
        header.put("template", severity.getCardColor());

        Map<String, Object> textObj = new LinkedHashMap<>();
        textObj.put("tag", "lark_md");
        textObj.put("content", content.toString());

        Map<String, Object> div = new LinkedHashMap<>();
        div.put("tag", "div");
        div.put("text", textObj);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", header);
        card.put("elements", java.util.Collections.singletonList(div));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msg_type", MSG_TYPE_INTERACTIVE);
        payload.put("card", card);
        return payload;
    }

    /**
     * 仅记录告警日志（无 Webhook 时）
     */
    private void logAlert(AlertRule rule, AlertSeverity severity,
                          Map<String, String> labels, Map<String, String> annotations) {
        log.error("[告警-{}] rule={}, summary={}, expr={}, labels={}, annotations={}",
                severity.getCode(), rule.getName(), rule.getSummary(), rule.getExpr(), labels, annotations);
    }

    /**
     * 拼接 Map 为 k=v, k=v 形式
     *
     * @param map Map
     * @return 拼接字符串
     */
    private String joinMap(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append("=").append(e.getValue());
            first = false;
        }
        return sb.toString();
    }
}
