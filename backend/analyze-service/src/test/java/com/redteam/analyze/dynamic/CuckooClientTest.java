package com.redteam.analyze.dynamic;

import com.redteam.analyze.config.CuckooProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CuckooClient 单元测试
 *
 * <p>覆盖提交、状态查询、报告获取、降级策略等场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CuckooClientTest {

    private CuckooProperties properties;

    @BeforeEach
    void setUp() {
        properties = new CuckooProperties();
        properties.setEnabled(true);
        properties.setEndpoint("http://localhost:8090");
        properties.setApikey("test-key");
        properties.setTimeout(5000);
    }

    // ==================== submitFile ====================

    @Test
    @DisplayName("submitFile: fileId 为空返回降级 taskId")
    void submitFile_nullFileId_returnsDegraded() {
        CuckooClient client = new CuckooClient(properties);
        ReflectionTestUtils.setField(client, "restClient", RestClient.builder().build());
        String taskId = client.submitFile(null);
        assertNotNull(taskId);
        assertTrue(taskId.startsWith(CuckooClient.DEGRADED_PREFIX));
        assertTrue(taskId.contains("null"));
    }

    @Test
    @DisplayName("submitFile: 配置禁用返回降级 taskId")
    void submitFile_disabled_returnsDegraded() {
        properties.setEnabled(false);
        CuckooClient client = new CuckooClient(properties);
        // restClient 为空时，disabled 分支不会调用 HTTP
        String taskId = client.submitFile(100L);
        assertNotNull(taskId);
        assertEquals(CuckooClient.DEGRADED_PREFIX + "100", taskId);
        assertTrue(client.isDegraded(taskId));
    }

    @Test
    @DisplayName("submitFile: HTTP 调用异常降级处理")
    void submitFile_httpThrows_returnsDegraded() {
        CuckooClient client = new CuckooClient(properties);
        // restClient 未初始化，调用会抛 NPE，被捕获降级
        String taskId = client.submitFile(200L);
        assertNotNull(taskId);
        assertTrue(taskId.startsWith(CuckooClient.DEGRADED_PREFIX));
        assertTrue(taskId.contains("200"));
    }

    // ==================== getTaskStatus ====================

    @Test
    @DisplayName("getTaskStatus: 降级 taskId 返回 DEGRADED")
    void getTaskStatus_degradedTaskId_returnsDegraded() {
        CuckooClient client = new CuckooClient(properties);
        String status = client.getTaskStatus(CuckooClient.DEGRADED_PREFIX + "100");
        assertEquals(CuckooClient.STATUS_DEGRADED, status);
    }

    @Test
    @DisplayName("getTaskStatus: 配置禁用返回 DEGRADED")
    void getTaskStatus_disabled_returnsDegraded() {
        properties.setEnabled(false);
        CuckooClient client = new CuckooClient(properties);
        String status = client.getTaskStatus("task-123");
        assertEquals(CuckooClient.STATUS_DEGRADED, status);
    }

    @Test
    @DisplayName("getTaskStatus: HTTP 异常返回 DEGRADED")
    void getTaskStatus_httpThrows_returnsDegraded() {
        CuckooClient client = new CuckooClient(properties);
        // restClient 未初始化，HTTP 调用抛异常，被捕获降级
        String status = client.getTaskStatus("task-456");
        assertEquals(CuckooClient.STATUS_DEGRADED, status);
    }

    // ==================== getReport ====================

    @Test
    @DisplayName("getReport: 降级 taskId 返回降级报告 JSON")
    void getReport_degradedTaskId_returnsDegradedJson() {
        CuckooClient client = new CuckooClient(properties);
        String report = client.getReport(CuckooClient.DEGRADED_PREFIX + "100");
        assertNotNull(report);
        assertTrue(report.contains("\"degraded\":true"));
        assertTrue(report.contains(CuckooClient.STATUS_DEGRADED));
    }

    @Test
    @DisplayName("getReport: 配置禁用返回降级报告 JSON")
    void getReport_disabled_returnsDegradedJson() {
        properties.setEnabled(false);
        CuckooClient client = new CuckooClient(properties);
        String report = client.getReport("task-789");
        assertNotNull(report);
        assertTrue(report.contains("\"degraded\":true"));
    }

    @Test
    @DisplayName("getReport: HTTP 异常降级返回 JSON")
    void getReport_httpThrows_returnsDegradedJson() {
        CuckooClient client = new CuckooClient(properties);
        String report = client.getReport("task-999");
        assertNotNull(report);
        assertTrue(report.contains(CuckooClient.STATUS_DEGRADED));
    }

    // ==================== 降级报告构建 ====================

    @Test
    @DisplayName("buildDegradedReportJson: 包含 degraded 标记与 score=0")
    void buildDegradedReportJson_containsDegradedAndZeroScore() {
        CuckooClient client = new CuckooClient(properties);
        String json = client.buildDegradedReportJson("task-1");
        assertNotNull(json);
        assertTrue(json.contains("\"score\":0"));
        assertTrue(json.contains("\"taskId\":\"task-1\""));
        assertTrue(json.contains("Cuckoo 沙箱不可用"));
    }

    // ==================== isEnabled / isDegraded ====================

    @Test
    @DisplayName("isEnabled: 反映配置状态")
    void isEnabled_reflectsConfig() {
        CuckooClient client = new CuckooClient(properties);
        assertTrue(client.isEnabled());
        properties.setEnabled(false);
        assertFalse(client.isEnabled());
    }

    @Test
    @DisplayName("isDegraded: 正确识别降级前缀")
    void isDegraded_identifiesPrefix() {
        CuckooClient client = new CuckooClient(properties);
        assertTrue(client.isDegraded(CuckooClient.DEGRADED_PREFIX + "1"));
        assertFalse(client.isDegraded("task-1"));
        assertFalse(client.isDegraded(null));
    }

    // ==================== init ====================

    @Test
    @DisplayName("init: 启用时正常初始化不抛异常")
    void init_enabled_doesNotThrow() {
        CuckooClient client = new CuckooClient(properties);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> client.init());
    }

    @Test
    @DisplayName("init: 已注入 restClient 时跳过重建")
    void init_skipWhenRestClientInjected() {
        RestClient injected = RestClient.builder().build();
        CuckooClient client = new CuckooClient(properties, injected);
        client.init();
        // 通过反射验证 restClient 未被替换（同一实例）
        Object after = ReflectionTestUtils.getField(client, "restClient");
        assertEquals(injected, after);
    }
}
