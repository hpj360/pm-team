package com.redteam.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 提交文件评审请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "提交文件评审请求")
public class SubmitFileReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @Schema(description = "文件ID")
    private Long fileId;

    /**
     * 提交人ID
     */
    @Schema(description = "提交人ID")
    private Long submitterId;

    /**
     * 提交人姓名
     */
    @Schema(description = "提交人姓名")
    private String submitterName;

    /**
     * 提交说明
     */
    @Schema(description = "提交说明")
    private String comment;
}
