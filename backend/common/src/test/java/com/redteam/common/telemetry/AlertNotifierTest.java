package com.redteam.common.telemetry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AlertNotifier 单元测试 (v5.4)
 *
 * <p>覆盖场景：</p>
 * <ol>
 *   <li>{@code sendAlert(rule=null)} 直接返回，不触发任何调用</li>
 *   <li>告警禁用（enabled=false）仅记录日志，不调用 RestTemplate</li>
 *   <li>无 Webhook 配置时仅记录日志，不抛异常</li>
 *   <li>P0 规则触发电话预留通道 + 飞书卡片</li>
 *   <li>规则级 Webhook 优先于全局默认</li>
 *   <li>{@code buildFeishuCard} 卡片结构正确（msg_type/header/elements）</li>
 *   <li>RestTemplate 抛异常时被吞掉，不影响业务</li>
 * </ol>
 *
 * @author 红方团队
 */
class AlertNotifierTest {

    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
    }

    @Test
    @DisplayName("testSendAlert_NullRule: rule 为 null 直接返回")
    void testSendAlert_NullRule() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "http://default/hook", true);
        assertDoesNotThrow(() -> notifier.sendAlert(null, null, null));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("testSendAlert_Disabled: 告警禁用时不调用 RestTemplate")
    void testSendAlert_Disabled() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "http://default/hook", false);
        AlertRule rule = AlertRule.builder()
                .name("r1").summary("s1").severity(AlertSeverity.P1).build();
        notifier.sendAlert(rule, null, null);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("testSendAlert_NoWebhook: 无 Webhook 配置仅记录日志，不抛异常")
    void testSendAlert_NoWebhook() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "", true);
        AlertRule rule = AlertRule.builder()
                .name("r1").summary("s1").severity(AlertSeverity.P2).build();
        assertDoesNotThrow(() -> notifier.sendAlert(rule, null, null));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("testSendAlert_P0: P0 规则使用规则级 Webhook 并调用 RestTemplate")
    void testSendAlert_P0() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "http://default/hook", true);
        AlertRule rule = AlertRule.builder()
                .name("fatal").summary("服务不可用")
                .severity(AlertSeverity.P0)
                .feishuWebhook("http://rule-level/hook")
                .build();
        notifier.sendAlert(rule, labelMap("k", "v"), null);
        verify(restTemplate, times(1))
                .postForEntity(eq("http://rule-level/hook"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("testResolveWebhook_RuleOverridesDefault: 规则级 Webhook 优先于全局默认")
    void testResolveWebhook_RuleOverridesDefault() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "http://default/hook", true);
        AlertRule rule = AlertRule.builder()
                .name("r2").summary("s2").severity(AlertSeverity.P1)
                .feishuWebhook("http://rule-level/hook")
                .build();
        notifier.sendAlert(rule, null, null);
        verify(restTemplate, times(1))
                .postForEntity(eq("http://rule-level/hook"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("testSendAlert_UsesDefaultWebhook: 规则未配 Webhook 时用全局默认")
    void testSendAlert_UsesDefaultWebhook() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "http://default/hook", true);
        AlertRule rule = AlertRule.builder()
                .name("r3").summary("s3").severity(AlertSeverity.P2).build();
        notifier.sendAlert(rule, null, null);
        verify(restTemplate, times(1))
                .postForEntity(eq("http://default/hook"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    @DisplayName("testSendAlert_RestTemplateThrows: RestTemplate 异常被吞掉不外抛")
    void testSendAlert_RestTemplateThrows() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "http://default/hook", true);
        AlertRule rule = AlertRule.builder()
                .name("r4").summary("s4").severity(AlertSeverity.P1).build();
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("connection refused"));
        assertDoesNotThrow(() -> notifier.sendAlert(rule, null, null));
    }

    @Test
    @DisplayName("testBuildFeishuCard: 卡片结构与字段正确")
    void testBuildFeishuCard() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "http://default/hook", true);
        AlertRule rule = AlertRule.builder()
                .name("kafka_lag_high").summary("Kafka 积压")
                .description("积压超过 10000")
                .expr("kafka_lag > 10000").duration("5m")
                .severity(AlertSeverity.P1).build();
        Map<String, String> labels = labelMap("category", "kafka");
        Map<String, String> annotations = labelMap("runbook", "https://wiki/runbook");

        Map<String, Object> payload = notifier.buildFeishuCard(rule, AlertSeverity.P1, labels, annotations);

        assertEquals("interactive", payload.get("msg_type"), "msg_type 应为 interactive");
        Map<String, Object> card = (Map<String, Object>) payload.get("card");
        assertNotNull(card, "card 不应为 null");

        Map<String, Object> header = (Map<String, Object>) card.get("header");
        Map<String, Object> titleObj = (Map<String, Object>) header.get("title");
        String title = (String) titleObj.get("content");
        assertTrue(title.contains("[P1]"), "标题应包含 [P1]");
        assertTrue(title.contains("kafka_lag_high"), "标题应包含规则名");
        assertTrue(title.contains("Kafka 积压"), "标题应包含 summary");
        assertEquals("orange", header.get("template"), "P1 卡片颜色应为 orange");

        java.util.List<Object> elements = (java.util.List<Object>) card.get("elements");
        assertEquals(1, elements.size(), "elements 应有 1 个 div");
        Map<String, Object> div = (Map<String, Object>) elements.get(0);
        Map<String, Object> textObj = (Map<String, Object>) div.get("text");
        String content = (String) textObj.get("content");
        assertTrue(content.contains("**级别**: P1"), "内容应包含级别");
        assertTrue(content.contains("**描述**"), "内容应包含描述");
        assertTrue(content.contains("**表达式**"), "内容应包含表达式");
        assertTrue(content.contains("**持续**"), "内容应包含持续时长");
        assertTrue(content.contains("category=kafka"), "内容应包含标签");
        assertTrue(content.contains("runbook=https://wiki/runbook"), "内容应包含注解");
        assertTrue(content.contains("<at user_id=\"all\">所有人</at>"), "P1 加急应 @all");
    }

    @Test
    @DisplayName("testBuildFeishuCard_P2NotUrgent: P2 卡片不包含 @all")
    void testBuildFeishuCard_P2NotUrgent() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "http://default/hook", true);
        AlertRule rule = AlertRule.builder()
                .name("rate_limit_triggered").summary("限流")
                .severity(AlertSeverity.P2).build();

        Map<String, Object> payload = notifier.buildFeishuCard(rule, AlertSeverity.P2, null, null);
        Map<String, Object> card = (Map<String, Object>) payload.get("card");
        Map<String, Object> header = (Map<String, Object>) card.get("header");
        assertEquals("blue", header.get("template"), "P2 卡片颜色应为 blue");

        java.util.List<Object> elements = (java.util.List<Object>) card.get("elements");
        Map<String, Object> div = (Map<String, Object>) elements.get(0);
        String content = (String) ((Map<String, Object>) div.get("text")).get("content");
        assertFalse(content.contains("<at user_id=\"all\">"), "P2 不应 @all");
    }

    @Test
    @DisplayName("testSendAlert_ContentType: 请求体 Content-Type 为 application/json")
    void testSendAlert_ContentType() {
        AlertNotifier notifier = new AlertNotifier(restTemplate, "http://default/hook", true);
        AlertRule rule = AlertRule.builder()
                .name("r5").summary("s5").severity(AlertSeverity.P2).build();

        org.mockito.ArgumentCaptor<HttpEntity<?>> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        notifier.sendAlert(rule, null, null);
        verify(restTemplate).postForEntity(eq("http://default/hook"), captor.capture(), eq(String.class));
        assertEquals(MediaType.APPLICATION_JSON, captor.getValue().getHeaders().getContentType(),
                "Content-Type 应为 application/json");
    }

    /**
     * 构造标签 Map
     */
    private Map<String, String> labelMap(String... kv) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }
}
