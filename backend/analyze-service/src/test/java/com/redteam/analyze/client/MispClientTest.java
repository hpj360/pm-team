package com.redteam.analyze.client;

import com.redteam.analyze.config.MispProperties;
import com.redteam.analyze.dto.MispAttribute;
import com.redteam.analyze.dto.MispEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * MISP 客户端单元测试
 *
 * <p>mock RestTemplate，验证 addEvent / listEvents / listAttributes / addAttribute
 * 的请求与响应解析逻辑，以及网络错误的异常封装。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MispClientTest {

    @Mock
    private RestTemplate restTemplate;

    private MispClient mispClient;

    @BeforeEach
    void setUp() {
        MispProperties props = new MispProperties();
        props.setEnabled(true);
        props.setEndpoint("http://localhost:8081");
        props.setApikey("test-key");
        props.setTimeout(5000);
        // 使用 package-private 构造器注入 mock RestTemplate
        mispClient = new MispClient(props, restTemplate);
    }

    /**
     * 测试 addEvent：正常响应解析
     */
    @Test
    @DisplayName("testAddEvent: 创建事件并解析返回的 Event")
    void testAddEvent() {
        String respJson = "{\"response\":{\"Event\":{"
                + "\"id\":\"123\","
                + "\"info\":\"test event\","
                + "\"threat_level_id\":\"2\","
                + "\"analysis\":\"0\","
                + "\"date\":\"2026-08-05\","
                + "\"uuid\":\"abc-uuid\"}}}";
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(respJson, HttpStatus.OK));

        MispEvent input = new MispEvent();
        input.setInfo("test event");
        input.setThreatLevelId("2");

        MispEvent created = mispClient.addEvent(input);

        assertNotNull(created);
        assertEquals("123", created.getId());
        assertEquals("test event", created.getInfo());
        assertEquals("2", created.getThreatLevelId());
        assertEquals("0", created.getAnalysis());
        assertEquals("2026-08-05", created.getDate());
        assertEquals("abc-uuid", created.getUuid());
    }

    /**
     * 测试 listEvents：数组格式响应解析
     */
    @Test
    @DisplayName("testListEvents: 列出事件并解析数组")
    void testListEvents() {
        String respJson = "["
                + "{\"Event\":{\"id\":\"1\",\"info\":\"event1\",\"threat_level_id\":\"1\"}},"
                + "{\"Event\":{\"id\":\"2\",\"info\":\"event2\",\"threat_level_id\":\"2\"}}"
                + "]";
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(respJson, HttpStatus.OK));

        List<MispEvent> events = mispClient.listEvents();

        assertNotNull(events);
        assertEquals(2, events.size());
        assertEquals("1", events.get(0).getId());
        assertEquals("event1", events.get(0).getInfo());
        assertEquals("2", events.get(1).getId());
        assertEquals("event2", events.get(1).getInfo());
    }

    /**
     * 测试 listAttributes：response.Attribute 数组格式解析
     */
    @Test
    @DisplayName("testListAttributes: 列出指定事件的属性")
    void testListAttributes() {
        String respJson = "{\"response\":{\"Attribute\":["
                + "{\"id\":\"10\",\"type\":\"ip-src\",\"value\":\"1.2.3.4\","
                + "\"category\":\"Network activity\",\"to_ids\":\"1\",\"comment\":\"c2\"},"
                + "{\"id\":\"11\",\"type\":\"domain\",\"value\":\"evil.com\","
                + "\"category\":\"Network activity\",\"to_ids\":\"0\"}"
                + "]}}";
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(respJson, HttpStatus.OK));

        List<MispAttribute> attrs = mispClient.listAttributes("123");

        assertNotNull(attrs);
        assertEquals(2, attrs.size());
        assertEquals("10", attrs.get(0).getId());
        assertEquals("ip-src", attrs.get(0).getType());
        assertEquals("1.2.3.4", attrs.get(0).getValue());
        assertTrue(attrs.get(0).isToIdsTrue());
        assertEquals("domain", attrs.get(1).getType());
        assertEquals("evil.com", attrs.get(1).getValue());
        assertFalse(attrs.get(1).isToIdsTrue());
    }

    /**
     * 测试 addAttribute：正常响应解析
     */
    @Test
    @DisplayName("testAddAttribute: 向事件添加属性并解析返回")
    void testAddAttribute() {
        String respJson = "{\"response\":{\"Attribute\":{"
                + "\"id\":\"20\",\"type\":\"ip-src\",\"value\":\"1.2.3.4\","
                + "\"category\":\"Network activity\",\"to_ids\":\"1\"}}}";
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(respJson, HttpStatus.OK));

        MispAttribute input = new MispAttribute();
        input.setType("ip-src");
        input.setValue("1.2.3.4");
        input.setCategory("Network activity");
        input.setToIds("1");

        MispAttribute created = mispClient.addAttribute("123", input);

        assertNotNull(created);
        assertEquals("20", created.getId());
        assertEquals("ip-src", created.getType());
        assertEquals("1.2.3.4", created.getValue());
        assertEquals("Network activity", created.getCategory());
    }

    /**
     * 测试 addEvent 网络错误：RestClientException 应封装为 MispException
     */
    @Test
    @DisplayName("testAddEvent_NetworkError: 网络错误封装为 MispException")
    void testAddEvent_NetworkError() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        MispEvent input = new MispEvent();
        input.setInfo("test");

        MispException ex = assertThrows(MispException.class, () -> mispClient.addEvent(input));
        assertTrue(ex.getMessage().contains("addEvent"));
        assertNotNull(ex.getCause());
    }

    /**
     * 测试 listEvents 网络错误
     */
    @Test
    @DisplayName("testListEvents_NetworkError: 网络错误封装为 MispException")
    void testListEvents_NetworkError() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("timeout"));

        assertThrows(MispException.class, () -> mispClient.listEvents());
    }

    /**
     * 测试 addAttribute 参数校验：eventId 为空抛 MispException
     */
    @Test
    @DisplayName("testAddAttribute_EmptyEventId: 空 eventId 抛 MispException")
    void testAddAttribute_EmptyEventId() {
        MispAttribute attr = new MispAttribute();
        attr.setType("ip-src");
        attr.setValue("1.2.3.4");
        assertThrows(MispException.class, () -> mispClient.addAttribute("", attr));
    }

    /**
     * 测试 listAttributes 空 eventId：返回空列表不抛异常
     */
    @Test
    @DisplayName("testListAttributes_EmptyEventId: 空 eventId 返回空列表")
    void testListAttributes_EmptyEventId() {
        List<MispAttribute> attrs = mispClient.listAttributes("");
        assertNotNull(attrs);
        assertTrue(attrs.isEmpty());
    }
}
