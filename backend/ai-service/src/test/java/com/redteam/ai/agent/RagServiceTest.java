package com.redteam.ai.agent;

import com.redteam.ai.entity.KnowledgeEntity;
import com.redteam.ai.mapper.KnowledgeMapper;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RagService} 单元测试
 *
 * <p>覆盖索引、检索、删除、降级四类场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RagServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private KnowledgeMapper knowledgeMapper;

    @InjectMocks
    private RagService ragService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ragService, "searchServiceUrl", "http://localhost:8081");
    }

    /**
     * 用例 1: indexDocument 正常索引 - 应落库并调用 search-service 向量索引
     */
    @Test
    @DisplayName("indexDocument_Success - 正常索引应落库并调用向量索引")
    void testIndexDocument_Success() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("title", "ATT&CK T1059");
        metadata.put("source", "ATT&CK");

        String knowledgeId = ragService.indexDocument(null, "命令行执行技术描述", metadata);

        assertNotNull(knowledgeId);
        verify(knowledgeMapper).insert(any(KnowledgeEntity.class));
        verify(restTemplate).postForObject(eq("http://localhost:8081/api/search/knowledge/index"),
                any(), eq(String.class));
    }

    /**
     * 用例 2: indexDocument 向量索引失败降级 - search-service 不可用时不阻塞落库
     */
    @Test
    @DisplayName("indexDocument_VectorIndexDown - 向量索引失败应降级不阻塞")
    void testIndexDocument_VectorIndexDown() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("search-service 连接拒绝"));

        String knowledgeId = ragService.indexDocument("k-001", "测试内容", null);

        assertNotNull(knowledgeId);
        verify(knowledgeMapper).insert(any(KnowledgeEntity.class));
    }

    /**
     * 用例 3: search 正常检索 - search-service 返回结果
     */
    @Test
    @DisplayName("search_Success - 向量检索应返回结果列表")
    void testSearch_Success() throws Exception {
        String mockResponse = "[{\"knowledgeId\":\"k1\",\"title\":\"T1059\",\"content\":\"命令执行\",\"score\":0.95}]";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

        List<Map<String, Object>> results = ragService.search("命令执行", 5);

        assertEquals(1, results.size());
        assertEquals("k1", results.get(0).get("knowledgeId"));
        assertEquals(0.95, results.get(0).get("score"));
    }

    /**
     * 用例 4: search 降级 - search-service 不可用时本地关键词匹配
     */
    @Test
    @DisplayName("search_Degraded - 服务不可用时应降级为本地关键词匹配")
    void testSearch_Degraded() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("service down"));
        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setKnowledgeId("k2");
        entity.setTitle("测试");
        entity.setContent("这是一段包含APT28钓鱼攻击的描述");
        entity.setSource("REPORT");
        when(knowledgeMapper.selectAllOrderByCreatedAtDesc()).thenReturn(Arrays.asList(entity));

        List<Map<String, Object>> results = ragService.search("APT28", 5);

        assertEquals(1, results.size());
        assertEquals("k2", results.get(0).get("knowledgeId"));
    }

    /**
     * 用例 5: search 空查询应返回空列表
     */
    @Test
    @DisplayName("search_EmptyQuery - 空查询应返回空列表")
    void testSearch_EmptyQuery() {
        List<Map<String, Object>> results = ragService.search("", 5);
        assertTrue(results.isEmpty());
    }

    /**
     * 用例 6: deleteKnowledge 正常删除
     */
    @Test
    @DisplayName("deleteKnowledge_Success - 应删除数据库记录与向量索引")
    void testDeleteKnowledge_Success() {
        boolean result = ragService.deleteKnowledge("k-001");

        assertTrue(result);
        verify(knowledgeMapper).deleteById(any(KnowledgeEntity.class));
        verify(restTemplate).delete(eq("http://localhost:8081/api/search/knowledge/k-001"));
    }

    /**
     * 用例 7: listAll 正常返回列表
     */
    @Test
    @DisplayName("listAll_Success - 应返回知识库文档列表")
    void testListAll_Success() {
        KnowledgeEntity e = new KnowledgeEntity();
        e.setKnowledgeId("k1");
        when(knowledgeMapper.selectAllOrderByCreatedAtDesc()).thenReturn(Collections.singletonList(e));

        List<KnowledgeEntity> list = ragService.listAll();

        assertEquals(1, list.size());
        assertEquals("k1", list.get(0).getKnowledgeId());
    }
}
