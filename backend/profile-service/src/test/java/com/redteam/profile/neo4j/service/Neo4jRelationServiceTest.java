package com.redteam.profile.neo4j.service;

import com.redteam.profile.neo4j.dto.Neo4jGraphDTO;
import com.redteam.profile.neo4j.dto.Neo4jGraphDTO.GraphEdge;
import com.redteam.profile.neo4j.dto.Neo4jGraphDTO.GraphNode;
import com.redteam.profile.neo4j.repository.Neo4jRelationRepository;
import com.redteam.profile.neo4j.service.impl.Neo4jRelationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link Neo4jRelationServiceImpl} 单元测试
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>正常路径：Cypher 返回节点+边，正确转换为 ECharts 格式</li>
 *   <li>空结果：Cypher 无匹配，返回仅含根目标的图谱</li>
 *   <li>降级路径：Repository 抛异常，自动降级到 Mock 数据</li>
 *   <li>参数校验：depth 钳制、targetId 为空</li>
 *   <li>节点度数计算与 symbolSize 映射</li>
 * </ul>
 *
 * <p>使用 Mockito 隔离 {@link Neo4jRelationRepository}，无需真实 Neo4j 实例。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Neo4j 关系图谱服务测试")
class Neo4jRelationServiceTest {

    @Mock
    private Neo4jRelationRepository neo4jRelationRepository;

    @InjectMocks
    private Neo4jRelationServiceImpl neo4jRelationService;

    // ==================== 正常路径 ====================

    @Nested
    @DisplayName("正常查询路径")
    class NormalPathTests {

        @Test
        @DisplayName("Cypher 返回节点+边时应正确转换为 ECharts 格式")
        void getRelationGraph_success() {
            // 准备 Cypher 返回数据：1 个 Target + 1 个 File + 1 条 CONTAINS 边
            Map<String, Object> row = buildCypherRow(
                    List.of(
                            buildNodeMap(1L, "目标A", "Target"),
                            buildNodeMap(101L, "文件X", "File")
                    ),
                    List.of(
                            buildRelMap(1L, 101L, "CONTAINS")
                    )
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            assertNotNull(result);
            assertEquals(2, result.getNodes().size());
            assertEquals(1, result.getEdges().size());

            // 验证节点
            GraphNode targetNode = result.getNodes().stream()
                    .filter(n -> "Target".equals(n.getNodeType()))
                    .findFirst().orElse(null);
            assertNotNull(targetNode);
            assertEquals("1", targetNode.getId());
            assertEquals("目标A", targetNode.getName());
            assertEquals("目标", targetNode.getCategory());
            assertEquals(1L, targetNode.getRawId());

            // 验证边
            GraphEdge edge = result.getEdges().get(0);
            assertEquals("1", edge.getSource());
            assertEquals("101", edge.getTarget());
            assertEquals("CONTAINS", edge.getRelationType());

            // 验证 Repository 被调用
            verify(neo4jRelationRepository).findRelationsByTargetId(1L, 1);
        }

        @Test
        @DisplayName("多跳查询应返回所有关联节点与边")
        void getRelationGraph_multiHop() {
            Map<String, Object> row = buildCypherRow(
                    List.of(
                            buildNodeMap(1L, "目标A", "Target"),
                            buildNodeMap(101L, "文件X", "File"),
                            buildNodeMap(201L, "IOC-1", "Ioc"),
                            buildNodeMap(301L, "CVE-2024-001", "Vuln")
                    ),
                    List.of(
                            buildRelMap(1L, 101L, "CONTAINS"),
                            buildRelMap(101L, 201L, "CONTAINS"),
                            buildRelMap(201L, 301L, "EXPLOITS")
                    )
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 3))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 3);

            assertEquals(4, result.getNodes().size());
            assertEquals(3, result.getEdges().size());

            // 验证类别映射
            Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
            for (GraphNode n : result.getNodes()) {
                nodeMap.put(n.getNodeType(), n);
            }
            assertEquals("目标", nodeMap.get("Target").getCategory());
            assertEquals("文件", nodeMap.get("File").getCategory());
            assertEquals("威胁指标", nodeMap.get("Ioc").getCategory());
            assertEquals("漏洞", nodeMap.get("Vuln").getCategory());
        }

