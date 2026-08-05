package com.redteam.analyze.controller;

import com.redteam.analyze.config.TaxiiProperties;
import com.redteam.analyze.service.StixExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TAXII 2.1 Server 控制器单元测试
 *
 * <p>覆盖 Discovery / API Root / Collections / Objects 端点与认证逻辑。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaxiiControllerTest {

    @Mock
    private StixExportService stixExportService;

    @Mock
    private TaxiiProperties taxiiProperties;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private TaxiiController controller;

    /**
     * 有效 Basic Auth 头（taxii:taxii123）
     */
    private static final String BASIC_AUTH_HEADER = "Basic " +
            Base64.getEncoder().encodeToString("taxii:taxii123".getBytes(StandardCharsets.UTF_8));

    /**
     * Mock IOC Bundle JSON（含 1 个 indicator 对象）
     */
    private static final String MOCK_IOC_BUNDLE =
            "{\"type\":\"bundle\",\"id\":\"bundle--test-123\","
                    + "\"objects\":[{\"type\":\"indicator\",\"id\":\"indicator--abc\","
                    + "\"pattern\":\"[ipv4-addr:value = '1.2.3.4']\",\"patternType\":\"stix\"}]}";

    /**
     * Mock APT Bundle JSON（含 1 个 threat-actor 对象）
     */
    private static final String MOCK_APT_BUNDLE =
            "{\"type\":\"bundle\",\"id\":\"bundle--apt-456\","
                    + "\"objects\":[{\"type\":\"threat-actor\",\"id\":\"threat-actor--xyz\","
                    + "\"name\":\"APT28\"}]}";

    @BeforeEach
    void setUp() {
        // 默认配置：启用 basic 认证
        when(taxiiProperties.isEnabled()).thenReturn(true);
        when(taxiiProperties.getAuthType()).thenReturn("basic");
        when(taxiiProperties.getUsername()).thenReturn("taxii");
        when(taxiiProperties.getPassword()).thenReturn("taxii123");
        // 默认请求携带有效 Basic Auth
        when(request.getHeader("Authorization")).thenReturn(BASIC_AUTH_HEADER);
        // Mock StixExportService 返回 Bundle JSON
        when(stixExportService.exportIocsToStix(any())).thenReturn(MOCK_IOC_BUNDLE);
        when(stixExportService.exportAptsToStix(any())).thenReturn(MOCK_APT_BUNDLE);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建无效 Basic Auth 头（wrong:wrong）
     */
    private String invalidBasicAuth() {
        return "Basic " + Base64.getEncoder()
                .encodeToString("wrong:wrong".getBytes(StandardCharsets.UTF_8));
    }

    // ==================== Discovery 端点 ====================

    @Test
    @DisplayName("testDiscovery: Discovery 端点返回正确结构")
    void testDiscovery() {
        ResponseEntity<Map<String, Object>> resp = controller.discovery(request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        // Content-Type 应为 TAXII 2.1
        MediaType contentType = resp.getHeaders().getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.toString().contains("application/taxii+json"));
        assertTrue(contentType.toString().contains("2.1"));

        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals("红方文件分析平台 TAXII Server", body.get("title"));
        assertEquals("威胁情报订阅服务", body.get("description"));
        assertEquals("/taxii/api/", body.get("default"));
        Object apiRoots = body.get("api_roots");
        assertNotNull(apiRoots);
        assertTrue(apiRoots instanceof List<?>);
        assertTrue(((List<?>) apiRoots).contains("/taxii/api/"));
    }

    // ==================== Collections 端点 ====================

    @Test
    @DisplayName("testCollections: Collections 列表包含 IOC + APT 集合")
    void testCollections() {
        ResponseEntity<Map<String, Object>> resp = controller.collections(request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        Object collections = body.get("collections");
        assertNotNull(collections);
        assertTrue(collections instanceof List<?>);

        List<?> list = (List<?>) collections;
        assertEquals(2, list.size());

        // 校验两个集合 ID 存在
        Set<String> ids = new HashSet<>();
        for (Object o : list) {
            assertTrue(o instanceof Map<?, ?>);
            ids.add(String.valueOf(((Map<?, ?>) o).get("id")));
        }
        assertTrue(ids.contains("ioc-collection"));
        assertTrue(ids.contains("apt-collection"));

        // 校验集合字段
        for (Object o : list) {
            Map<?, ?> c = (Map<?, ?>) o;
            assertNotNull(c.get("title"));
            assertNotNull(c.get("description"));
            assertEquals(Boolean.TRUE, c.get("can_read"));
            assertEquals(Boolean.FALSE, c.get("can_write"));
            assertNotNull(c.get("media_types"));
        }
    }

    // ==================== Collection Detail 端点 ====================

    @Test
    @DisplayName("testCollectionDetail: 单个集合详情返回正确字段")
    void testCollectionDetail() {
        ResponseEntity<Map<String, Object>> resp =
                controller.collectionDetail("ioc-collection", request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals("ioc-collection", body.get("id"));
        assertEquals("IOC 情报集合", body.get("title"));
        assertEquals("恶意IP/域名/文件哈希", body.get("description"));
        assertEquals(Boolean.TRUE, body.get("can_read"));
        assertEquals(Boolean.FALSE, body.get("can_write"));
        assertNotNull(body.get("media_types"));
    }

    @Test
    @DisplayName("testCollectionDetail_AptCollection: APT 集合详情")
    void testCollectionDetail_AptCollection() {
        ResponseEntity<Map<String, Object>> resp =
                controller.collectionDetail("apt-collection", request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals("apt-collection", body.get("id"));
        assertEquals("APT 组织集合", body.get("title"));
    }

    @Test
    @DisplayName("testCollectionDetail_NotFound: 不存在的集合返回 404")
    void testCollectionDetail_NotFound() {
        ResponseEntity<Map<String, Object>> resp =
                controller.collectionDetail("unknown-collection", request);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // ==================== Objects 端点 ====================

    @Test
    @DisplayName("testObjects: Objects 端点返回 STIX 对象")
    void testObjects() {
        ResponseEntity<Map<String, Object>> resp = controller.objects(
                "ioc-collection", null, 50, null, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals(Boolean.FALSE, body.get("more"));
        Object objects = body.get("objects");
        assertNotNull(objects);
        assertTrue(objects instanceof List<?>);
        assertFalse(((List<?>) objects).isEmpty());

        // 校验返回的 STIX 对象结构
        Object first = ((List<?>) objects).get(0);
        assertTrue(first instanceof Map<?, ?>);
        Map<?, ?> obj = (Map<?, ?>) first;
        assertEquals("indicator", obj.get("type"));
        assertTrue(String.valueOf(obj.get("id")).startsWith("indicator--"));
    }

    @Test
    @DisplayName("testObjects_AptCollection: APT 集合返回 threat-actor 对象")
    void testObjects_AptCollection() {
        ResponseEntity<Map<String, Object>> resp = controller.objects(
                "apt-collection", null, 50, null, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        List<?> objects = (List<?>) body.get("objects");
        assertNotNull(objects);
        assertFalse(objects.isEmpty());
        Map<?, ?> first = (Map<?, ?>) objects.get(0);
        assertEquals("threat-actor", first.get("type"));
        assertEquals("APT28", first.get("name"));
    }

    @Test
    @DisplayName("testObjects_TypeFilter: 类型过滤生效")
    void testObjects_TypeFilter() {
        // 请求仅返回 indicator 类型
        ResponseEntity<Map<String, Object>> resp = controller.objects(
                "ioc-collection", "indicator", 50, null, request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        List<?> objects = (List<?>) body.get("objects");
        assertNotNull(objects);
        assertFalse(objects.isEmpty());
        for (Object o : objects) {
            assertEquals("indicator", ((Map<?, ?>) o).get("type"));
        }
    }

    @Test
    @DisplayName("testObjects_NotFound: 不存在的集合返回 404")
    void testObjects_NotFound() {
        ResponseEntity<Map<String, Object>> resp = controller.objects(
                "unknown", null, 50, null, request);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    // ==================== 认证测试 ====================

    @Test
    @DisplayName("testAuth_InvalidCredentials: 无效认证返回 401")
    void testAuth_InvalidCredentials() {
        // 请求携带无效凭证
        when(request.getHeader("Authorization")).thenReturn(invalidBasicAuth());

        ResponseEntity<Map<String, Object>> resp = controller.discovery(request);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        // 响应体应为错误信息
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertNotNull(body.get("title"));
    }

    @Test
    @DisplayName("testAuth_MissingHeader: 缺失 Authorization 头返回 401")
    void testAuth_MissingHeader() {
        when(request.getHeader("Authorization")).thenReturn(null);

        ResponseEntity<Map<String, Object>> resp = controller.collections(request);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @DisplayName("testAuth_None: auth-type=none 时无需认证")
    void testAuth_None() {
        when(taxiiProperties.getAuthType()).thenReturn("none");
        when(request.getHeader("Authorization")).thenReturn(null);

        ResponseEntity<Map<String, Object>> resp = controller.discovery(request);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertEquals("红方文件分析平台 TAXII Server", body.get("title"));
    }

    @Test
    @DisplayName("testAuth_ApiKey: API Key 认证通过")
    void testAuth_ApiKey() {
        when(taxiiProperties.getAuthType()).thenReturn("apikey");
        when(taxiiProperties.getApiKey()).thenReturn("secret-key");
        when(request.getHeader("X-API-Key")).thenReturn("secret-key");
        when(request.getHeader("Authorization")).thenReturn(null);

        ResponseEntity<Map<String, Object>> resp = controller.apiRoot(request);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("testAuth_ApiKey_Invalid: 错误 API Key 返回 401")
    void testAuth_ApiKey_Invalid() {
        when(taxiiProperties.getAuthType()).thenReturn("apikey");
        when(taxiiProperties.getApiKey()).thenReturn("secret-key");
        when(request.getHeader("X-API-Key")).thenReturn("wrong-key");
        when(request.getHeader("Authorization")).thenReturn(null);

        ResponseEntity<Map<String, Object>> resp = controller.apiRoot(request);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @DisplayName("testAuth_Disabled: TAXII 禁用时跳过认证")
    void testAuth_Disabled() {
        when(taxiiProperties.isEnabled()).thenReturn(false);
        when(request.getHeader("Authorization")).thenReturn(null);

        ResponseEntity<Map<String, Object>> resp = controller.discovery(request);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    // ==================== API Root 端点 ====================

    @Test
    @DisplayName("testApiRoot: API Root 端点返回正确结构")
    void testApiRoot() {
        ResponseEntity<Map<String, Object>> resp = controller.apiRoot(request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> body = resp.getBody();
        assertNotNull(body);
        assertNotNull(body.get("title"));
        Object versions = body.get("versions");
        assertNotNull(versions);
        assertTrue(versions instanceof List<?>);
        assertTrue(((List<?>) versions).contains("taxii-2.1"));
        assertNotNull(body.get("max_content_length"));
    }
}
