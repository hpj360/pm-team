package com.redteam.ai.agent;

import com.redteam.ai.agent.tool.AgentTool;
import com.redteam.ai.client.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AgentExecutor} 单元测试
 *
 * <p>覆盖 ReAct 循环正常完成、最大步数限制、LLM 不可用降级、轨迹记录四类场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentExecutorTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private ToolRegistry toolRegistry;

    @InjectMocks
    private AgentExecutor agentExecutor;

    @BeforeEach
    void setUp() {
        // 默认 mock：工具表为空，listTools 返回空列表
        when(toolRegistry.listTools()).thenReturn(Collections.emptyList());
    }

    /**
     * 用例 1: LLM 首步返回 Final Answer 应直接完成
     */
    @Test
    @DisplayName("execute_FinalAnswerFirstStep - 首步返回最终答案应直接完成")
    void testExecute_FinalAnswerFirstStep() {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(
                "Thought: 用户的问题可以直接回答\n"
                        + "Final Answer: 基于现有知识，APT28 是一个俄罗斯关联的 APT 组织。");

        AgentResult result = agentExecutor.execute("APT28 是什么？", Set.of("*"));

        assertTrue(result.getConclusion().contains("APT28"));
        assertEquals(1, result.getTraces().size());
        assertEquals("FINAL_ANSWER", result.getTraces().get(0).getAction());
        assertFalse(result.isDegraded());
        assertTrue(result.getConfidence() >= 0.5);
    }

    /**
     * 用例 2: ReAct 循环 - 先调用工具再返回 Final Answer
     */
    @Test
    @DisplayName("execute_ReActLoop - 调用工具后返回最终答案")
    void testExecute_ReActLoop() {
        when(llmClient.isAvailable()).thenReturn(true);
        // 工具 mock
        AgentTool mockTool = mock(AgentTool.class);
        when(mockTool.getName()).thenReturn("search_files");
        when(mockTool.execute(anyMap())).thenReturn("找到文件 malware.exe，fileId=f1");
        when(toolRegistry.getTool("search_files")).thenReturn(mockTool);
        when(toolRegistry.checkPermission(eq("search_files"), anySet())).thenReturn(true);

        // 第一次 LLM 返回：调用工具
        // 第二次 LLM 返回：最终答案
        when(llmClient.chat(anyString(), anyString())).thenReturn(
                "Thought: 我需要先检索文件\n"
                        + "Action: search_files\n"
                        + "Action Input: {\"query\": \"malware\"}",
                "Thought: 已获取文件信息\n"
                        + "Final Answer: 检索到恶意文件 malware.exe，建议立即隔离。");

        AgentResult result = agentExecutor.execute("查找最近的恶意文件", Set.of("*"));

        assertEquals(2, result.getTraces().size());
        assertEquals("search_files", result.getTraces().get(0).getAction());
        assertEquals("FINAL_ANSWER", result.getTraces().get(1).getAction());
        assertTrue(result.getConclusion().contains("malware.exe"));
        assertFalse(result.getEvidenceChain().isEmpty());
    }

    /**
     * 用例 3: LLM 不可用降级 - 应返回降级结论
     */
    @Test
    @DisplayName("execute_LlmUnavailable - LLM 不可用应降级返回模板结论")
    void testExecute_LlmUnavailable() {
        when(llmClient.isAvailable()).thenReturn(false);

        AgentResult result = agentExecutor.execute("分析最近的攻击", Set.of("*"));

        assertTrue(result.isDegraded());
        assertNotNull(result.getConclusion());
        assertTrue(result.getConclusion().contains("降级"));
        assertNotNull(result.getErrorMessage());
        verify(llmClient, never()).chat(anyString(), anyString());
    }

    /**
     * 用例 4: 达到最大步数应强制总结
     */
    @Test
    @DisplayName("execute_MaxSteps - 达到最大步数应强制总结")
    void testExecute_MaxSteps() {
        when(llmClient.isAvailable()).thenReturn(true);
        // 每次都返回无法解析的格式（既非 Action 也非 Final Answer）
        when(llmClient.chat(anyString(), anyString())).thenReturn("无法解析的响应");

        // maxSteps=2，2 步后应强制总结
        // 强制总结时 LLM 也会被调用（用于总结），mock 同样返回
        AgentResult result = agentExecutor.execute("测试", Set.of("*"), 2, 8000);

        assertNotNull(result.getConclusion());
        assertEquals(2, result.getTraces().size());
        // 非正常结束置信度较低
        assertTrue(result.getConfidence() < 0.7);
    }

    /**
     * 用例 5: 工具不存在时应记录错误观察结果并继续循环
     */
    @Test
    @DisplayName("execute_ToolNotFound - 工具不存在应记录错误并继续")
    void testExecute_ToolNotFound() {
        when(llmClient.isAvailable()).thenReturn(true);
        when(toolRegistry.getTool("nonexistent")).thenReturn(null);

        when(llmClient.chat(anyString(), anyString())).thenReturn(
                "Thought: 调用一个不存在的工具\n"
                        + "Action: nonexistent\n"
                        + "Action Input: {}",
                "Thought: 工具不存在，直接回答\n"
                        + "Final Answer: 无法完成分析，工具不可用。");

        AgentResult result = agentExecutor.execute("测试", Set.of("*"));

        assertEquals(2, result.getTraces().size());
        assertTrue(result.getTraces().get(0).getObservation().contains("不存在"));
    }

    /**
     * 用例 6: 权限不足时应拒绝工具调用
     */
    @Test
    @DisplayName("execute_PermissionDenied - 权限不足应拒绝工具调用")
    void testExecute_PermissionDenied() {
        when(llmClient.isAvailable()).thenReturn(true);
        AgentTool mockTool = mock(AgentTool.class);
        when(mockTool.getName()).thenReturn("search_files");
        when(toolRegistry.getTool("search_files")).thenReturn(mockTool);
        when(toolRegistry.checkPermission(eq("search_files"), anySet())).thenReturn(false);

        when(llmClient.chat(anyString(), anyString())).thenReturn(
                "Thought: 调用搜索\n"
                        + "Action: search_files\n"
                        + "Action Input: {\"query\": \"test\"}",
                "Thought: 权限不足，直接回答\n"
                        + "Final Answer: 权限不足无法完成分析。");

        AgentResult result = agentExecutor.execute("测试", Collections.emptySet());

        assertTrue(result.getTraces().get(0).getObservation().contains("权限不足"));
    }

    /**
     * 用例 7: LLM 返回空响应应记录并继续
     */
    @Test
    @DisplayName("execute_EmptyResponse - LLM 空响应应记录并继续")
    void testExecute_EmptyResponse() {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(null, null);

        AgentResult result = agentExecutor.execute("测试", Set.of("*"), 2, 8000);

        // 2 步空响应后达到最大步数，强制总结
        assertNotNull(result.getConclusion());
    }
}
