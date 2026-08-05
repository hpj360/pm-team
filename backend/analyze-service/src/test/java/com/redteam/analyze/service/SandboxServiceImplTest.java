package com.redteam.analyze.service;

import com.redteam.analyze.config.SandboxProperties;
import com.redteam.analyze.dto.SandboxReportVO;
import com.redteam.analyze.service.impl.SandboxServiceImpl;
import com.redteam.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 沙箱分析服务单元测试
 *
 * <p>覆盖提交、获取报告、获取状态、降级策略、参数校验等场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SandboxServiceImplTest {

    @Mock
    private SandboxProperties sandboxProperties;

    @InjectMocks
    private SandboxServiceImpl sandboxService;

    @BeforeEach
    void setUp() {
        // 默认配置：禁用沙箱
        when(sandboxProperties.isEnabled()).thenReturn(false);
        when(sandboxProperties.getApiUrl()).thenReturn("http://localhost:8090");
        when(sandboxProperties.getApiKey()).thenReturn("test-key");
    }

    // ==================== submitToSandbox ====================

    @Test
    @DisplayName("submitToSandbox: fileId 为空抛业务异常")
    void submitToSandbox_nullFileId_throwsException() {
        assertThrows(BusinessException.class, () -> sandboxService.submitToSandbox(null));
    }

    @Test
    @DisplayName("submitToSandbox: 沙箱禁用返回降级任务ID")
    void submitToSandbox_disabled_returnsDegradedId() {
        when(sandboxProperties.isEnabled()).thenReturn(false);
        String taskId = sandboxService.submitToSandbox(100L);
        assertNotNull(taskId);
        assertTrue(taskId.startsWith("degraded-"));
        assertTrue(taskId.contains("100"));
    }

    @Test
    @DisplayName("submitToSandbox: 沙箱启用但 HTTP 调用失败降级处理")
    void submitToSandbox_enabledButHttpFails_returnsDegradedId() {
        when(sandboxProperties.isEnabled()).thenReturn(true);
        // restClient 未初始化（init 未调用），HTTP 调用抛 NPE，被捕获降级
        String taskId = sandboxService.submitToSandbox(200L);
        assertNotNull(taskId);
        assertTrue(taskId.startsWith("degraded-"));
    }

    @Test
    @DisplayName("submitToSandbox: 降级任务ID包含原 fileId")
    void submitToSandbox_degradedIdContainsFileId() {
        when(sandboxProperties.isEnabled()).thenReturn(false);
        String taskId = sandboxService.submitToSandbox(999L);
        assertEquals("degraded-999", taskId);
    }

    // ==================== getSandboxReport ====================

    @Test
    @DisplayName("getSandboxReport: taskId 为空抛业务异常")
    void getSandboxReport_nullTaskId_throwsException() {
        assertThrows(BusinessException.class, () -> sandboxService.getSandboxReport(null));
    }

    @Test
    @DisplayName("getSandboxReport: 空字符串 taskId 抛业务异常")
    void getSandboxReport_blankTaskId_throwsException() {
        assertThrows(BusinessException.class, () -> sandboxService.getSandboxReport(""));
        assertThrows(BusinessException.class, () -> sandboxService.getSandboxReport("   "));
    }

    @Test
    @DisplayName("getSandboxReport: 降级任务ID返回降级报告")
    void getSandboxReport_degradedTaskId_returnsDegradedReport() {
        SandboxReportVO report = sandboxService.getSandboxReport("degraded-100");
        assertNotNull(report);
        assertEquals("degraded-100", report.getTaskId());
        assertEquals("DEGRADED", report.getStatus());
        assertTrue(report.getDegraded());
        assertEquals(0.0, report.getScore());
        assertNotNull(report.getThreats());
        assertTrue(report.getThreats().isEmpty());
        assertNotNull(report.getSignatures());
        assertTrue(report.getSignatures().isEmpty());
        assertNotNull(report.getStaticInfo());
        assertEquals(Boolean.TRUE, report.getStaticInfo().get("degraded"));
        assertNotNull(report.getErrorMessage());
    }

    @Test
    @DisplayName("getSandboxReport: 正常任务ID但HTTP失败返回降级报告")
    void getSandboxReport_normalTaskId_httpFails_returnsDegradedReport() {
        when(sandboxProperties.isEnabled()).thenReturn(true);
        // restClient 未初始化，HTTP 调用抛异常，被捕获降级
        SandboxReportVO report = sandboxService.getSandboxReport("task-123");
        assertNotNull(report);
        assertEquals("DEGRADED", report.getStatus());
        assertTrue(report.getDegraded());
    }

    // ==================== getSandboxStatus ====================

    @Test
    @DisplayName("getSandboxStatus: taskId 为空抛业务异常")
    void getSandboxStatus_nullTaskId_throwsException() {
        assertThrows(BusinessException.class, () -> sandboxService.getSandboxStatus(null));
    }

    @Test
    @DisplayName("getSandboxStatus: 空字符串 taskId 抛业务异常")
    void getSandboxStatus_blankTaskId_throwsException() {
        assertThrows(BusinessException.class, () -> sandboxService.getSandboxStatus(""));
    }

    @Test
    @DisplayName("getSandboxStatus: 降级任务ID返回 DEGRADED")
    void getSandboxStatus_degradedTaskId_returnsDegraded() {
        String status = sandboxService.getSandboxStatus("degraded-100");
        assertEquals("DEGRADED", status);
    }

    @Test
    @DisplayName("getSandboxStatus: 沙箱禁用返回 DEGRADED")
    void getSandboxStatus_disabled_returnsDegraded() {
        when(sandboxProperties.isEnabled()).thenReturn(false);
        String status = sandboxService.getSandboxStatus("task-123");
        assertEquals("DEGRADED", status);
    }

    @Test
    @DisplayName("getSandboxStatus: 沙箱启用但HTTP失败返回 DEGRADED")
    void getSandboxStatus_enabledButHttpFails_returnsDegraded() {
        when(sandboxProperties.isEnabled()).thenReturn(true);
        // restClient 未初始化，HTTP 调用抛异常，被捕获降级
        String status = sandboxService.getSandboxStatus("task-456");
        assertEquals("DEGRADED", status);
    }

    // ==================== 降级报告完整性验证 ====================

    @Test
    @DisplayName("降级报告：包含静态分析提示信息")
    void degradedReport_containsStaticInfoNote() {
        SandboxReportVO report = sandboxService.getSandboxReport("degraded-200");
        assertNotNull(report.getStaticInfo());
        assertEquals("沙箱服务不可用，仅返回基础静态分析信息", report.getStaticInfo().get("note"));
    }

    @Test
    @DisplayName("降级报告：摘要描述正确")
    void degradedReport_summaryCorrect() {
        SandboxReportVO report = sandboxService.getSandboxReport("degraded-300");
        assertEquals("沙箱不可用，已降级为基础静态分析结果", report.getSummary());
    }

    @Test
    @DisplayName("降级报告：错误信息标识降级")
    void degradedReport_errorMessageIndicatesDegradation() {
        SandboxReportVO report = sandboxService.getSandboxReport("degraded-400");
        assertEquals("沙箱服务降级", report.getErrorMessage());
    }

    @Test
    @DisplayName("降级报告：威胁评分为 0")
    void degradedReport_scoreZero() {
        SandboxReportVO report = sandboxService.getSandboxReport("degraded-500");
        assertEquals(0.0, report.getScore());
    }

    // ==================== init 方法验证 ====================

    @Test
    @DisplayName("init: 沙箱启用时正常初始化不抛异常")
    void init_enabled_doesNotThrow() {
        when(sandboxProperties.isEnabled()).thenReturn(true);
        when(sandboxProperties.getApiUrl()).thenReturn("http://localhost:8090");
        when(sandboxProperties.getApiKey()).thenReturn("key-123");
        assertDoesNotThrow(() -> sandboxService.init());
    }

    @Test
    @DisplayName("init: 沙箱禁用时也能正常初始化")
    void init_disabled_doesNotThrow() {
        when(sandboxProperties.isEnabled()).thenReturn(false);
        assertDoesNotThrow(() -> sandboxService.init());
    }
}
