package com.redteam.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 工作流定义 DTO
 *
 * <p>用于创建/更新工作流定义时承载请求体。后端使用 Jackson 将 {@link #nodes} / {@link #edges}
 * 序列化为 JSON 字符串，写入 {@code workflow_definition.nodes_json}/{@code edges_json}。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "工作流定义")
public class WorkflowDefinitionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作流名称
     */
    @Schema(description = "工作流名称")
    private String name;

    /**
     * 描述
     */
    @Schema(description = "描述")
    private String description;

    /**
     * 业务类型：FILE_REVIEW/TASK_APPROVAL/REPORT_REVIEW
     */
    @Schema(description = "业务类型：FILE_REVIEW/TASK_APPROVAL/REPORT_REVIEW")
    private String businessType;

    /**
     * 节点列表
     */
    @Schema(description = "节点列表")
    private List<WorkflowNodeDTO> nodes;

    /**
     * 边列表
     */
    @Schema(description = "边列表")
    private List<WorkflowEdgeDTO> edges;
}
