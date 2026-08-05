package com.redteam.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 报告模板返回 VO
 *
 * <p>用于模板列表查询接口的响应数据。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "报告模板信息")
public class ReportTemplateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模板ID
     */
    @Schema(description = "模板ID", example = "tpl-task-summary")
    private String templateId;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称", example = "任务总结报告模板")
    private String templateName;

    /**
     * 模板类型
     */
    @Schema(description = "模板类型", example = "TASK_SUMMARY")
    private String templateType;

    /**
     * Thymeleaf 模板路径
     */
    @Schema(description = "Thymeleaf 模板路径", example = "task-summary")
    private String templatePath;

    /**
     * 模板描述
     */
    @Schema(description = "模板描述", example = "用于任务完成后自动生成的总结报告")
    private String description;
}
