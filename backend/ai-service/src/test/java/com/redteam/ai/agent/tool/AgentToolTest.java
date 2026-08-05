package com.redteam.ai.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import com.redteam.ai.agent.RagService;
import com.redteam.ai.client.LlmClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 6 个内置 AgentTool 的单元测试
 *
 * <p>每个工具覆盖 1-2 个用例（含降级场景）。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentToolTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private LlmClient llmClient;

    @Mock
    private RagService ragService;

    private SearchFilesTool searchFilesTool;
    private GetThreatIntelTool getThreatIntelTool;
    private RunNerTool runNerTool;
    private QueryNeo4jTool queryNeo4jTool;
    private GenerateReportTool generateReportTool;
    private SearchKnowledgeTool searchKnowledgeTool;

    @BeforeEach
    void setUp() {
        searchFilesTool = new SearchFilesTool();
        ReflectionTestUtils.setField(searchFilesTool, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(searchFilesTool, "searchServiceUrl", "http://localhost:8081");

        getThreatIntelTool = new GetThreatIntelTool();
        ReflectionTestUtils.setField(getThreatIntelTool, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(getThreatIntelTool, "threatIntelServiceUrl", "http://localhost:8086");

        runNerTool = new RunNerTool();
        ReflectionTestUtils.setField(runNerTool, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(runNerTool, "nerServiceUrl", "http://localhost:8087");

        queryNeo4jTool = new QueryNeo4jTool();
        ReflectionTestUtils.setField(queryNeo4jTool, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(queryNeo4jTool, "profileServiceUrl", "http://localhost:8085");

        generateReportTool = new GenerateReportTool();
        ReflectionTestUtils.setField(generateReportTool, "llmClient", llmClient);

        searchKnowledgeTool = new SearchKnowledgeTool();
        ReflectionTestUtils.setField(searchKnowledgeTool, "ragService", ragService);
    }

    // ===== SearchFilesTool =====

    /**
     * 用例 1: SearchFilesTool 正常执行
     */
    @Test
    @DisplayName("SearchFilesTool_Success - 正常检索应返回结果")
    void testSearchFilesTool_Success() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("[{\"fileId\":\"f1\",\"fileName\":\"malware.exe\"}]");

        Map<String, Object> params = new HashMap<>();
        params.put("query", "malware");
        params.put("topK", 3);
        String result = searchFilesTool.execute(params);

        assertTrue(result.contains("f1"));
        verify(restTemplate).getForObject(anyString(), eq(String.class));
    }

    /**
     * 用例 2: SearchFilesTool 服务不可用降级
     */
    @Test
    @DisplayName("SearchFilesTool_Degraded - 服务不可用应返回降级提示")
    void testSearchFilesTool_Degraded() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("service down"));

        Map<String, Object> params = new HashMap<>();
        params.put("query", "malware");
        String result = searchFilesTool.execute(params);

        assertTrue(result.contains("不可用"));
    }

    // ===== GetThreatIntelTool =====

    /**
     * 用例 3: GetThreatIntelTool 正常执行
     */
    @Test
    @DisplayName("GetThreatIntelTool_Success - 正常查询应返回情报")
    void testGetThreatIntelTool_Success() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"indicator\":\"192.168.1.1\",\"threatActor\":\"APT28\"}");

        Map<String, Object> params = new HashMap<>();
        params.put("indicator", "192.168.1.1");
        String result = getThreatIntelTool.execute(params);

        assertTrue(result.contains("APT28"));
    }

    /**
     * 用例 4: GetThreatIntelTool 参数为空应返回错误
     */
    @Test
    @DisplayName("GetThreatIntelTool_EmptyParam - 空参数应返回错误")
    void testGetThreatIntelTool_EmptyParam() {
        String result = getThreatIntelTool.execute(new HashMap<>());
        assertTrue(result.contains("不能为空"));
    }

    // ===== RunNerTool =====

    /**
     * 用例 5: RunNerTool 正常执行
     */
    @Test
    @DisplayName("RunNerTool_Success - 正常识别应返回实体")
    void testRunNerTool_Success() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("[{\"entityType\":\"IP\",\"entityText\":\"10.0.0.1\"}]");

        Map<String, Object> params = new HashMap<>();
        params.put("text", "访问了 10.0.0.1 进行 C2 通信");
        String result = runNerTool.execute(params);

        assertTrue(result.contains("10.0.0.1"));
    }

    /**
     * 用例 6: RunNerTool 服务不可用降级
     */
    @Test
    @DisplayName("RunNerTool_Degraded - 服务不可用应返回降级提示")
    void testRunNerTool_Degraded() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("ner down"));

        Map<String, Object> params = new HashMap<>();
        params.put("text", "测试文本");
        String result = runNerTool.execute(params);

        assertTrue(result.contains("不可用"));
    }

    // ===== QueryNeo4jTool =====

    /**
     * 用例 7: QueryNeo4jTool 正常执行
     */
    @Test
    @DisplayName("QueryNeo4jTool_Success - 正常查询应返回关系")
    void testQueryNeo4jTool_Success() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"nodes\":[],\"edges\":[]}");

        Map<String, Object> params = new HashMap<>();
        params.put("entity", "192.168.1.1");
        params.put("depth", 2);
        String result = queryNeo4jTool.execute(params);

        assertNotNull(result);
    }

    /**
     * 用例 8: QueryNeo4jTool 服务不可用降级
     */
    @Test
    @DisplayName("QueryNeo4jTool_Degraded - 服务不可用应返回降级提示")
    void testQueryNeo4jTool_Degraded() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("neo4j down"));

        Map<String, Object> params = new HashMap<>();
        params.put("entity", "192.168.1.1");
        String result = queryNeo4jTool.execute(params);

        assertTrue(result.contains("不可用"));
    }

    // ===== GenerateReportTool =====

    /**
     * 用例 9: GenerateReportTool LLM 可用时正常生成
     */
    @Test
    @DisplayName("GenerateReportTool_Success - LLM 可用时应生成报告")
    void testGenerateReportTool_Success() {
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn("## 分析报告\n结论：高危");

        Map<String, Object> params = new HashMap<>();
        params.put("context", "发现 C2 通信与恶意文件");
        params.put("title", "威胁分析报告");
        String result = generateReportTool.execute(params);

        assertTrue(result.contains("高危"));
    }

    /**
     * 用例 10: GenerateReportTool LLM 不可用降级为模板
     */
    @Test
    @DisplayName("GenerateReportTool_Degraded - LLM 不可用应返回降级模板")
    void testGenerateReportTool_Degraded() {
        when(llmClient.isAvailable()).thenReturn(false);

        Map<String, Object> params = new HashMap<>();
        params.put("context", "测试证据");
        String result = generateReportTool.execute(params);

        assertTrue(result.contains("降级模板"));
        assertTrue(result.contains("测试证据"));
    }

    // ===== SearchKnowledgeTool =====

    /**
     * 用例 11: SearchKnowledgeTool 正常检索
     */
    @Test
    @DisplayName("SearchKnowledgeTool_Success - 正常检索应返回知识片段")
    void testSearchKnowledgeTool_Success() {
        Map<String, Object> hit = new HashMap<>();
        hit.put("knowledgeId", "k1");
        hit.put("title", "T1059");
        hit.put("content", "命令行执行");
        when(ragService.search(anyString(), anyInt())).thenReturn(List.of(hit));

        Map<String, Object> params = new HashMap<>();
        params.put("query", "命令执行");
        params.put("topK", 5);
        String result = searchKnowledgeTool.execute(params);

        assertTrue(result.contains("k1"));
        assertTrue(result.contains("T1059"));
    }

    /**
     * 用例 12: SearchKnowledgeTool 无匹配结果
     */
    @Test
    @DisplayName("SearchKnowledgeTool_NoResult - 无匹配应返回提示")
    void testSearchKnowledgeTool_NoResult() {
        when(ragService.search(anyString(), anyInt())).thenReturn(java.util.Collections.emptyList());

        Map<String, Object> params = new HashMap<>();
        params.put("query", "不存在的关键词");
        String result = searchKnowledgeTool.execute(params);

        assertTrue(result.contains("未检索到"));
    }

    // ===== 工具元信息验证 =====

    /**
     * 用例 13: 所有工具名称与描述应正确返回
     */
    @Test
    @DisplayName("ToolMetadata - 工具名称与描述应正确")
    void testToolMetadata() {
        assertEquals("search_files", searchFilesTool.getName());
        assertTrue(searchFilesTool.getDescription().contains("检索"));
        assertNotNull(searchFilesTool.getParametersSchema());

        assertEquals("get_threat_intel", getThreatIntelTool.getName());
        assertEquals("run_ner", runNerTool.getName());
        assertEquals("query_neo4j", queryNeo4jTool.getName());
        assertEquals("generate_report", generateReportTool.getName());
        assertEquals("search_knowledge", searchKnowledgeTool.getName());
    }
}
