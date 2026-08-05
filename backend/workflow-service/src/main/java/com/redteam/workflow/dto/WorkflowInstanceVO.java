package com.redteam.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批实例 VO
 *
 * <p>包含实例基础信息以及该实例的全部审批记录。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "审批实例详情")
public class WorkflowInstanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 实例ID
     */
    @Schema(description = "实例ID")
    private Long id;

    /**
     * 工作流名称
     */
    @Schema(description = "工作流名称")
    private String workflowName;

    /**
     * 业务ID
     */
    @Schema(description = "业务ID")
    private String businessId;

    /**
     * 业务类型
     */
    @Schema(description = "业务类型")
    private String businessType;

    /**
     * 提交人姓名
     */
    @Schema(description = "提交人姓名")
    private String submitterName;

    /**
     * 状态：PENDING/APPROVED/REJECTED/CANCELLED
     */
    @Schema(description = "状态：PENDING/APPROVED/REJECTED/CANCELLED")
    private String status;

    /**
     * 当前节点名称
     */
    @Schema(description = "当前节点名称")
    private String currentNodeName;

    /**
     * 审批记录列表
     */
    @Schema(description = "审批记录列表")
    private List<WorkflowReviewVO> reviews;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
