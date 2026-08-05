package com.redteam.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 工作流边 DTO
 *
 * <p>描述节点之间的连接关系，{@code sourceNodeId} → {@code targetNodeId}。
 * {@code condition} 仅对 {@code CONDITION} 类型节点的出边有效。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "工作流边")
public class WorkflowEdgeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 源节点ID
     */
    @Schema(description = "源节点ID")
    private String sourceNodeId;

    /**
     * 目标节点ID
     */
    @Schema(description = "目标节点ID")
    private String targetNodeId;

    /**
     * 条件表达式（仅 CONDITION 节点出边适用）
     */
    @Schema(description = "条件表达式（仅 CONDITION 节点出边适用）")
    private String condition;
}
