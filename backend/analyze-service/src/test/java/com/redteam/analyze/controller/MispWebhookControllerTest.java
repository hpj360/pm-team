package com.redteam.analyze.controller;

import com.redteam.analyze.config.MispProperties;
import com.redteam.analyze.entity.IoCEntity;
import com.redteam.analyze.service.IoCService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MISP Webhook 控制器单元测试
 *
 * <p>覆盖 webhook 接收、HMAC-SHA256 签名校验（通过/失败/缺失）与
 * payload 解析（Event 级别 / Attribute 级别 / 非法 JSON）。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MispWebhookControllerTest {

    @Mock
    private MispProperties mispProperties;

    @Mock
    private IoCService ioCService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private MispWebhookController controller;

    /**
     * 测试用 secret
     */
    private static final String SECRET = "test-secret";

    /**
     * Event 级别 payload（含 2 个属性）
     */
    private static final String EVENT_PAYLOAD = "{"
            + "\"action\":\"add\","
            + "\"type\":\"event\","
            + "\"Event\":{"
            + "\"id\":\"1\","
            + "\"info\":\"test event\","
            + "\"threat_level_id\":\"2\","
            + "\"Attribute\":["
            + "{\"type\":\"ip-src\",\"value\":\"1.2.3.4\",\"comment\":\"c2\"},"
            + "{\"type\":\"domain\",\"value\":\"evil.com\"}"
            + "]}}";

    /**
     * Attribute 级别 payload（单个属性）
     */
    private static final String ATTRIBUTE_PAYLOAD = "{"
            + "\"action\":\"add\","
            + "\"type\":\"attribute\","
            + "\"event_id\":\"1\","
            + "\"Event\":{\"id\":\"1\",\"info\":\"parent event\",\"threat_level_id\":\"3\"},"
            + "\"Attribute\":{\"type\":\"md5\",\"value\":\"d41d8cd98f00b204e9800998ecf8427e\",\"comment\":\"sample\"}"
            + "}";

    @BeforeEach
    void setUp() {
        // 默认不配置 secret（跳过签名校验）
        when(mispProperties.getWebhookSecret()).thenReturn("");
        // 默认无签名头
        when(request.getHeader("X-Signature")).thenReturn(null);
        when(request.getHeader("Signature")).thenReturn(null);
        // mock saveOrUpdateIoc 直接返回输入
        when(ioCService.saveOrUpdateIoc(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * 计算 HMAC-SHA256 签名（hex 编码），与控制器逻辑一致
     */
    private String sign(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    // ==================== 签名校验 ====================

    @Test
    @DisplayName("testWebhook_NoSecret: 未配置 secret 时跳过签名校验并正常处理")
    void testWebhook_NoSecret() {
        when(mispProperties.getWebhookSecret()).thenReturn("");

        ResponseEntity<Map<String, Object>> resp = controller.webhook(EVENT_PAYLOAD, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals(2, body.get("saved"));
    }

    @Test
    @DisplayName("testWebhook_ValidSignature: 正确签名通过校验")
    void testWebhook_ValidSignature() throws Exception {
        when(mispProperties.getWebhookSecret()).thenReturn(SECRET);
        String sig = sign(SECRET, EVENT_PAYLOAD);
        when(request.getHeader("X-Signature")).thenReturn(sig);

        ResponseEntity<Map<String, Object>> resp = controller.webhook(EVENT_PAYLOAD, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
    }

    @Test
    @DisplayName("testWebhook_ValidSignature_WithPrefix: 带 sha256= 前缀的签名通过校验")
    void testWebhook_ValidSignature_WithPrefix() throws Exception {
        when(mispProperties.getWebhookSecret()).thenReturn(SECRET);
        String sig = "sha256=" + sign(SECRET, EVENT_PAYLOAD);
        when(request.getHeader("X-Signature")).thenReturn(sig);

        ResponseEntity<Map<String, Object>> resp = controller.webhook(EVENT_PAYLOAD, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
    }

    @Test
    @DisplayName("testWebhook_InvalidSignature: 错误签名返回 401")
    void testWebhook_InvalidSignature() {
        when(mispProperties.getWebhookSecret()).thenReturn(SECRET);
        when(request.getHeader("X-Signature")).thenReturn("deadbeef");

        ResponseEntity<Map<String, Object>> resp = controller.webhook(EVENT_PAYLOAD, request);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(Boolean.FALSE, body.get("success"));
        verify(ioCService, never()).saveOrUpdateIoc(any());
    }

    @Test
    @DisplayName("testWebhook_MissingSignature: 配置 secret 但缺失签名头返回 401")
    void testWebhook_MissingSignature() {
        when(mispProperties.getWebhookSecret()).thenReturn(SECRET);
        // request 默认无签名头

        ResponseEntity<Map<String, Object>> resp = controller.webhook(EVENT_PAYLOAD, request);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        verify(ioCService, never()).saveOrUpdateIoc(any());
    }

    // ==================== payload 解析 ====================

    @Test
    @DisplayName("testWebhook_EventPayload: Event 级别推送写入 2 条 IOC")
    void testWebhook_EventPayload() {
        ResponseEntity<Map<String, Object>> resp = controller.webhook(EVENT_PAYLOAD, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals(2, body.get("saved"));
        assertEquals("add", body.get("action"));
        assertEquals("event", body.get("type"));

        // 验证保存的 IOC
        ArgumentCaptor<IoCEntity> captor = ArgumentCaptor.forClass(IoCEntity.class);
        verify(ioCService, times(2)).saveOrUpdateIoc(captor.capture());
        List<IoCEntity> saved = captor.getAllValues();
        boolean hasIp = saved.stream().anyMatch(i ->
                "IP".equals(i.getIocType()) && "1.2.3.4".equals(i.getIocValue()));
        boolean hasDomain = saved.stream().anyMatch(i ->
                "DOMAIN".equals(i.getIocType()) && "evil.com".equals(i.getIocValue()));
        assertTrue(hasIp, "应保存 IP 类型 IOC");
        assertTrue(hasDomain, "应保存 DOMAIN 类型 IOC");
        // 校验来源与 MISP 事件 ID
        saved.forEach(i -> {
            assertEquals("MISP", i.getSource());
            assertEquals("1", i.getMispEventId());
        });
    }

    @Test
    @DisplayName("testWebhook_AttributePayload: Attribute 级别推送写入 1 条 IOC")
    void testWebhook_AttributePayload() {
        ResponseEntity<Map<String, Object>> resp = controller.webhook(ATTRIBUTE_PAYLOAD, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(Boolean.TRUE, body.get("success"));
        assertEquals(1, body.get("saved"));

        ArgumentCaptor<IoCEntity> captor = ArgumentCaptor.forClass(IoCEntity.class);
        verify(ioCService, times(1)).saveOrUpdateIoc(captor.capture());
        IoCEntity saved = captor.getValue();
        assertEquals("MD5", saved.getIocType());
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", saved.getIocValue());
        assertEquals("MISP", saved.getSource());
    }

    @Test
    @DisplayName("testWebhook_InvalidJson: 非法 JSON 返回 400")
    void testWebhook_InvalidJson() {
        ResponseEntity<Map<String, Object>> resp = controller.webhook("not a json", request);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(Boolean.FALSE, body.get("success"));
        verify(ioCService, never()).saveOrUpdateIoc(any());
    }

    @Test
    @DisplayName("testWebhook_EmptyBody: 空体返回 400")
    void testWebhook_EmptyBody() {
        ResponseEntity<Map<String, Object>> resp = controller.webhook("", request);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        verify(ioCService, never()).saveOrUpdateIoc(any());
    }
}
