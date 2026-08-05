package com.redteam.analyze.dynamic;

import com.redteam.analyze.config.CuckooProperties;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DynamicAnalysisService 单元测试
 *
 * <p>覆盖状态机流转、降级编排、联合 IOC 注入、参数校验等场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamicAnalysisServiceTest {

    @Mock
    private CuckooClient cuckooClient;

    @Mock
    private CuckooProperties cuckooProperties;

    @Mock
    private BehaviorIndicatorExtractor behaviorIndicatorExtractor;

    @InjectMocks
    private DynamicAnalysisService service;

    @BeforeEach
    void setUp() {
        // BehaviorIndicatorExtractor.extract 默认行为：调用真实逻辑填充 task
        doAnswer(invocation -> {
            DynamicAnalysisTask t = invocation.getArgument(0);
            t.setProcessTree(List.of());
            t.setNetworkConnections(List.of());
            t.setFileOperations(List.of());
            t.setAttackTechniques(List.of());
            t.setIocs(List.of());
            t.setIndicators(new HashMap<>());
            return null;
        }).when(behaviorIndicatorExtractor).extract(any(DynamicAnalysisTask.class));
        when(behaviorIndicatorExtractor.buildStixObjects(any(DynamicAnalysisTask.class)))
                .thenAnswer(inv -> List.of());
    }

    // ==================== submitDynamicAnalysis ====================

    @Test
    @DisplayName("submitDynamicAnalysis: fileId 为空抛业务异常")
    void submit_nullFileId_throwsException() {
        assertThrows(BusinessException.class, () -> service.submitDynamicAnalysis(null));
    }

    @Test
    @DisplayName("submitDynamicAnalysis: 正常提交状态为 SUBMITTED")
    void submit_normal_returnsSubmittedTask() {
        when(cuckooClient.submitFile(100L)).thenReturn("cuckoo-task-1");
        when(cuckooClient.isDegraded("cuckoo-task-1")).thenReturn(false);

        String taskId = service.submitDynamicAnalysis(100L);
        assertNotNull(taskId);
        assertTrue(taskId.startsWith("dyn-"));

        DynamicAnalysisTask task = service.getTask(taskId);
        assertEquals(DynamicAnalysisTask.STATUS_SUBMITTED, task.getStatus());
        assertEquals("cuckoo-task-1", task.getCuckooTaskId());
        assertFalse(task.isDegraded());
        verify(cuckooClient).submitFile(100L);
    }

    @Test
    @DisplayName("submitDynamicAnalysis: Cuckoo 降级时状态为 DEGRADED")
    void submit_degraded_returnsDegradedStatus() {
        String degradedId = CuckooClient.DEGRADED_PREFIX + "100";
        when(cuckooClient.submitFile(100L)).thenReturn(degradedId);
        when(cuckooClient.isDegraded(degradedId)).thenReturn(true);

        String taskId = service.submitDynamicAnalysis(100L);
        DynamicAnalysisTask task = service.getTask(taskId);
        assertEquals(DynamicAnalysisTask.STATUS_DEGRADED, task.getStatus());
        assertTrue(task.isDegraded());
        assertNotNull(task.getErrorMessage());
    }

    // ==================== pollTask ====================

    @Test
    @DisplayName("pollTask: COMPLETED 自动拉取报告并解析为 PARSED")
    void poll_completed_autoParsesReport() {
        when(cuckooClient.submitFile(1L)).thenReturn("cuckoo-1");
        when(cuckooClient.isDegraded("cuckoo-1")).thenReturn(false);
        when(cuckooClient.getTaskStatus("cuckoo-1")).thenReturn(CuckooClient.STATUS_COMPLETED);
        when(cuckooClient.getReport("cuckoo-1")).thenReturn("{\"score\":8.5}");

        String taskId = service.submitDynamicAnalysis(1L);
        String status = service.pollTask(taskId);

        assertEquals(DynamicAnalysisTask.STATUS_PARSED, status);
        DynamicAnalysisTask task = service.getTask(taskId);
        assertEquals("{\"score\":8.5}", task.getRawReport());
        assertNotNull(task.getParsedTime());
        verify(behaviorIndicatorExtractor).extract(any(DynamicAnalysisTask.class));
    }

    @Test
    @DisplayName("pollTask: RUNNING 状态推进")
    void poll_running_advancesToRunning() {
        when(cuckooClient.submitFile(2L)).thenReturn("cuckoo-2");
        when(cuckooClient.isDegraded("cuckoo-2")).thenReturn(false);
        when(cuckooClient.getTaskStatus("cuckoo-2")).thenReturn(CuckooClient.STATUS_RUNNING);

        String taskId = service.submitDynamicAnalysis(2L);
        String status = service.pollTask(taskId);
        assertEquals(DynamicAnalysisTask.STATUS_RUNNING, status);
    }

    @Test
    @DisplayName("pollTask: DEGRADED 状态不再轮询")
    void poll_degraded_skipsPolling() {
        String degradedId = CuckooClient.DEGRADED_PREFIX + "3";
        when(cuckooClient.submitFile(3L)).thenReturn(degradedId);
        when(cuckooClient.isDegraded(degradedId)).thenReturn(true);

        String taskId = service.submitDynamicAnalysis(3L);
        String status = service.pollTask(taskId);
        assertEquals(DynamicAnalysisTask.STATUS_DEGRADED, status);
        verify(cuckooClient, never()).getTaskStatus(anyString());
    }

    @Test
    @DisplayName("pollTask: 已 PARSED 任务不再推进")
    void poll_parsed_unchanged() {
        when(cuckooClient.submitFile(4L)).thenReturn("cuckoo-4");
        when(cuckooClient.isDegraded("cuckoo-4")).thenReturn(false);
        when(cuckooClient.getTaskStatus("cuckoo-4")).thenReturn(CuckooClient.STATUS_COMPLETED);
        when(cuckooClient.getReport("cuckoo-4")).thenReturn("{\"score\":5.0}");

        String taskId = service.submitDynamicAnalysis(4L);
        service.pollTask(taskId); // 第一次：PARSED
        // 第二次轮询：应保持 PARSED，不再调用 cuckoo
        String status = service.pollTask(taskId);
        assertEquals(DynamicAnalysisTask.STATUS_PARSED, status);
        verify(cuckooClient, times(1)).getTaskStatus(anyString());
    }

    @Test
    @DisplayName("pollTask: 状态查询降级标记为 DEGRADED")
    void poll_statusDegraded_marksTask() {
        when(cuckooClient.submitFile(5L)).thenReturn("cuckoo-5");
        when(cuckooClient.isDegraded("cuckoo-5")).thenReturn(false);
        when(cuckooClient.getTaskStatus("cuckoo-5")).thenReturn(CuckooClient.STATUS_DEGRADED);

        String taskId = service.submitDynamicAnalysis(5L);
        String status = service.pollTask(taskId);
        assertEquals(DynamicAnalysisTask.STATUS_DEGRADED, status);
        assertTrue(service.getTask(taskId).isDegraded());
    }

    // ==================== parseReport ====================

    @Test
    @DisplayName("parseReport: 报告为空标记 FAILED")
    void parse_blankReport_marksFailed() {
        when(cuckooClient.submitFile(6L)).thenReturn("cuckoo-6");
        when(cuckooClient.isDegraded("cuckoo-6")).thenReturn(false);

        String taskId = service.submitDynamicAnalysis(6L);
        // 手动设置空报告
        DynamicAnalysisTask task = service.getTask(taskId);
        task.setRawReport("");
        DynamicAnalysisTask result = service.parseReport(taskId);
        assertEquals(DynamicAnalysisTask.STATUS_FAILED, result.getStatus());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("parseReport: 降级任务跳过解析")
    void parse_degraded_skipped() {
        String degradedId = CuckooClient.DEGRADED_PREFIX + "7";
        when(cuckooClient.submitFile(7L)).thenReturn(degradedId);
        when(cuckooClient.isDegraded(degradedId)).thenReturn(true);

        String taskId = service.submitDynamicAnalysis(7L);
        DynamicAnalysisTask result = service.parseReport(taskId);
        assertEquals(DynamicAnalysisTask.STATUS_DEGRADED, result.getStatus());
        verify(behaviorIndicatorExtractor, never()).extract(any());
    }

    // ==================== 联合 IOC 注入 ====================

    @Test
    @DisplayName("attachStaticIocs: 解析后合并静态 IOC")
    void attachStaticIocs_mergedAfterParse() {
        when(cuckooClient.submitFile(8L)).thenReturn("cuckoo-8");
        when(cuckooClient.isDegraded("cuckoo-8")).thenReturn(false);
        when(cuckooClient.getTaskStatus("cuckoo-8")).thenReturn(CuckooClient.STATUS_COMPLETED);
        when(cuckooClient.getReport("cuckoo-8")).thenReturn("{\"score\":3.0}");

        // 模拟动态 IOC 提取结果
        doAnswer(invocation -> {
            DynamicAnalysisTask t = invocation.getArgument(0);
            Map<String, Object> dynIoc = new HashMap<>();
            dynIoc.put("type", "IP");
            dynIoc.put("value", "1.1.1.1");
            t.setIocs(new java.util.ArrayList<>(List.of(dynIoc)));
            t.setProcessTree(List.of());
            t.setNetworkConnections(List.of());
            t.setFileOperations(List.of());
            t.setAttackTechniques(List.of());
            t.setIndicators(new HashMap<>());
            return null;
        }).when(behaviorIndicatorExtractor).extract(any(DynamicAnalysisTask.class));

        String taskId = service.submitDynamicAnalysis(8L);
        // 注入静态 IOC
        Map<String, Object> staticIoc = new HashMap<>();
        staticIoc.put("type", "DOMAIN");
        staticIoc.put("value", "static-evil.com");
        service.attachStaticIocs(taskId, List.of(staticIoc));

        service.pollTask(taskId);

        DynamicReportVO vo = service.getReport(taskId);
        // 应包含动态 + 静态两个 IOC
        assertEquals(2, vo.getIocs().size());
        assertTrue(vo.getIocs().stream().anyMatch(i -> "static-evil.com".equals(i.get("value"))));
        assertTrue(vo.getIocs().stream().anyMatch(i -> "1.1.1.1".equals(i.get("value"))));
    }

    // ==================== getTask / getReport 校验 ====================

    @Test
    @DisplayName("getTask: taskId 为空抛异常")
    void getTask_blankId_throws() {
        assertThrows(BusinessException.class, () -> service.getTask(""));
        assertThrows(BusinessException.class, () -> service.getTask(null));
    }

    @Test
    @DisplayName("getTask: 不存在的 taskId 抛异常")
    void getTask_notFound_throws() {
        assertThrows(BusinessException.class, () -> service.getTask("non-existent"));
    }

    @Test
    @DisplayName("getReport: 返回 VO 含降级标记")
    void getReport_degraded_returnsDegradedVO() {
        String degradedId = CuckooClient.DEGRADED_PREFIX + "9";
        when(cuckooClient.submitFile(9L)).thenReturn(degradedId);
        when(cuckooClient.isDegraded(degradedId)).thenReturn(true);

        String taskId = service.submitDynamicAnalysis(9L);
        DynamicReportVO vo = service.getReport(taskId);
        assertNotNull(vo);
        assertTrue(vo.isDegraded());
        assertEquals(0.0, vo.getScore());
        assertNotNull(vo.getSummary());
    }

    @Test
    @DisplayName("getReport: 解析后 VO 含 STIX 对象")
    void getReport_parsed_containsStixObjects() {
        when(cuckooClient.submitFile(10L)).thenReturn("cuckoo-10");
        when(cuckooClient.isDegraded("cuckoo-10")).thenReturn(false);
        when(cuckooClient.getTaskStatus("cuckoo-10")).thenReturn(CuckooClient.STATUS_COMPLETED);
        when(cuckooClient.getReport("cuckoo-10")).thenReturn("{\"score\":7.0,\"summary\":\"malware\"}");
        when(behaviorIndicatorExtractor.buildStixObjects(any(DynamicAnalysisTask.class)))
                .thenAnswer(inv -> {
                    DynamicAnalysisTask t = inv.getArgument(0);
                    return new java.util.ArrayList<>(t.getProcessTree());
                });

        String taskId = service.submitDynamicAnalysis(10L);
        service.pollTask(taskId);
        DynamicReportVO vo = service.getReport(taskId);
        assertNotNull(vo.getStixObjects());
        assertEquals(7.0, vo.getScore());
        assertEquals("malware", vo.getSummary());
    }

    // ==================== listTasks ====================

    @Test
    @DisplayName("listTasks: 返回全部任务")
    void listTasks_returnsAll() {
        when(cuckooClient.submitFile(any())).thenReturn("cuckoo-x");
        when(cuckooClient.isDegraded(anyString())).thenReturn(false);
        service.submitDynamicAnalysis(11L);
        service.submitDynamicAnalysis(12L);
        List<DynamicAnalysisTask> list = service.listTasks();
        assertEquals(2, list.size());
    }

    // ==================== 联合编排（静态降级） ====================

    @Test
    @DisplayName("attachStaticIocs: null 或空 taskId 安全返回")
    void attachStaticIocs_nullSafe() {
        service.attachStaticIocs(null, List.of());
        service.attachStaticIocs("", List.of());
        // 不抛异常即可
    }
}
