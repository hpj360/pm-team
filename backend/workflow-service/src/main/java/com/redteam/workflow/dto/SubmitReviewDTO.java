package com.redteam.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 启动工作流实例请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "启动工作流实例请求")
public class SubmitReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 工作流定义ID
     */
    @Schema(description = "工作流定义ID")
    private Long workflowId;

    /**
     * 业务ID（如文件ID/任务ID）
     */
    @Schema(description = "业务ID")
    private String businessId;

    /**
     * 业务类型
     */
    @Schema(description = "业务类型")
    private String businessType;

    /**
     * 提交说明
     */
    @Schema(description = "提交说明")
    private String comment;
}
