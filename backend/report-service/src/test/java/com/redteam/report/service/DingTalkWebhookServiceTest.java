package com.redteam.report.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * {@link DingTalkWebhookService} 单元测试
 *
 * <p>使用 {@link MockRestServiceServer} 绑定 RestTemplate 模拟钉钉 Webhook 远程调用，
 * 覆盖发送成功、URL 为空跳过、发送失败不抛异常三类场景。</p>
 *
 * @author 红方团队
 */
class DingTalkWebhookServiceTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private DingTalkWebhookService dingTalkWebhookService;

    private static final String WEBHOOK_URL = "https://oapi.dingtalk.com/robot/send?access_token=xxxxxxxxxxxx";

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        dingTalkWebhookService = new DingTalkWebhookService(restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(
                dingTalkWebhookService, "dingtalkWebhookUrl", WEBHOOK_URL);
    }

    /**
     * 发送成功应返回 true，且 payload 包含 markdown 与 msgtype 字段
     */
    @Test
    @DisplayName("sendNotification - 发送成功应返回 true")
    void testSendNotification_Success() {
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"markdown\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"msgtype\"")))
                .andRespond(withSuccess());

        boolean result = dingTalkWebhookService.sendNotification(
                "每周渗透测试报告", "https://example.com/reports/rpt-001", "本周发现 3 个高危漏洞");

        assertTrue(result);
        mockServer.verify();
    }

    /**
     * Webhook URL 为空时应跳过推送并返回 false
     */
    @Test
    @DisplayName("sendNotification - URL 为空应跳过并返回 false")
    void testSendNotification_EmptyUrl() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                dingTalkWebhookService, "dingtalkWebhookUrl", "");

        boolean result = dingTalkWebhookService.sendNotification(
                "每周渗透测试报告", "https://example.com/reports/rpt-001", "摘要内容");

        assertFalse(result);
        // 未发起任何请求
        mockServer.verify();
    }

    /**
     * Webhook URL 为 null 时应跳过推送并返回 false
     */
    @Test
    @DisplayName("sendNotification - URL 为 null 应跳过并返回 false")
    void testSendNotification_NullUrl() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                dingTalkWebhookService, "dingtalkWebhookUrl", null);

        boolean result = dingTalkWebhookService.sendNotification(
                "每周渗透测试报告", "https://example.com/reports/rpt-001", "摘要内容");

        assertFalse(result);
    }

    /**
     * 发送失败（HTTP 4xx）不应抛出异常，应返回 false
     */
    @Test
    @DisplayName("sendNotification - HTTP 4xx 失败应返回 false 且不抛异常")
    void testSendNotification_SendFailure_4xx() {
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("invalid_payload"));

        boolean result = dingTalkWebhookService.sendNotification(
                "每周渗透测试报告", "https://example.com/reports/rpt-001", "摘要内容");

        assertFalse(result);
        mockServer.verify();
    }

    /**
     * 发送失败（HTTP 5xx）不应抛出异常，应返回 false
     */
    @Test
    @DisplayName("sendNotification - HTTP 5xx 失败应返回 false 且不抛异常")
    void testSendNotification_SendFailure_5xx() {
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("server error"));

        boolean result = dingTalkWebhookService.sendNotification(
                "每周渗透测试报告", "https://example.com/reports/rpt-001", "摘要内容");

        assertFalse(result);
        mockServer.verify();
    }

    /**
     * 入参为 null 时不应抛出 NPE，应正常发送（空字符串拼接）
     */
    @Test
    @DisplayName("sendNotification - null 入参应安全处理不抛异常")
    void testSendNotification_NullArgs() {
        mockServer.expect(requestTo(WEBHOOK_URL))
                .andRespond(withSuccess());

        boolean result = dingTalkWebhookService.sendNotification(null, null, null);

        assertTrue(result);
        mockServer.verify();
    }
}
