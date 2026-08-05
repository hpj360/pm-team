package com.redteam.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 审批决定请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "审批决定请求")
public class ReviewDecisionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 实例ID
     */
    @Schema(description = "实例ID")
    private Long instanceId;

    /**
     * 决定：APPROVE / REJECT
     */
    @Schema(description = "决定：APPROVE/REJECT")
    private String decision;

    /**
     * 审批意见
     */
    @Schema(description = "审批意见")
    private String comment;
}
