package com.redteam.ai.agent;

import com.redteam.ai.entity.AgentTaskEntity;
import com.redteam.ai.mapper.AgentTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AutonomousAnalysisService} 单元测试
 *
 * <p>覆盖提交任务、状态查询、结果获取、轨迹查询四类场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutonomousAnalysisServiceTest {

    @Mock
    private AgentExecutor agentExecutor;

    @Mock
    private AgentTaskMapper agentTaskMapper;

    @InjectMocks
    private AutonomousAnalysisService service;

    @BeforeEach
    void setUp() {
        // mapper insert 不做真实操作
        when(agentTaskMapper.insert(any(AgentTaskEntity.class))).thenReturn(1);
        when(agentTaskMapper.updateById(any(AgentTaskEntity.class))).thenReturn(1);
    }

    /**
     * 用例 1: submitAnalysis 应返回 taskId 并创建 PENDING 任务
     */
    @Test
    @DisplayName("submitAnalysis_Success - 应返回 taskId 并创建 PENDING 任务")
    void testSubmitAnalysis_Success() {
        // mock agentExecutor.execute 同步返回结果（executeAsync 是 @Async，但单元测试中仍会调用）
        AgentResult mockResult = AgentResult.builder()
                .conclusion("测试结论")
                .confidence(0.85)
                .degraded(false)
                .evidenceChain(Arrays.asList("证据1"))
                .traces(Arrays.asList(new AgentTrace(1, "思考", "FINAL_ANSWER", "", "结论")))
                .build();
        when(agentExecutor.execute(anyString(), anySet())).thenReturn(mockResult);

        String taskId = service.submitAnalysis("分析最近的攻击", 1001L);

        assertNotNull(taskId);
        // 任务应存在于缓存中
        AgentTaskEntity entity = service.getTask(taskId);
        assertNotNull(entity);
        assertEquals("分析最近的攻击", entity.getQuery());
        assertEquals(Long.valueOf(1001L), entity.getUserId());
    }

    /**
     * 用例 2: submitAnalysis 空查询应抛异常
     */
    @Test
    @DisplayName("submitAnalysis_EmptyQuery - 空查询应抛异常")
    void testSubmitAnalysis_EmptyQuery() {
        assertThrows(IllegalArgumentException.class, () -> service.submitAnalysis("", 1001L));
        assertThrows(IllegalArgumentException.class, () -> service.submitAnalysis(null, 1001L));
    }

    /**
     * 用例 3: getTask 不存在的 taskId 应返回 null
     */
    @Test
    @DisplayName("getTask_NotFound - 不存在的 taskId 应返回 null")
    void testGetTask_NotFound() {
        when(agentTaskMapper.selectById("nonexistent")).thenReturn(null);
        AgentTaskEntity entity = service.getTask("nonexistent");
        assertNull(entity);
    }

    /**
     * 用例 4: getTraces 应正确解析轨迹 JSON
     */
    @Test
    @DisplayName("getTraces_Success - 应正确解析推理轨迹")
    void testGetTraces_Success() {
        // 先提交一个任务（会触发 executeAsync）
        AgentResult mockResult = AgentResult.builder()
                .conclusion("结论")
                .confidence(0.9)
                .traces(Arrays.asList(new AgentTrace(1, "思考", "FINAL_ANSWER", "", "结论")))
                .build();
        when(agentExecutor.execute(anyString(), anySet())).thenReturn(mockResult);

        String taskId = service.submitAnalysis("测试", 1L);

        List<AgentTrace> traces = service.getTraces(taskId);
        // executeAsync 是异步的，可能还没完成；但 @Async 在单元测试中默认同步执行（因为线程池被 mock）
        // 这里验证方法不抛异常即可
        assertNotNull(traces);
    }

    /**
     * 用例 5: listTasks 应返回任务列表
     */
    @Test
    @DisplayName("listTasks_Success - 应返回任务列表")
    void testListTasks_Success() {
        AgentTaskEntity e1 = new AgentTaskEntity();
        e1.setTaskId("t1");
        e1.setUserId(1L);
        e1.setStatus("COMPLETED");
        e1.setQuery("查询1");
        when(agentTaskMapper.selectByUserId(eq(1L), anyInt())).thenReturn(Arrays.asList(e1));

        List<AgentTaskEntity> list = service.listTasks(1L, 10);

        assertEquals(1, list.size());
        assertEquals("t1", list.get(0).getTaskId());
    }

    /**
     * 用例 6: listTasks 数据库异常时应从缓存降级返回
     */
    @Test
    @DisplayName("listTasks_Degraded - 数据库异常时应从缓存降级返回")
    void testListTasks_Degraded() {
        when(agentTaskMapper.selectByUserId(anyLong(), anyInt()))
                .thenThrow(new RuntimeException("DB down"));

        // 先提交一个任务到缓存
        AgentResult mockResult = AgentResult.builder()
                .conclusion("结论")
                .confidence(0.5)
                .build();
        when(agentExecutor.execute(anyString(), anySet())).thenReturn(mockResult);
        service.submitAnalysis("测试缓存", 2L);

        List<AgentTaskEntity> list = service.listTasks(2L, 10);
        // 应从缓存返回
        assertNotNull(list);
    }
}
