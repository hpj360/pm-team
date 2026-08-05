package com.redteam.profile.service;

import com.redteam.profile.service.GraphAlgorithmService.AlgorithmInfo;
import com.redteam.profile.service.GraphAlgorithmService.AlgorithmResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link GraphAlgorithmService} 单元测试
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>算法清单：始终返回 4 个算法</li>
 *   <li>降级路径：GDS 禁用 / Cypher 异常时返回 success=false</li>
 *   <li>正常路径：PageRank / 社区发现 / 度中心性 / 最短路径（mock Neo4jClient 流式链）</li>
 *   <li>参数校验：未知算法 / 最短路径缺少 sourceId、targetId</li>
 * </ul>
 *
 * <p>使用 Mockito 隔离 {@link Neo4jClient}，模拟 {@code query().bindAll().fetch().all()} 流式调用。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("图算法服务测试")
class GraphAlgorithmServiceTest {

    @Mock
    private Neo4jClient neo4jClient;

    @InjectMocks
    private GraphAlgorithmService graphAlgorithmService;

    /**
     * 设置 gdsEnabled 字段（@Value 注入字段，单测中通过反射写入）
     *
     * @param enabled 是否启用 GDS
     */
    private void enableGds(boolean enabled) {
        ReflectionTestUtils.setField(graphAlgorithmService, "gdsEnabled", enabled);
    }

    /**
     * 模拟 Neo4jClient 流式调用链
     *
     * <p>根据 Cypher 关键字匹配返回不同结果集；throwKeywords 中的关键字在 {@code query()} 阶段抛异常，
     * 用于覆盖删除图投影的 try-catch 与算法异常降级路径。</p>
     *
     * @param cypherResults 关键字 → 结果集映射
     * @param throwKeywords 触发抛异常的关键字集合
     */
    @SuppressWarnings("unchecked")
    private void mockNeo4j(Map<String, Collection<Map<String, Object>>> cypherResults,
                           Set<String> throwKeywords) {
        when(neo4jClient.query(anyString())).thenAnswer(invocation -> {
            String cypher = invocation.getArgument(0);
            for (String kw : throwKeywords) {
                if (cypher.contains(kw)) {
                    throw new RuntimeException("GDS 调用失败: " + kw);
                }
            }
            Neo4jClient.UnboundRunnableSpec spec = Mockito.mock(Neo4jClient.UnboundRunnableSpec.class);
            Neo4jClient.RecordFetchSpec<Map<String, Object>> fetchSpec =
                    Mockito.mock(Neo4jClient.RecordFetchSpec.class);
            when(spec.bindAll(anyMap())).thenReturn(spec);
            when(spec.fetch()).thenReturn(fetchSpec);

            Collection<Map<String, Object>> result = Collections.emptyList();
            for (Map.Entry<String, Collection<Map<String, Object>>> e : cypherResults.entrySet()) {
                if (cypher.contains(e.getKey())) {
                    result = e.getValue();
                    break;
                }
            }
            when(fetchSpec.all()).thenReturn(result);
            return spec;
        });
    }

    // ==================== 算法清单 ====================

    @Test
    @DisplayName("listAvailableAlgorithms 应返回 4 个算法")
    void testListAvailableAlgorithms() {
        List<AlgorithmInfo> list = graphAlgorithmService.listAvailableAlgorithms();

        assertNotNull(list);
        assertEquals(4, list.size());

        List<String> codes = list.stream().map(AlgorithmInfo::getCode).toList();
        assertTrue(codes.contains("pagerank"));
        assertTrue(codes.contains("community"));
        assertTrue(codes.contains("shortestpath"));
        assertTrue(codes.contains("centrality"));

        // 验证每个算法信息字段完整
        for (AlgorithmInfo info : list) {
            assertNotNull(info.getCode());
            assertNotNull(info.getName());
            assertNotNull(info.getDescription());
            assertNotNull(info.getRequiredParams());
        }
    }

    // ==================== 降级路径 ====================

    @Test
    @DisplayName("GDS 禁用时 executeAlgorithm 应返回降级结果")
    void testExecuteAlgorithm_GdsDisabled() {
        enableGds(false);

        AlgorithmResult result = graphAlgorithmService.executeAlgorithm("pagerank", null);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未启用"));
        assertEquals("pagerank", result.getAlgorithm());
        assertTrue(result.getNodes().isEmpty());
    }

    @Test
    @DisplayName("Cypher 异常时应返回降级结果")
    void testExecuteAlgorithm_CypherException() {
        enableGds(true);
        // drop 抛异常（被忽略），pageRank 抛异常（触发降级）
        mockNeo4j(Collections.emptyMap(), Set.of("gds.graph.drop", "gds.pageRank"));

        AlgorithmResult result = graphAlgorithmService.executeAlgorithm("pagerank", null);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertEquals("GDS 插件未安装或未启用", result.getMessage());
        assertTrue(result.getNodes().isEmpty());
    }

