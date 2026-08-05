package com.redteam.profile.neo4j.repository;

import com.redteam.profile.neo4j.entity.TargetNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.Map;

/**
 * Neo4j 关系图谱 Repository
 *
 * <p>继承 {@link Neo4jRepository} 提供 TargetNode 的基础 CRUD 能力，
 * 同时通过自定义 @Query 提供 1-3 跳多跳遍历查询，用于构建关系图谱。</p>
 *
 * <p>支持的 Cypher 查询：</p>
 * <ul>
 *   <li>{@link #findRelationsByTargetId(Long, int)} - 以指定目标为根节点，
 *       查询 1-N 跳范围内的所有关联节点与关系</li>
 * </ul>
 *
 * @author 红方团队
 */
public interface Neo4jRelationRepository extends Neo4jRepository<TargetNode, Long> {

    /**
     * 多跳关系查询：以指定目标为根节点，查询 1-depth 跳范围内的所有关联节点与边
     *
     * <p>Cypher 使用可变长度路径 {@code *1..depth} 进行多跳遍历，
     * 返回每条路径的节点列表与关系列表。Service 层负责去重并转换为 ECharts 格式。</p>
     *
     * <p>查询示例（depth=3）：</p>
     * <pre>{@code
     * MATCH path = (t:Target)-[*1..3]-(n)
     * WHERE t.id = $targetId
     * RETURN nodes(path) AS nodes, relationships(path) AS rels
     * }</pre>
     *
     * @param targetId 根目标ID
     * @param depth    关系展开深度（1-3）
     * @return 查询结果迭代器，每个元素为包含 "nodes" 和 "rels" 两个键的 Map
     */
    @Query("MATCH path = (t:Target)-[*1..#{#depth}]-(n) " +
            "WHERE t.id = $targetId " +
            "UNWIND nodes(path) AS node " +
            "WITH collect(DISTINCT node) AS uniqueNodes " +
            "OPTIONAL MATCH (src)-[r]->(dst) WHERE src IN uniqueNodes AND dst IN uniqueNodes " +
            "WITH uniqueNodes, collect(DISTINCT r) AS allRels " +
            "RETURN " +
            "  [node IN uniqueNodes | {nodeId: node.id, name: coalesce(node.name, node.fileName, node.iocValue, node.cveId, 'Unknown'), nodeType: head(labels(node))}] AS nodes, " +
            "  [rel IN allRels WHERE rel IS NOT NULL | {source: startNode(rel).id, target: endNode(rel).id, relationType: type(rel)}] AS rels")
    Iterable<Map<String, Object>> findRelationsByTargetId(@Param("targetId") Long targetId,
                                                           @Param("depth") int depth);
}
