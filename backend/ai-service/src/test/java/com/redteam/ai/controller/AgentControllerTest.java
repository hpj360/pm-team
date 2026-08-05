package com.redteam.ai.controller;

import com.redteam.ai.agent.AgentTrace;
import com.redteam.ai.agent.AutonomousAnalysisService;
import com.redteam.ai.agent.RagService;
import com.redteam.ai.dto.AgentAnalysisRequest;
import com.redteam.ai.dto.KnowledgeIndexRequest;
import com.redteam.ai.entity.AgentTaskEntity;
import com.redteam.ai.entity.KnowledgeEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link AgentController} 端点测试
 *
 * <p>使用 MockMvc 测试 7 个端点的请求与响应。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentControllerTest {

    @Mock
    private AutonomousAnalysisService autonomousAnalysisService;

    @Mock
    private RagService ragService;

    @InjectMocks
    private AgentController agentController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(agentController).build();
    }

    /**
     * 用例 1: POST /api/ai/agent/analyze 应返回 taskId
     */
    @Test
    @DisplayName("submitAnalysis - 提交分析任务应返回 taskId")
    void testSubmitAnalysis() throws Exception {
        when(autonomousAnalysisService.submitAnalysis(anyString(), anyLong()))
                .thenReturn("task-001");

        AgentAnalysisRequest request = new AgentAnalysisRequest();
        request.setQuery("分析最近的钓鱼攻击");
        request.setUserId(1001L);

        mockMvc.perform(post("/api/ai/agent/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("task-001"));
    }

    /**
     * 用例 2: POST /api/ai/agent/analyze 空查询应返回 400
     */
    @Test
    @DisplayName("submitAnalysis_EmptyQuery - 空查询应返回错误")
    void testSubmitAnalysis_EmptyQuery() throws Exception {
        AgentAnalysisRequest request = new AgentAnalysisRequest();
        request.setQuery("");
        request.setUserId(1L);

        mockMvc.perform(post("/api/ai/agent/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 用例 3: GET /api/ai/agent/tasks/{taskId} 应返回任务详情
     */
    @Test
    @DisplayName("getTask - 应返回任务详情")
    void testGetTask() throws Exception {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setTaskId("task-002");
        entity.setQuery("测试查询");
        entity.setStatus("COMPLETED");
        entity.setConclusion("测试结论");
        entity.setConfidence(0.85);
        when(autonomousAnalysisService.getTask("task-002")).thenReturn(entity);

        mockMvc.perform(get("/api/ai/agent/tasks/task-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.taskId").value("task-002"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.conclusion").value("测试结论"));
    }

    /**
     * 用例 4: GET /api/ai/agent/tasks/{taskId} 不存在应返回 404
     */
    @Test
    @DisplayName("getTask_NotFound - 任务不存在应返回 404")
    void testGetTask_NotFound() throws Exception {
        when(autonomousAnalysisService.getTask("nonexistent")).thenReturn(null);

        mockMvc.perform(get("/api/ai/agent/tasks/nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    /**
     * 用例 5: GET /api/ai/agent/tasks 应返回任务列表
     */
    @Test
    @DisplayName("listTasks - 应返回任务列表")
    void testListTasks() throws Exception {
        AgentTaskEntity e1 = new AgentTaskEntity();
        e1.setTaskId("t1");
        e1.setStatus("COMPLETED");
        when(autonomousAnalysisService.listTasks(anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(e1));

        mockMvc.perform(get("/api/ai/agent/tasks").param("userId", "1").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].taskId").value("t1"));
    }

    /**
     * 用例 6: GET /api/ai/agent/traces/{taskId} 应返回推理轨迹
     */
    @Test
    @DisplayName("getTraces - 应返回推理轨迹")
    void testGetTraces() throws Exception {
        List<AgentTrace> traces = Collections.singletonList(
                new AgentTrace(1, "思考", "search_files", "{\"query\":\"test\"}", "观察结果"));
        when(autonomousAnalysisService.getTraces("task-003")).thenReturn(traces);

        mockMvc.perform(get("/api/ai/agent/traces/task-003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].step").value(1))
                .andExpect(jsonPath("$.data[0].action").value("search_files"));
    }

    /**
     * 用例 7: POST /api/ai/knowledge 应索引知识库文档
     */
    @Test
    @DisplayName("indexKnowledge - 应索引知识库文档")
    void testIndexKnowledge() throws Exception {
        when(ragService.indexDocument(isNull(), anyString(), anyMap()))
                .thenReturn("k-001");

        KnowledgeIndexRequest request = new KnowledgeIndexRequest();
        request.setTitle("ATT&CK T1059");
        request.setContent("命令行执行技术");
        request.setSource("ATT&CK");
        request.setMetadata(new HashMap<>());

        mockMvc.perform(post("/api/ai/knowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("k-001"));
    }

    /**
     * 用例 8: GET /api/ai/knowledge/search 应返回检索结果
     */
    @Test
    @DisplayName("searchKnowledge - 应返回知识库检索结果")
    void testSearchKnowledge() throws Exception {
        Map<String, Object> hit = new HashMap<>();
        hit.put("knowledgeId", "k1");
        hit.put("title", "T1059");
        when(ragService.search(anyString(), anyInt()))
                .thenReturn(Collections.singletonList(hit));

        mockMvc.perform(get("/api/ai/knowledge/search")
                        .param("query", "命令执行")
                        .param("topK", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].knowledgeId").value("k1"));
    }

    /**
     * 用例 9: GET /api/ai/knowledge 应返回知识库列表
     */
    @Test
    @DisplayName("listKnowledge - 应返回知识库文档列表")
    void testListKnowledge() throws Exception {
        KnowledgeEntity e = new KnowledgeEntity();
        e.setKnowledgeId("k1");
        e.setTitle("测试知识");
        when(ragService.listAll()).thenReturn(Collections.singletonList(e));

        mockMvc.perform(get("/api/ai/knowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].knowledgeId").value("k1"));
    }
}