    // ==================== 未知算法 ====================

    @Test
    @DisplayName("未知算法应返回错误结果")
    void testExecuteAlgorithm_UnknownAlgorithm() {
        enableGds(true);

        AlgorithmResult result = graphAlgorithmService.executeAlgorithm("unknownAlgo", null);

        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("未知算法"));
    }

    // ==================== PageRank ====================

    @Test
    @DisplayName("PageRank 执行应返回排名节点与汇总信息")
    void testExecuteAlgorithm_PageRank() {
        enableGds(true);
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("name", "目标A");
        r1.put("score", 1.5);
        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("name", "文件X");
        r2.put("score", 0.8);
        Collection<Map<String, Object>> rows = List.of(r1, r2);

        mockNeo4j(Map.of("gds.pageRank", rows), Set.of("gds.graph.drop"));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", 10);
        AlgorithmResult result = graphAlgorithmService.executeAlgorithm("pagerank", params);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(2, result.getNodes().size());
        assertEquals(1.5, result.getNodes().get(0).get("score"));
        assertEquals(2, result.getSummary().get("count"));
        assertEquals(1.5, result.getSummary().get("topScore"));
    }

    // ==================== 社区发现 ====================

    @Test
    @DisplayName("社区发现应返回节点社区归属与社区数量")
    void testExecuteAlgorithm_CommunityDetection() {
        enableGds(true);
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("name", "目标A");
        r1.put("communityId", 1);
        Map<String, Object> r2 = new LinkedHashMap<>();
        r2.put("name", "文件X");
        r2.put("communityId", 1);
        Map<String, Object> r3 = new LinkedHashMap<>();
        r3.put("name", "IOC-1");
        r3.put("communityId", 2);
        Collection<Map<String, Object>> rows = List.of(r1, r2, r3);

        mockNeo4j(Map.of("gds.louvain", rows), Set.of("gds.graph.drop"));

        AlgorithmResult result = graphAlgorithmService.executeAlgorithm("community", null);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(3, result.getNodes().size());
        assertEquals(3, result.getSummary().get("count"));
        assertEquals(2L, result.getSummary().get("communityCount"));
    }

    // ==================== 度中心性 ====================

    @Test
    @DisplayName("度中心性应返回度数排名与汇总信息")
    void testExecuteAlgorithm_DegreeCentrality() {
        enableGds(true);
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("name", "目标A");
        r1.put("score", 5.0);
        Collection<Map<String, Object>> rows = List.of(r1);

        mockNeo4j(Map.of("gds.degree", rows), Set.of("gds.graph.drop"));

        AlgorithmResult result = graphAlgorithmService.executeAlgorithm("centrality", null);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(1, result.getNodes().size());
        assertEquals(5.0, result.getSummary().get("topScore"));
    }

    // ==================== 最短路径 ====================

    @Nested
    @DisplayName("最短路径")
    class ShortestPathTests {

        @Test
        @DisplayName("最短路径应返回总成本与路径长度")
        void testExecuteAlgorithm_ShortestPath() {
            enableGds(true);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("totalCost", 5.0);
            row.put("path", List.of("节点A", "节点B", "节点C"));
            Collection<Map<String, Object>> rows = List.of(row);

            mockNeo4j(Map.of("gds.shortestPath.dijkstra", rows), Set.of("gds.graph.drop"));

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sourceId", 1L);
            params.put("targetId", 2L);
            AlgorithmResult result = graphAlgorithmService.executeAlgorithm("shortestpath", params);

            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(1, result.getNodes().size());
            assertEquals(5.0, result.getSummary().get("totalCost"));
            assertEquals(3, result.getSummary().get("pathLength"));
        }

        @Test
        @DisplayName("最短路径缺少 sourceId / targetId 应返回错误结果")
        void testExecuteAlgorithm_ShortestPath_MissingParams() {
            enableGds(true);

            AlgorithmResult result = graphAlgorithmService.executeAlgorithm("shortestpath", null);

            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertTrue(result.getMessage().contains("sourceId"));
        }
    }

    // ==================== 参数健壮性 ====================

    @Test
    @DisplayName("limit 为字符串时应被正确解析")
    void testExecuteAlgorithm_LimitAsString() {
        enableGds(true);
        Collection<Map<String, Object>> rows = List.of();

        mockNeo4j(Map.of("gds.pageRank", rows), Set.of("gds.graph.drop"));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("limit", "5");
        AlgorithmResult result = graphAlgorithmService.executeAlgorithm("pagerank", params);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals(0, result.getNodes().size());
        assertEquals(0.0, result.getSummary().get("topScore"));
    }
}
