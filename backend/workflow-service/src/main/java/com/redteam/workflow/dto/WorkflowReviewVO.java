package com.redteam.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批意见 VO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "审批意见")
public class WorkflowReviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 节点ID
     */
    @Schema(description = "节点ID")
    private String nodeId;

    /**
     * 审批人ID
     */
    @Schema(description = "审批人ID")
    private Long reviewerId;

    /**
     * 审批人姓名
     */
    @Schema(description = "审批人姓名")
    private String reviewerName;

    /**
     * 决定：APPROVE/REJECT
     */
    @Schema(description = "决定：APPROVE/REJECT")
    private String decision;

    /**
     * 审批意见
     */
    @Schema(description = "审批意见")
    private String comment;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
