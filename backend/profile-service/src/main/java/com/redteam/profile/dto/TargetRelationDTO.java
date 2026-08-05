package com.redteam.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 目标关系图谱 DTO
 *
 * <p>用于前端 ECharts 关系图渲染，包含节点（GraphTarget）与边（GraphEdge）两部分。
 * 节点表示目标，边表示目标间的关系。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "目标关系图谱")
public class TargetRelationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图谱节点列表
     */
    @Schema(description = "图谱节点列表")
    private List<GraphTarget> nodes;

    /**
     * 图谱边列表
     */
    @Schema(description = "图谱边列表")
    private List<GraphEdge> edges;

    /**
     * 图谱节点
     */
    @Data
    @Schema(description = "图谱节点")
    public static class GraphTarget implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 节点ID（目标ID 字符串化，便于 ECharts 处理）
         */
        @Schema(description = "节点ID")
        private String id;

        /**
         * 节点名称
         */
        @Schema(description = "节点名称")
        private String name;

        /**
         * 节点类型（1-个人，2-组织，3-网站，4-IP，5-域名，6-其他）
         */
        @Schema(description = "节点类型")
        private Integer type;

        /**
         * 风险等级
         */
        @Schema(description = "风险等级")
        private Integer riskLevel;

        /**
         * 节点类别（用于 ECharts category，由 type 映射）
         */
        @Schema(description = "节点类别名称")
        private String category;

        /**
         * 节点大小（用于 ECharts symbolSize，根据关联数计算）
         */
        @Schema(description = "节点大小")
        private Integer symbolSize;

        /**
         * 节点值（关联数）
         */
        @Schema(description = "节点值")
        private Integer value;
    }

    /**
     * 图谱边
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
         * 关系类型
         */
        @Schema(description = "关系类型")
        private String relationType;

        /**
         * 关系权重
         */
        @Schema(description = "关系权重")
        private Double weight;

        /**
         * 关系描述
         */
        @Schema(description = "关系描述")
        private String description;
    }
}
