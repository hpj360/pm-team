package com.redteam.profile.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j GDS 图算法服务
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>列出可用的 GDS 图算法（PageRank / Louvain 社区发现 / Dijkstra 最短路径 / 度中心性）</li>
 *   <li>通过 {@link Neo4jClient} 执行 GDS Cypher 流式查询，返回节点结果与汇总信息</li>
 *   <li>GDS 未启用或调用异常时自动降级，返回 success=false 的降级结果，不影响现有功能</li>
 * </ul>
 *
 * <p>降级策略：</p>
 * <ol>
 *   <li>{@code neo4j.gds.enabled=false} 时，{@link #executeAlgorithm} 直接返回降级结果</li>
 *   <li>GDS 插件未安装或 Cypher 抛出任何异常时，捕获后返回降级结果，日志记录原因</li>
 *   <li>{@link #listAvailableAlgorithms} 始终返回 4 个算法，与 GDS 是否启用无关</li>
 * </ol>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class GraphAlgorithmService {

    /** 算法编码：PageRank */
    public static final String ALGO_PAGERANK = "pagerank";

    /** 算法编码：社区发现（Louvain） */
    public static final String ALGO_COMMUNITY = "community";

    /** 算法编码：最短路径（Dijkstra） */
    public static final String ALGO_SHORTESTPATH = "shortestpath";

    /** 算法编码：度中心性 */
    public static final String ALGO_CENTRALITY = "centrality";

    /** GDS 临时图投影名称（与 Cypher 中的 'target-graph' 对应） */
    private static final String GRAPH_NAME = "target-graph";

    /** 降级消息：GDS 未启用 */
    private static final String MSG_GDS_DISABLED = "GDS 插件未启用，请在配置中开启 neo4j.gds.enabled";

    /** 降级消息：GDS 未安装或调用异常 */
    private static final String MSG_GDS_NOT_INSTALLED = "GDS 插件未安装或未启用";

    /** 错误消息前缀：未知算法 */
    private static final String MSG_UNKNOWN_ALGORITHM = "未知算法：";

    /** 默认节点标签 */
    private static final String DEFAULT_NODE_LABEL = "Target";

    /** 默认关系类型（* 表示全部关系） */
    private static final String DEFAULT_RELATIONSHIP_TYPE = "*";

    /** 默认返回条数上限 */
    private static final int DEFAULT_LIMIT = 10;

    @Value("${neo4j.gds.enabled:false}")
    private boolean gdsEnabled;

    @Autowired
    private Neo4jClient neo4jClient;

    /**
     * 列出可用的图算法
     *
     * <p>始终返回 4 个算法（PageRank / 社区发现 / 最短路径 / 度中心性），
     * 与 GDS 是否启用无关，便于前端展示算法清单。</p>
     *
     * @return 算法信息列表
     */
    public List<AlgorithmInfo> listAvailableAlgorithms() {
        List<AlgorithmInfo> list = new ArrayList<>();
        list.add(buildInfo(ALGO_PAGERANK, "PageRank 页面排名",
                "基于关系结构评估节点重要性，score 越高表示节点影响力越大",
                List.of("nodeLabel")));
        list.add(buildInfo(ALGO_COMMUNITY, "Louvain 社区发现",
                "基于模块度优化发现图中的社区聚类结构，相同 communityId 的节点属于同一社区",
                List.of("nodeLabel")));
        list.add(buildInfo(ALGO_SHORTESTPATH, "Dijkstra 最短路径",
                "基于 Dijkstra 算法计算两节点间的最短路径及总成本",
                List.of("sourceId", "targetId")));
        list.add(buildInfo(ALGO_CENTRALITY, "Degree Centrality 度中心性",
                "统计每个节点的度数，评估节点在图中的连接中心性",
                List.of("nodeLabel")));
        return list;
    }

    /**
     * 执行图算法
     *
     * <p>流程：</p>
     * <ol>
     *   <li>检查 {@code gdsEnabled}，未启用直接返回降级结果</li>
     *   <li>按算法编码分发到对应实现，未知算法返回错误结果</li>
     *   <li>任何异常捕获后返回降级结果，保证接口可用</li>
     * </ol>
     *
     * @param algorithm 算法名称：pagerank / community / shortestpath / centrality
     * @param params    算法参数（nodeLabel, relationshipType, maxIterations, limit, sourceId, targetId 等）
     * @return 算法执行结果
     */
    public AlgorithmResult executeAlgorithm(String algorithm, Map<String, Object> params) {
        Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;

        if (!gdsEnabled) {
            log.warn("GDS 未启用，返回降级结果: algorithm={}", algorithm);
            return degradedResult(algorithm, MSG_GDS_DISABLED);
        }

        String algo = algorithm == null ? "" : algorithm.toLowerCase();
        try {
            switch (algo) {
                case ALGO_PAGERANK:
                    return executePageRank(safeParams);
                case ALGO_COMMUNITY:
                    return executeCommunityDetection(safeParams);
                case ALGO_SHORTESTPATH:
                    return executeShortestPath(safeParams);
                case ALGO_CENTRALITY:
                    return executeDegreeCentrality(safeParams);
                default:
                    log.warn("未知算法: algorithm={}", algorithm);
                    return errorResult(algorithm, MSG_UNKNOWN_ALGORITHM + algorithm);
            }
        } catch (Exception e) {
            log.warn("图算法执行异常，返回降级结果: algorithm={}", algorithm, e);
            return degradedResult(algorithm, MSG_GDS_NOT_INSTALLED);
        }
    }

    // ==================== 算法实现 ====================

    /**
     * PageRank：评估节点重要性
     */
    private AlgorithmResult executePageRank(Map<String, Object> params) {
        ensureGraphProjection(params);
        int limit = asInt(params.get("limit"), DEFAULT_LIMIT);
        String cypher = "CALL gds.pageRank.stream('" + GRAPH_NAME + "') " +
                "YIELD nodeId, score " +
                "RETURN gds.util.asNode(nodeId).name AS name, score " +
                "ORDER BY score DESC LIMIT " + limit;
        Collection<Map<String, Object>> rows = runFetch(cypher, Collections.emptyMap());
        List<Map<String, Object>> nodes = new ArrayList<>(rows);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", nodes.size());
        summary.put("topScore", nodes.isEmpty() ? 0.0 : nodes.get(0).get("score"));
        return successResult(ALGO_PAGERANK, nodes, summary);
    }

    /**
     * 社区发现（Louvain）：发现图中的社区聚类结构
     */
    private AlgorithmResult executeCommunityDetection(Map<String, Object> params) {
        ensureGraphProjection(params);
        String cypher = "CALL gds.louvain.stream('" + GRAPH_NAME + "') " +
                "YIELD nodeId, communityId " +
                "RETURN gds.util.asNode(nodeId).name AS name, communityId " +
                "ORDER BY communityId";
        Collection<Map<String, Object>> rows = runFetch(cypher, Collections.emptyMap());
        List<Map<String, Object>> nodes = new ArrayList<>(rows);

        long communityCount = nodes.stream()
                .map(m -> m.get("communityId"))
                .distinct()
                .count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", nodes.size());
        summary.put("communityCount", communityCount);
        return successResult(ALGO_COMMUNITY, nodes, summary);
    }

    /**
     * 最短路径（Dijkstra）：计算两节点间的最短路径与总成本
     */
    private AlgorithmResult executeShortestPath(Map<String, Object> params) {
        Object sourceId = params.get("sourceId");
        Object targetId = params.get("targetId");
        if (sourceId == null || targetId == null) {
            return errorResult(ALGO_SHORTESTPATH, "最短路径算法需要 sourceId 与 targetId 参数");
        }
        ensureGraphProjection(params);

        String cypher = "CALL gds.shortestPath.dijkstra.stream('" + GRAPH_NAME + "', { " +
                "sourceNode: gds.util.asNode($sourceId), " +
                "targetNode: gds.util.asNode($targetId) " +
                "}) " +
                "YIELD index, sourceNode, targetNode, totalCost, nodeIds, costs " +
                "RETURN totalCost, [nodeId IN nodeIds | gds.util.asNode(nodeId).name] AS path";
        Map<String, Object> bindParams = new HashMap<>();
        bindParams.put("sourceId", sourceId);
        bindParams.put("targetId", targetId);

        Collection<Map<String, Object>> rows = runFetch(cypher, bindParams);
        List<Map<String, Object>> nodes = new ArrayList<>(rows);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", nodes.size());
        if (!nodes.isEmpty()) {
            summary.put("totalCost", nodes.get(0).get("totalCost"));
            Object pathObj = nodes.get(0).get("path");
            if (pathObj instanceof List) {
                summary.put("pathLength", ((List<?>) pathObj).size());
            }
        }
        return successResult(ALGO_SHORTESTPATH, nodes, summary);
    }

    /**
     * 度中心性：统计每个节点的度数
     */
    private AlgorithmResult executeDegreeCentrality(Map<String, Object> params) {
        ensureGraphProjection(params);
        int limit = asInt(params.get("limit"), DEFAULT_LIMIT);
        String cypher = "CALL gds.degree.stream('" + GRAPH_NAME + "') " +
                "YIELD nodeId, score " +
                "RETURN gds.util.asNode(nodeId).name AS name, score " +
                "ORDER BY score DESC LIMIT " + limit;
        Collection<Map<String, Object>> rows = runFetch(cypher, Collections.emptyMap());
        List<Map<String, Object>> nodes = new ArrayList<>(rows);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", nodes.size());
        summary.put("topScore", nodes.isEmpty() ? 0.0 : nodes.get(0).get("score"));
        return successResult(ALGO_CENTRALITY, nodes, summary);
    }

    // ==================== 图投影管理 ====================

    /**
     * 创建临时图投影
     *
     * <p>执行算法前先创建（或重建）名为 {@value #GRAPH_NAME} 的图投影。
     * 删除步骤包裹 try-catch 容忍投影不存在的情形，创建步骤失败则抛出由上层捕获。</p>
     *
     * @param params 算法参数（读取 nodeLabel / relationshipType）
     */
    private void ensureGraphProjection(Map<String, Object> params) {
        String nodeLabel = asString(params.get("nodeLabel"), DEFAULT_NODE_LABEL);
        String relType = asString(params.get("relationshipType"), DEFAULT_RELATIONSHIP_TYPE);

        // 先删除已存在的同名图投影（忽略异常，可能尚未创建）
        try {
            String dropCypher = "CALL gds.graph.drop('" + GRAPH_NAME + "', false)";
            runFetch(dropCypher, Collections.emptyMap());
        } catch (Exception e) {
            log.debug("删除图投影失败（可忽略，可能不存在）: {}", e.getMessage());
        }

        // 创建图投影
        String createCypher = "CALL gds.graph.project('" + GRAPH_NAME + "', $nodeLabel, $relationshipType)";
        Map<String, Object> projParams = new HashMap<>();
        projParams.put("nodeLabel", nodeLabel);
        projParams.put("relationshipType", relType);
        runFetch(createCypher, projParams);
        log.info("GDS 图投影已创建: graph={}, nodeLabel={}, relationshipType={}", GRAPH_NAME, nodeLabel, relType);
    }

    /**
     * 执行 Cypher 查询并返回结果集合
     *
     * @param cypher Cypher 语句
     * @param params 绑定参数
     * @return 结果行集合
     */
    private Collection<Map<String, Object>> runFetch(String cypher, Map<String, Object> params) {
        return neo4jClient.query(cypher).bindAll(params).fetch().all();
    }

    // ==================== 结果构建 ====================

    /**
     * 构建成功结果
     */
    private AlgorithmResult successResult(String algorithm, List<Map<String, Object>> nodes, Map<String, Object> summary) {
        AlgorithmResult result = new AlgorithmResult();
        result.setAlgorithm(algorithm);
        result.setSuccess(true);
        result.setMessage("执行成功");
        result.setNodes(nodes);
        result.setSummary(summary);
        return result;
    }

    /**
     * 构建降级结果（GDS 不可用）
     */
    private AlgorithmResult degradedResult(String algorithm, String message) {
        AlgorithmResult result = new AlgorithmResult();
        result.setAlgorithm(algorithm);
        result.setSuccess(false);
        result.setMessage(message);
        result.setNodes(new ArrayList<>());
        result.setSummary(new LinkedHashMap<>());
        return result;
    }

    /**
     * 构建错误结果（参数错误 / 未知算法）
     */
    private AlgorithmResult errorResult(String algorithm, String message) {
        AlgorithmResult result = new AlgorithmResult();
        result.setAlgorithm(algorithm);
        result.setSuccess(false);
        result.setMessage(message);
        result.setNodes(new ArrayList<>());
        result.setSummary(new LinkedHashMap<>());
        return result;
    }

    /**
     * 构建算法信息
     */
    private AlgorithmInfo buildInfo(String code, String name, String description, List<String> requiredParams) {
        AlgorithmInfo info = new AlgorithmInfo();
        info.setCode(code);
        info.setName(name);
        info.setDescription(description);
        info.setRequiredParams(requiredParams);
        return info;
    }

    // ==================== 工具方法 ====================

    /**
     * 安全转换为 int
     *
     * @param value        原始值
     * @param defaultValue 默认值
     * @return int 值
     */
    private int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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

    // ==================== 内部类 ====================

    /**
     * 算法信息
     */
    @Data
    public static class AlgorithmInfo implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 算法编码 */
        private String code;

        /** 算法名称 */
        private String name;

        /** 算法描述 */
        private String description;

        /** 必需参数 */
        private List<String> requiredParams;
    }

    /**
     * 算法执行结果
     */
    @Data
    public static class AlgorithmResult implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 算法名称 */
        private String algorithm;

        /** 是否成功 */
        private boolean success;

        /** 消息 */
        private String message;

        /** 节点结果（含算法产出属性） */
        private List<Map<String, Object>> nodes;

        /** 汇总信息 */
        private Map<String, Object> summary;
    }
}
