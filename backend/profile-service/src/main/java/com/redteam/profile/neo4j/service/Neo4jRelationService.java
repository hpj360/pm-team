package com.redteam.profile.neo4j.service;

import com.redteam.profile.neo4j.dto.Neo4jGraphDTO;

/**
 * Neo4j 关系图谱服务接口
 *
 * <p>提供基于 Neo4j 图数据库的多跳关系查询能力，用于前端 ECharts 力导向图渲染。
 * 当 Neo4j 不可用时，自动降级到 Mock 数据，保证前端图谱可用。</p>
 *
 * @author 红方团队
 */
public interface Neo4jRelationService {

    /**
     * 查询目标的多跳关系图谱
     *
     * <p>以指定目标为根节点，查询 1-depth 跳范围内的所有关联节点与边，
     * 转换为前端 ECharts 力导向图所需的 nodes + edges 格式。</p>
     *
     * <p>当 Neo4j 连接失败或查询异常时，自动降级到 Mock 数据，
     * 返回以根目标为中心的占位图谱，不影响前端渲染。</p>
     *
     * @param targetId 根目标ID
     * @param depth    关系展开深度（1-3），null 时使用默认值 1，超过 3 时截断为 3
     * @return 关系图谱 DTO，包含节点列表与边列表
     */
    Neo4jGraphDTO getRelationGraph(Long targetId, Integer depth);
}
