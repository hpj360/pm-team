package com.redteam.profile.neo4j.service.impl;

import com.redteam.profile.neo4j.dto.Neo4jGraphDTO;
import com.redteam.profile.neo4j.dto.Neo4jGraphDTO.GraphEdge;
import com.redteam.profile.neo4j.dto.Neo4jGraphDTO.GraphNode;
import com.redteam.profile.neo4j.repository.Neo4jRelationRepository;
import com.redteam.profile.neo4j.service.Neo4jRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j 关系图谱服务实现
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>调用 {@link Neo4jRelationRepository} 执行 Cypher 多跳查询</li>
 *   <li>将 Neo4j 返回的 {@code Iterable<Map<String, Object>>} 转换为
 *       {@link Neo4jGraphDTO}，供前端 ECharts 力导向图直接渲染</li>
 *   <li>Neo4j 连接失败或查询异常时，自动降级到 Mock 数据，保证前端图谱可用</li>
 * </ul>
 *
 * <p>降级策略：当 Repository 抛出任何异常时，返回以根目标为中心的占位图谱，
 * 包含根目标节点 + 两个示例关联节点 + 两条示例边，日志记录降级原因。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jRelationServiceImpl implements Neo4jRelationService {

    /**
     * 默认关系展开深度
     */
    private static final int DEFAULT_DEPTH = 1;

    /**
     * 最大关系展开深度，避免图谱过大影响前端渲染性能
     */
    private static final int MAX_DEPTH = 3;

    /**
     * 基础节点大小（ECharts symbolSize 基准值）
     */
    private static final int BASE_SYMBOL_SIZE = 30;

    /**
     * 每条关联增加的节点大小
     */
    private static final int SYMBOL_SIZE_PER_DEGREE = 8;

    /**
     * 节点大小上限
     */
    private static final int MAX_SYMBOL_SIZE = 100;

    /**
     * 默认关系权重
     */
    private static final double DEFAULT_EDGE_WEIGHT = 0.5;

    /**
     * Cypher 返回结果中节点列表的键名
     */
    private static final String KEY_NODES = "nodes";

    /**
     * Cypher 返回结果中关系列表的键名
     */
    private static final String KEY_RELS = "rels";

    /**
     * 节点 Map 中的 ID 键名
     */
    private static final String KEY_NODE_ID = "nodeId";

    /**
     * 节点 Map 中的名称键名
     */
    private static final String KEY_NODE_NAME = "name";

    /**
     * 节点 Map 中的类型键名
     */
    private static final String KEY_NODE_TYPE = "nodeType";

    /**
     * 关系 Map 中的源节点 ID 键名
     */
    private static final String KEY_REL_SOURCE = "source";

    /**
     * 关系 Map 中的目标节点 ID 键名
     */
    private static final String KEY_REL_TARGET = "target";

    /**
     * 关系 Map 中的关系类型键名
     */
    private static final String KEY_REL_TYPE = "relationType";

    private final Neo4jRelationRepository neo4jRelationRepository;

    /**
     * 查询目标的多跳关系图谱
     *
     * <p>流程：</p>
     * <ol>
     *   <li>校验并钳制 depth 到 [1, 3] 区间</li>
     *   <li>调用 Repository 执行 Cypher 多跳查询</li>
     *   <li>将返回的 Map 结构转换为 {@link Neo4jGraphDTO}</li>
     *   <li>任何异常触发降级，返回 Mock 图谱</li>
     * </ol>
     *
     * @param targetId 根目标ID
     * @param depth    关系展开深度（1-3），null 时使用默认值 1
     * @return 关系图谱 DTO
     */
    @Override
    public Neo4jGraphDTO getRelationGraph(Long targetId, Integer depth) {
        int actualDepth = clampDepth(depth);
        log.info("查询 Neo4j 关系图谱: targetId={}, depth={}", targetId, actualDepth);

        if (targetId == null) {
            log.warn("targetId 为空，返回空图谱");
            return emptyGraph();
        }

        try {
            Iterable<Map<String, Object>> rows = neo4jRelationRepository
                    .findRelationsByTargetId(targetId, actualDepth);
            Neo4jGraphDTO graph = convertToGraph(rows, targetId);
            log.info("Neo4j 关系图谱查询成功: targetId={}, nodes={}, edges={}",
                    targetId,
                    graph.getNodes() != null ? graph.getNodes().size() : 0,
                    graph.getEdges() != null ? graph.getEdges().size() : 0);
            return graph;
        } catch (Exception e) {
            log.warn("Neo4j 查询失败，降级到 Mock 数据: targetId={}", targetId, e);
            return buildMockGraph(targetId);
        }
    }

    /**
     * 将 Cypher 返回的结果集合转换为 {@link Neo4jGraphDTO}
     *
     * <p>Cypher 查询返回的每一行是一个 Map，包含 "nodes" 和 "rels" 两个键。
     * 本方法合并所有行的节点与边，按节点 ID 去重，并计算每个节点的度数用于 symbolSize。</p>
     *
     * @param rows     Cypher 返回的结果迭代器
     * @param targetId 根目标ID（用于补充根节点，当 Cypher 无结果时）
     * @return 关系图谱 DTO
     */
    @SuppressWarnings("unchecked")
    private Neo4jGraphDTO convertToGraph(Iterable<Map<String, Object>> rows, Long targetId) {
        Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
        List<GraphEdge> edges = new ArrayList<>();

        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                // 收集节点
                Object nodesValue = row.get(KEY_NODES);
                if (nodesValue instanceof Collection) {
                    for (Object nodeObj : (Collection<Object>) nodesValue) {
                        if (!(nodeObj instanceof Map)) {
                            continue;
                        }
                        Map<String, Object> nodeMapRow = (Map<String, Object>) nodeObj;
                        GraphNode node = toGraphNode(nodeMapRow);
                        if (node != null && node.getId() != null) {
                            nodeMap.putIfAbsent(node.getId(), node);
                        }
                    }
                }
                // 收集边
                Object relsValue = row.get(KEY_RELS);
                if (relsValue instanceof Collection) {
                    for (Object relObj : (Collection<Object>) relsValue) {
                        if (!(relObj instanceof Map)) {
                            continue;
                        }
                        Map<String, Object> relMap = (Map<String, Object>) relObj;
                        GraphEdge edge = toGraphEdge(relMap);
                        if (edge != null) {
                            edges.add(edge);
                        }
                    }
                }
            }
        }

        // Cypher 无结果时，补充根节点避免空图
        if (nodeMap.isEmpty()) {
            log.info("Neo4j 查询无关联节点，返回仅含根目标的图谱: targetId={}", targetId);
            GraphNode root = new GraphNode();
            root.setId(String.valueOf(targetId));
            root.setName("目标-" + targetId);
            root.setNodeType("Target");
            root.setCategory(mapNodeCategory("Target"));
            root.setSymbolSize(BASE_SYMBOL_SIZE);
            root.setValue(0);
            root.setRawId(targetId);
            nodeMap.put(root.getId(), root);
        }

        // 计算每个节点的度数，更新 symbolSize
        Map<String, Integer> degreeMap = new HashMap<>();
        for (GraphEdge edge : edges) {
            degreeMap.merge(edge.getSource(), 1, Integer::sum);
            degreeMap.merge(edge.getTarget(), 1, Integer::sum);
        }
        for (GraphNode node : nodeMap.values()) {
            int degree = degreeMap.getOrDefault(node.getId(), 0);
            node.setValue(degree);
            node.setSymbolSize(Math.min(MAX_SYMBOL_SIZE,
                    BASE_SYMBOL_SIZE + degree * SYMBOL_SIZE_PER_DEGREE));
        }

        Neo4jGraphDTO graph = new Neo4jGraphDTO();
        graph.setNodes(new ArrayList<>(nodeMap.values()));
        graph.setEdges(edges);
        return graph;
    }

    /**
     * 将 Cypher 返回的节点 Map 转换为 {@link GraphNode}
     *
     * @param raw 节点 Map（包含 nodeId / name / nodeType 键）
     * @return 图谱节点，若缺少必要字段返回 null
     */
    private GraphNode toGraphNode(Map<String, Object> raw) {
        Object nodeIdRaw = raw.get(KEY_NODE_ID);
        if (nodeIdRaw == null) {
            return null;
        }
        GraphNode node = new GraphNode();
        String nodeId = String.valueOf(nodeIdRaw);
        node.setId(nodeId);
        node.setName(asString(raw.get(KEY_NODE_NAME), "Unknown"));
        String nodeType = asString(raw.get(KEY_NODE_TYPE), "Unknown");
        node.setNodeType(nodeType);
        node.setCategory(mapNodeCategory(nodeType));
        node.setSymbolSize(BASE_SYMBOL_SIZE);
        node.setValue(0);
        node.setRawId(asLong(nodeIdRaw));
        return node;
    }

    /**
     * 将 Cypher 返回的关系 Map 转换为 {@link GraphEdge}
     *
     * @param raw 关系 Map（包含 source / target / relationType 键）
     * @return 图谱边，若缺少必要字段返回 null
     */
    private GraphEdge toGraphEdge(Map<String, Object> raw) {
        Object sourceRaw = raw.get(KEY_REL_SOURCE);
        Object targetRaw = raw.get(KEY_REL_TARGET);
        if (sourceRaw == null || targetRaw == null) {
            return null;
        }
        GraphEdge edge = new GraphEdge();
        edge.setSource(String.valueOf(sourceRaw));
        edge.setTarget(String.valueOf(targetRaw));
        edge.setRelationType(asString(raw.get(KEY_REL_TYPE), "RELATED"));
        edge.setWeight(DEFAULT_EDGE_WEIGHT);
        return edge;
    }

    /**
     * 构建 Mock 降级图谱
     *
     * <p>当 Neo4j 不可用时，返回以根目标为中心的占位图谱：
     * 根目标节点 + 两个示例关联节点 + 两条示例边，
     * 保证前端 ECharts 图谱组件能正常渲染。</p>
     *
     * @param targetId 根目标ID
     * @return Mock 关系图谱
     */
    private Neo4jGraphDTO buildMockGraph(Long targetId) {
        String rootId = String.valueOf(targetId);

        GraphNode root = new GraphNode();
        root.setId(rootId);
        root.setName("目标-" + targetId + "（Mock）");
        root.setNodeType("Target");
        root.setCategory(mapNodeCategory("Target"));
        root.setSymbolSize(MAX_SYMBOL_SIZE);
        root.setValue(2);
        root.setRawId(targetId);

        GraphNode fileNode = new GraphNode();
        fileNode.setId(targetId + "-file-1");
        fileNode.setName("关联文件（Mock）");
        fileNode.setNodeType("File");
        fileNode.setCategory(mapNodeCategory("File"));
        fileNode.setSymbolSize(BASE_SYMBOL_SIZE + SYMBOL_SIZE_PER_DEGREE);
        fileNode.setValue(1);

        GraphNode iocNode = new GraphNode();
        iocNode.setId(targetId + "-ioc-1");
        iocNode.setName("关联IOC（Mock）");
        iocNode.setNodeType("Ioc");
        iocNode.setCategory(mapNodeCategory("Ioc"));
        iocNode.setSymbolSize(BASE_SYMBOL_SIZE);
        iocNode.setValue(1);

        GraphEdge edge1 = new GraphEdge();
        edge1.setSource(rootId);
        edge1.setTarget(fileNode.getId());
        edge1.setRelationType("CONTAINS");
        edge1.setWeight(DEFAULT_EDGE_WEIGHT);

        GraphEdge edge2 = new GraphEdge();
        edge2.setSource(fileNode.getId());
        edge2.setTarget(iocNode.getId());
        edge2.setRelationType("CONTAINS");
        edge2.setWeight(DEFAULT_EDGE_WEIGHT);

        Neo4jGraphDTO graph = new Neo4jGraphDTO();
        graph.setNodes(List.of(root, fileNode, iocNode));
        graph.setEdges(List.of(edge1, edge2));
        return graph;
    }

    /**
     * 返回空图谱
     *
     * @return 空的 Neo4jGraphDTO
     */
    private Neo4jGraphDTO emptyGraph() {
        Neo4jGraphDTO graph = new Neo4jGraphDTO();
        graph.setNodes(new ArrayList<>());
        graph.setEdges(new ArrayList<>());
        return graph;
    }

    /**
     * 钳制 depth 到 [1, 3] 区间
     *
     * @param depth 原始深度值
     * @return 钳制后的深度值
     */
    private int clampDepth(Integer depth) {
        if (depth == null) {
            return DEFAULT_DEPTH;
        }
        return Math.min(Math.max(1, depth), MAX_DEPTH);
    }

    /**
     * 将节点类型（Neo4j 标签）映射为可读类别名
     *
     * @param nodeType 节点类型（Target/File/Ioc/Vuln/AttackChain）
     * @return 可读类别名
     */
    private String mapNodeCategory(String nodeType) {
        if (nodeType == null) {
            return "未知";
        }
        return switch (nodeType) {
            case "Target" -> "目标";
            case "File" -> "文件";
            case "Ioc" -> "威胁指标";
            case "Vuln" -> "漏洞";
            case "AttackChain" -> "攻击链";
            default -> nodeType;
        };
    }

    /**
     * 安全转换为字符串
     *
     * @param value        原始值
     * @param defaultValue 默认值
     * @return 字符串
     */
    private String asString(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String str = String.valueOf(value);
        return str.isEmpty() ? defaultValue : str;
    }

    /**
     * 安全转换为 Long
     *
     * @param value 原始值
     * @return Long 值，转换失败返回 null
     */
    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
