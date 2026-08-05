package com.redteam.profile.neo4j.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Neo4j 关系图谱响应 DTO
 *
 * <p>用于前端 ECharts 力导向图渲染，包含节点（GraphNode）与边（GraphEdge）两部分。
 * 节点表示 Neo4j 中的实体（目标、文件、IOC、漏洞、攻击链），
 * 边表示实体之间的关系。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "Neo4j 关系图谱")
public class Neo4jGraphDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图谱节点列表
     */
    @Schema(description = "图谱节点列表")
    private List<GraphNode> nodes;

    /**
     * 图谱边列表
     */
    @Schema(description = "图谱边列表")
    private List<GraphEdge> edges;

    /**
     * 图谱节点（ECharts 力导向图格式）
     */
    @Data
    @Schema(description = "图谱节点")
    public static class GraphNode implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 节点ID（字符串化，便于 ECharts 处理）
         */
        @Schema(description = "节点ID")
        private String id;

        /**
         * 节点名称（用于显示）
         */
        @Schema(description = "节点名称")
        private String name;

        /**
         * 节点类型（Target/File/Ioc/Vuln/AttackChain）
         */
        @Schema(description = "节点类型")
        private String nodeType;

        /**
         * 节点类别（用于 ECharts category 分类）
         */
        @Schema(description = "节点类别")
        private String category;

        /**
         * 节点大小（用于 ECharts symbolSize）
         */
        @Schema(description = "节点大小")
        private Integer symbolSize;

        /**
         * 节点值（关联数）
         */
        @Schema(description = "节点值")
        private Integer value;

        /**
         * 节点ID（Long 类型，内部使用）
         */
        @Schema(description = "节点原始ID")
        private Long rawId;
    }

    /**
     * 图谱边（ECharts 力导向图格式）
     */
    @Data
    @Schema(description = "图谱边")
    public static class GraphEdge implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 源节点ID
         */
        @Schema(description = "源节点ID")
        private String source;

        /**
         * 目标节点ID
         */
        @Schema(description = "目标节点ID")
        private String target;

        /**
         * 关系类型（CONTAINS/RELATES_TO/EXPLOITS/TARGETS）
         */
        @Schema(description = "关系类型")
        private String relationType;

        /**
         * 关系权重
         */
        @Schema(description = "关系权重")
        private Double weight;
    }
}
