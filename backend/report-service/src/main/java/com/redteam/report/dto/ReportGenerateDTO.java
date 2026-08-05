package com.redteam.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 报告生成请求 DTO
 *
 * <p>用于 {@code POST /api/v1/reports} 接口的请求体，
 * 触发异步报告生成流程并立即返回 {@code reportId}。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "报告生成请求")
public class ReportGenerateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报告名称
     */
    @NotBlank(message = "报告名称不能为空")
    @Schema(description = "报告名称", example = "渗透测试报告-2026Q3", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reportName;

    /**
     * 报告类型
     */
    @NotBlank(message = "报告类型不能为空")
    @Pattern(regexp = "PENETRATION_TEST|VULNERABILITY_SCAN|ATTACK_CHAIN|TARGET_PROFILE|TASK_SUMMARY",
            message = "报告类型必须是 PENETRATION_TEST/VULNERABILITY_SCAN/ATTACK_CHAIN/TARGET_PROFILE/TASK_SUMMARY 之一")
    @Schema(description = "报告类型（PENETRATION_TEST/VULNERABILITY_SCAN/ATTACK_CHAIN/TARGET_PROFILE/TASK_SUMMARY）",
            example = "TASK_SUMMARY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reportType;

    /**
     * 关联任务ID
     */
    @Schema(description = "关联任务ID", example = "task-1001")
    private String taskId;

    /**
     * 关联目标ID
     */
    @Schema(description = "关联目标ID", example = "target-2001")
    private String targetId;

    /**
     * 模板ID（可选，未指定时按 reportType 自动匹配）
     */
    @Schema(description = "模板ID（可选，未指定时按 reportType 自动匹配）", example = "tpl-task-summary")
    private String templateId;

    /**
     * 报告格式
     */
    @NotBlank(message = "报告格式不能为空")
    @Pattern(regexp = "PDF|WORD|HTML", message = "报告格式必须是 PDF/WORD/HTML 之一")
    @Schema(description = "报告格式（PDF/WORD/HTML）", example = "PDF", requiredMode = Schema.RequiredMode.REQUIRED)
    private String format;
}