        @Test
        @DisplayName("节点去重：多行返回相同节点时应合并去重")
        void getRelationGraph_deduplicateNodes() {
            Map<String, Object> row1 = buildCypherRow(
                    List.of(
                            buildNodeMap(1L, "目标A", "Target"),
                            buildNodeMap(101L, "文件X", "File")
                    ),
                    List.of(buildRelMap(1L, 101L, "CONTAINS"))
            );
            Map<String, Object> row2 = buildCypherRow(
                    List.of(
                            buildNodeMap(1L, "目标A", "Target"),
                            buildNodeMap(201L, "文件Y", "File")
                    ),
                    List.of(buildRelMap(1L, 201L, "CONTAINS"))
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 2))
                    .thenReturn(List.of(row1, row2));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 2);

            // 节点 1 出现两次应去重，最终 3 个节点
            assertEquals(3, result.getNodes().size());
            assertEquals(2, result.getEdges().size());
        }

        @Test
        @DisplayName("节点度数应正确计算并映射到 symbolSize")
        void getRelationGraph_degreeCalculation() {
            // 目标A 关联 文件X、文件Y、文件Z，度数=3
            Map<String, Object> row = buildCypherRow(
                    List.of(
                            buildNodeMap(1L, "目标A", "Target"),
                            buildNodeMap(101L, "文件X", "File"),
                            buildNodeMap(102L, "文件Y", "File"),
                            buildNodeMap(103L, "文件Z", "File")
                    ),
                    List.of(
                            buildRelMap(1L, 101L, "CONTAINS"),
                            buildRelMap(1L, 102L, "CONTAINS"),
                            buildRelMap(1L, 103L, "CONTAINS")
                    )
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            GraphNode targetNode = result.getNodes().stream()
                    .filter(n -> "Target".equals(n.getNodeType()))
                    .findFirst().orElse(null);
            assertNotNull(targetNode);
            assertEquals(3, targetNode.getValue());
            // symbolSize = 30 + 3 * 8 = 54
            assertEquals(54, targetNode.getSymbolSize());
        }

        @Test
        @DisplayName("节点度数超过上限时 symbolSize 应被截断为 100")
        void getRelationGraph_symbolSizeCapped() {
            // 目标A 关联 10 个文件，度数=10
            List<Map<String, Object>> nodes = new ArrayList<>();
            nodes.add(buildNodeMap(1L, "目标A", "Target"));
            List<Map<String, Object>> rels = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                nodes.add(buildNodeMap(100L + i, "文件" + i, "File"));
                rels.add(buildRelMap(1L, 100L + i, "CONTAINS"));
            }
            Map<String, Object> row = buildCypherRow(nodes, rels);
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            GraphNode targetNode = result.getNodes().stream()
                    .filter(n -> "Target".equals(n.getNodeType()))
                    .findFirst().orElse(null);
            assertNotNull(targetNode);
            assertEquals(10, targetNode.getValue());
            // symbolSize 应被截断为 100（30 + 10 * 8 = 110 > 100）
            assertEquals(100, targetNode.getSymbolSize());
        }
    }

    // ==================== 空结果路径 ====================

    @Nested
    @DisplayName("空结果路径")
    class EmptyResultTests {

        @Test
        @DisplayName("Cypher 返回空迭代器时应返回仅含根目标的图谱")
        void getRelationGraph_emptyIterable() {
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(Collections.emptyList());

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            assertNotNull(result);
            assertEquals(1, result.getNodes().size());
            assertTrue(result.getEdges().isEmpty());

            GraphNode root = result.getNodes().get(0);
            assertEquals("1", root.getId());
            assertEquals("Target", root.getNodeType());
            assertEquals(0, root.getValue());
        }

        @Test
        @DisplayName("Cypher 返回 null 时应返回仅含根目标的图谱")
        void getRelationGraph_nullIterable() {
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(null);

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            assertNotNull(result);
            assertEquals(1, result.getNodes().size());
            assertEquals("1", result.getNodes().get(0).getId());
        }

        @Test
        @DisplayName("Cypher 行中 nodes 为空时应返回仅含根目标的图谱")
        void getRelationGraph_emptyNodesInRow() {
            Map<String, Object> row = new HashMap<>();
            row.put("nodes", Collections.emptyList());
            row.put("rels", Collections.emptyList());
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            assertNotNull(result);
            assertEquals(1, result.getNodes().size());
            assertTrue(result.getEdges().isEmpty());
        }
    }

    // ==================== 降级路径 ====================

    @Nested
    @DisplayName("Neo4j 降级路径")
    class FallbackTests {

        @Test
        @DisplayName("Repository 抛异常时应降级到 Mock 数据")
        void getRelationGraph_exceptionFallback() {
            when(neo4jRelationRepository.findRelationsByTargetId(anyLong(), anyInt()))
                    .thenThrow(new RuntimeException("Neo4j connection refused"));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            assertNotNull(result);
            // Mock 图谱：根目标 + 文件 + IOC = 3 节点
            assertEquals(3, result.getNodes().size());
            assertEquals(2, result.getEdges().size());

            // 验证根节点
            GraphNode root = result.getNodes().stream()
                    .filter(n -> "Target".equals(n.getNodeType()))
                    .findFirst().orElse(null);
            assertNotNull(root);
            assertEquals("1", root.getId());
            assertTrue(root.getName().contains("Mock"));
            assertEquals(2, root.getValue());

            // 验证边
            GraphEdge edge1 = result.getEdges().get(0);
            assertEquals("CONTAINS", edge1.getRelationType());
            assertEquals("1", edge1.getSource());
        }

        @Test
        @DisplayName("降级 Mock 数据中应包含文件和 IOC 节点")
        void getRelationGraph_mockContainsFileAndIoc() {
            when(neo4jRelationRepository.findRelationsByTargetId(anyLong(), anyInt()))
                    .thenThrow(new RuntimeException("Neo4j down"));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(42L, 2);

            assertNotNull(result);
            assertEquals(3, result.getNodes().size());
            Map<String, GraphNode> typeMap = new LinkedHashMap<>();
            for (GraphNode n : result.getNodes()) {
                typeMap.put(n.getNodeType(), n);
            }
            assertTrue(typeMap.containsKey("Target"));
            assertTrue(typeMap.containsKey("File"));
            assertTrue(typeMap.containsKey("Ioc"));
        }
    }

    // ==================== 参数校验 ====================

    @Nested
    @DisplayName("参数校验与钳制")
    class ParameterTests {

        @Test
        @DisplayName("depth 为 null 时应使用默认值 1")
        void getRelationGraph_nullDepth() {
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(Collections.emptyList());

            assertDoesNotThrow(() -> neo4jRelationService.getRelationGraph(1L, null));
            verify(neo4jRelationRepository).findRelationsByTargetId(1L, 1);
        }

        @Test
        @DisplayName("depth 超过 3 时应被截断为 3")
        void getRelationGraph_depthClampedToMax() {
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 3))
                    .thenReturn(Collections.emptyList());

            neo4jRelationService.getRelationGraph(1L, 99);
            verify(neo4jRelationRepository).findRelationsByTargetId(1L, 3);
        }

        @Test
        @DisplayName("depth 小于 1 时应被钳制为 1")
        void getRelationGraph_depthClampedToMin() {
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(Collections.emptyList());

            neo4jRelationService.getRelationGraph(1L, 0);
            verify(neo4jRelationRepository, org.mockito.Mockito.times(1)).findRelationsByTargetId(1L, 1);

            neo4jRelationService.getRelationGraph(1L, -5);
            verify(neo4jRelationRepository, org.mockito.Mockito.times(2)).findRelationsByTargetId(1L, 1);
        }

        @Test
        @DisplayName("targetId 为 null 时应返回空图谱，不调用 Repository")
        void getRelationGraph_nullTargetId() {
            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(null, 1);

            assertNotNull(result);
            assertTrue(result.getNodes().isEmpty());
            assertTrue(result.getEdges().isEmpty());
            verify(neo4jRelationRepository, org.mockito.Mockito.never())
                    .findRelationsByTargetId(anyLong(), anyInt());
        }
    }

    // ==================== 数据健壮性 ====================

    @Nested
    @DisplayName("数据健壮性")
    class RobustnessTests {

        @Test
        @DisplayName("节点缺少 nodeId 时应跳过该节点")
        void getRelationGraph_nodeMissingId() {
            Map<String, Object> badNode = new HashMap<>();
            badNode.put("name", "无ID节点");
            badNode.put("nodeType", "File");

            Map<String, Object> goodNode = buildNodeMap(1L, "目标A", "Target");

            Map<String, Object> row = buildCypherRow(
                    List.of(badNode, goodNode),
                    Collections.emptyList()
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            // 只有 goodNode 被保留
            assertEquals(1, result.getNodes().size());
            assertEquals("1", result.getNodes().get(0).getId());
        }

        @Test
        @DisplayName("边缺少 source 或 target 时应跳过该边")
        void getRelationGraph_edgeMissingEndpoints() {
            Map<String, Object> badEdge = new HashMap<>();
            badEdge.put("source", 1L);
            // 缺少 target

            Map<String, Object> goodEdge = buildRelMap(1L, 101L, "CONTAINS");

            Map<String, Object> row = buildCypherRow(
                    List.of(buildNodeMap(1L, "目标A", "Target"), buildNodeMap(101L, "文件X", "File")),
                    List.of(badEdge, goodEdge)
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            assertEquals(2, result.getNodes().size());
            assertEquals(1, result.getEdges().size());
            assertEquals("CONTAINS", result.getEdges().get(0).getRelationType());
        }

        @Test
        @DisplayName("节点名称为空时应使用 Unknown 兜底")
        void getRelationGraph_emptyName() {
            Map<String, Object> node = new HashMap<>();
            node.put("nodeId", 1L);
            node.put("name", "");
            node.put("nodeType", "Target");

            Map<String, Object> row = buildCypherRow(
                    List.of(node),
                    Collections.emptyList()
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            assertEquals("Unknown", result.getNodes().get(0).getName());
        }

        @Test
        @DisplayName("未知节点类型应原样保留 nodeType")
        void getRelationGraph_unknownNodeType() {
            Map<String, Object> row = buildCypherRow(
                    List.of(buildNodeMap(1L, "节点X", "CustomType")),
                    Collections.emptyList()
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            GraphNode node = result.getNodes().get(0);
            assertEquals("CustomType", node.getNodeType());
            assertEquals("CustomType", node.getCategory());
        }

        @Test
        @DisplayName("nodeId 为 Integer 类型时应正确转换为字符串")
        void getRelationGraph_integerNodeId() {
            Map<String, Object> node = new HashMap<>();
            node.put("nodeId", 1);  // Integer 而非 Long
            node.put("name", "目标A");
            node.put("nodeType", "Target");

            Map<String, Object> row = buildCypherRow(
                    List.of(node),
                    Collections.emptyList()
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(List.of(row));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            GraphNode graphNode = result.getNodes().get(0);
            assertEquals("1", graphNode.getId());
            assertEquals(1L, graphNode.getRawId());
        }

        @Test
        @DisplayName("行中包含 null 行应安全跳过")
        void getRelationGraph_nullRow() {
            Map<String, Object> goodRow = buildCypherRow(
                    List.of(buildNodeMap(1L, "目标A", "Target")),
                    Collections.emptyList()
            );
            when(neo4jRelationRepository.findRelationsByTargetId(1L, 1))
                    .thenReturn(java.util.Arrays.asList(null, goodRow, null));

            Neo4jGraphDTO result = neo4jRelationService.getRelationGraph(1L, 1);

            assertEquals(1, result.getNodes().size());
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 Cypher 返回的行 Map（包含 nodes 和 rels 两个键）
     *
     * @param nodes 节点列表
     * @param rels  关系列表
     * @return 行 Map
     */
    private Map<String, Object> buildCypherRow(List<Map<String, Object>> nodes,
                                               List<Map<String, Object>> rels) {
        Map<String, Object> row = new HashMap<>();
        row.put("nodes", nodes);
        row.put("rels", rels);
        return row;
    }

    /**
     * 构造 Cypher 返回的节点 Map
     *
     * @param id       节点ID
     * @param name     节点名称
     * @param nodeType 节点类型
     * @return 节点 Map
     */
    private Map<String, Object> buildNodeMap(Long id, String name, String nodeType) {
        Map<String, Object> node = new HashMap<>();
        node.put("nodeId", id);
        node.put("name", name);
        node.put("nodeType", nodeType);
        return node;
    }

    /**
     * 构造 Cypher 返回的关系 Map
     *
     * @param source       源节点ID
     * @param target       目标节点ID
     * @param relationType 关系类型
     * @return 关系 Map
     */
    private Map<String, Object> buildRelMap(Long source, Long target, String relationType) {
        Map<String, Object> rel = new HashMap<>();
        rel.put("source", source);
        rel.put("target", target);
        rel.put("relationType", relationType);
        return rel;
    }
}
