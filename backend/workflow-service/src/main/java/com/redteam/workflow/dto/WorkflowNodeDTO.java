package com.redteam.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 工作流节点 DTO
 *
 * <p>描述一个审批节点，支持以下节点类型：</p>
 * <ul>
 *   <li>{@code START} - 开始节点</li>
 *   <li>{@code APPROVAL} - 审批节点</li>
 *   <li>{@code CC} - 抄送节点</li>
 *   <li>{@code CONDITION} - 条件分支节点</li>
 *   <li>{@code END} - 结束节点</li>
 * </ul>
 *
 * <p>审批模式（仅 {@code APPROVAL} 节点适用）：</p>
 * <ul>
 *   <li>{@code SEQUENTIAL} - 线性审批：节点内审批人按顺序逐人通过</li>
 *   <li>{@code PARALLEL_ALL} - 会签：节点内所有审批人都通过后才进入下一节点</li>
 *   <li>{@code PARALLEL_ANY} - 或签：节点内任一审批人通过即进入下一节点</li>
 * </ul>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "工作流节点")
public class WorkflowNodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点ID
     */
    @Schema(description = "节点ID")
    private String nodeId;

    /**
     * 节点名称
     */
    @Schema(description = "节点名称")
    private String nodeName;

    /**
     * 节点类型：START / APPROVAL / CC / CONDITION / END
     */
    @Schema(description = "节点类型：START/APPROVAL/CC/CONDITION/END")
    private String nodeType;

    /**
     * 审批模式：SEQUENTIAL / PARALLEL_ALL / PARALLEL_ANY（仅 APPROVAL 节点适用）
     */
    @Schema(description = "审批模式：SEQUENTIAL/PARALLEL_ALL/PARALLEL_ANY")
    private String approvalMode;

    /**
     * 审批人ID列表
     */
    @Schema(description = "审批人ID列表")
    private List<Long> approverIds;
}
