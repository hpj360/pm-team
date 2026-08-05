package com.redteam.ai.service;

import com.redteam.ai.client.LlmClient;
import com.redteam.ai.vo.NlSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link NaturalLanguageSearchService} 单元测试
 *
 * <p>使用 Mockito 模拟 {@link LlmClient} 和 {@link RestTemplate}，覆盖以下场景：</p>
 * <ol>
 *   <li>LLM 正常解析 + search-service 正常返回</li>
 *   <li>LLM 不可用（返回 null）降级为关键词搜索</li>
 *   <li>LLM 响应 JSON 解析失败降级为关键词搜索</li>
 *   <li>search-service 不可用返回空结果 + 错误信息</li>
 *   <li>仅解析自然语言为搜索条件（不执行搜索）</li>
 * </ol>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("自然语言搜索服务测试")
class NaturalLanguageSearchServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private RestTemplate restTemplate;

    private NaturalLanguageSearchService service;

    @BeforeEach
    void setUp() {
        service = new NaturalLanguageSearchService();
        // 注入 Mock 依赖和配置值
        ReflectionTestUtils.setField(service, "llmClient", llmClient);
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(service, "searchServiceUrl", "http://localhost:8083");
        ReflectionTestUtils.setField(service, "searchServicePath", "/search");
    }

    // ==================== 测试数据构造 ====================

    /**
     * 构造 LLM 正常返回的 JSON 响应
     */
    private String buildLlmResponse(String keyword, String fileType) {
        return "{\"keyword\":\"" + keyword + "\",\"searchMode\":\"keyword\",\"fileType\":\""
                + fileType + "\",\"tagIds\":[],\"booleanConditions\":[]}";
    }

    /**
     * 构造 search-service 的成功响应
     */
    private ResponseEntity<Map> buildSearchResponse(long total, List<Map<String, Object>> hits) {
        Map<String, Object> data = new HashMap<>();
        data.put("total", total);
        data.put("hits", hits);
        data.put("pageNum", 1);
        data.put("pageSize", 10);

        Map<String, Object> body = new HashMap<>();
        body.put("code", 200);
        body.put("message", "成功");
        body.put("data", data);

        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    // ==================== 测试用例 ====================

    /**
     * 测试 1: LLM 正常解析 + 搜索执行
     *
     * <p>LLM 返回有效的 JSON 条件，search-service 正常返回结果。
     * 验证 llmUsed=true、结果不为空、无错误信息。</p>
     */
    @Test
    @DisplayName("search - LLM 正常解析 + 搜索执行成功")
    void testSearch_Success() {
        String query = "查找所有包含 APT28 相关 IP 的 PDF 文件";
        String llmResponse = buildLlmResponse("APT28", "pdf");
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        List<Map<String, Object>> hits = List.of(
                Map.of("fileId", 1, "fileName", "apt28_report.pdf"),
                Map.of("fileId", 2, "fileName", "apt28_ioc.pdf")
        );
        ResponseEntity<Map> response = buildSearchResponse(2L, hits);
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(response);

        NlSearchResult result = service.search(query);

        // 验证 LLM 被成功使用
        assertTrue(result.isLlmUsed(), "LLM 应被成功使用");
        // 验证解析条件
        assertNotNull(result.getParsedConditions());
        assertEquals("APT28", result.getParsedConditions().get("keyword"));
        assertEquals("pdf", result.getParsedConditions().get("fileType"));
        // 验证搜索结果
        assertEquals(2L, result.getTotal(), "命中总数应为 2");
        assertNotNull(result.getResults(), "结果列表不应为 null");
        assertEquals(2, result.getResults().size(), "结果数量应为 2");
        // 验证无错误信息
        assertNull(result.getErrorMessage(), "不应有错误信息");
        // 验证原始输入
        assertEquals(query, result.getNaturalLanguageQuery());

        verify(llmClient).chat(anyString(), anyString());
        verify(restTemplate).postForEntity(anyString(), any(), eq(Map.class));
    }

    /**
     * 测试 2: LLM 不可用降级为关键词搜索
     *
     * <p>LLM 返回 null（服务不可用），降级为将自然语言直接作为 keyword。
     * 验证 llmUsed=false、降级条件正确、搜索仍执行。</p>
     */
    @Test
    @DisplayName("search - LLM 不可用时降级为关键词搜索")
    void testSearch_LlmUnavailable() {
        String query = "查找APT28相关文件";
        when(llmClient.chat(anyString(), anyString())).thenReturn(null);

        List<Map<String, Object>> hits = List.of(
                Map.of("fileId", 1, "fileName", "apt28.pdf")
        );
        ResponseEntity<Map> response = buildSearchResponse(1L, hits);
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(response);

        NlSearchResult result = service.search(query);

        // 验证 LLM 未被成功使用
        assertFalse(result.isLlmUsed(), "LLM 不可用时 llmUsed 应为 false");
        // 验证降级条件：自然语言直接作为 keyword
        assertNotNull(result.getParsedConditions());
        assertEquals(query, result.getParsedConditions().get("keyword"), "降级时应将自然语言作为 keyword");
        assertEquals("keyword", result.getParsedConditions().get("searchMode"));
        // 验证搜索仍执行
        assertEquals(1L, result.getTotal(), "搜索仍应返回结果");
        assertEquals(1, result.getResults().size());
        // 验证错误信息包含降级提示
        assertNotNull(result.getErrorMessage(), "应包含降级错误信息");
        assertTrue(result.getErrorMessage().contains("LLM"), "错误信息应提及 LLM 不可用");
    }

    /**
     * 测试 3: JSON 解析失败降级
     *
     * <p>LLM 返回非 JSON 内容，解析失败后降级为关键词搜索。
     * 验证 llmUsed=false、降级条件正确、搜索仍执行。</p>
     */
    @Test
    @DisplayName("search - LLM 响应 JSON 解析失败时降级")
    void testSearch_JsonParseError() {
        String query = "查找恶意IP相关文件";
        // LLM 返回非 JSON 文本
        when(llmClient.chat(anyString(), anyString())).thenReturn("抱歉，我无法解析这个请求。这不是一个有效的 JSON 格式。");

        ResponseEntity<Map> response = buildSearchResponse(0L, Collections.emptyList());
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(response);

        NlSearchResult result = service.search(query);

        // 验证 LLM 未被成功使用
        assertFalse(result.isLlmUsed(), "JSON 解析失败时 llmUsed 应为 false");
        // 验证降级条件
        assertNotNull(result.getParsedConditions());
        assertEquals(query, result.getParsedConditions().get("keyword"), "降级时应将自然语言作为 keyword");
        // 验证搜索仍执行
        assertEquals(0L, result.getTotal());
        assertNotNull(result.getResults());
        assertTrue(result.getResults().isEmpty());
        // 验证错误信息包含解析失败提示
        assertNotNull(result.getErrorMessage(), "应包含解析失败错误信息");
        assertTrue(result.getErrorMessage().contains("JSON"), "错误信息应提及 JSON 解析失败");
    }

    /**
     * 测试 4: search-service 不可用返回空结果
     *
     * <p>LLM 正常解析，但 search-service 调用抛异常。
     * 验证返回空结果、错误信息包含 search-service 不可用提示。</p>
     */
    @Test
    @DisplayName("search - search-service 不可用时返回空结果")
    void testSearch_SearchServiceDown() {
        String query = "查找APT28";
        String llmResponse = buildLlmResponse("APT28", "");
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        // search-service 调用抛出连接异常
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        NlSearchResult result = service.search(query);

        // LLM 仍被成功使用
        assertTrue(result.isLlmUsed(), "LLM 解析仍应成功");
        // 验证返回空结果
        assertEquals(0L, result.getTotal(), "search-service 不可用时应返回 0 条");
        assertNotNull(result.getResults());
        assertTrue(result.getResults().isEmpty(), "结果列表应为空");
        // 验证错误信息
        assertNotNull(result.getErrorMessage(), "应包含错误信息");
        assertTrue(result.getErrorMessage().contains("search-service"), "错误信息应提及 search-service 不可用");
        // 验证解析条件仍存在
        assertNotNull(result.getParsedConditions());
        assertEquals("APT28", result.getParsedConditions().get("keyword"));
    }

    /**
     * 测试 5: 仅解析自然语言为搜索条件（不执行搜索）
     *
     * <p>验证 parseToSearchConditions 方法正确解析自然语言为结构化条件，
     * 且不调用 search-service。</p>
     */
    @Test
    @DisplayName("parseToSearchConditions - 仅解析不搜索")
    void testParseToSearchConditions() {
        String query = "查找所有包含 APT28 相关 IP 的 PDF 文件";
        String llmResponse = buildLlmResponse("APT28", "pdf");
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        Map<String, Object> conditions = service.parseToSearchConditions(query);

        // 验证解析条件
        assertNotNull(conditions);
        assertEquals("APT28", conditions.get("keyword"));
        assertEquals("keyword", conditions.get("searchMode"));
        assertEquals("pdf", conditions.get("fileType"));
        assertNotNull(conditions.get("tagIds"));
        assertTrue(((List<?>) conditions.get("tagIds")).isEmpty());
        assertNotNull(conditions.get("booleanConditions"));
        assertTrue(((List<?>) conditions.get("booleanConditions")).isEmpty());
        // 验证未调用 search-service
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Map.class));
    }

    /**
     * 测试 6: LLM 返回带 markdown 代码块的 JSON 也能正确解析
     *
     * <p>LLM 有时会将 JSON 包裹在 ```json ... ``` 中，验证 extractJson 能正确剥离。</p>
     */
    @Test
    @DisplayName("search - LLM 返回 markdown 代码块包裹的 JSON 也能解析")
    void testSearch_MarkdownWrappedJson() {
        String query = "查找CVE漏洞";
        String llmResponse = "```json\n{\"keyword\":\"CVE\",\"searchMode\":\"keyword\",\"fileType\":\"\",\"tagIds\":[],\"booleanConditions\":[]}\n```";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        ResponseEntity<Map> response = buildSearchResponse(0L, Collections.emptyList());
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(response);

        NlSearchResult result = service.search(query);

        assertTrue(result.isLlmUsed(), "markdown 包裹的 JSON 应能正常解析");
        assertEquals("CVE", result.getParsedConditions().get("keyword"));
        assertNull(result.getErrorMessage());
    }
}
