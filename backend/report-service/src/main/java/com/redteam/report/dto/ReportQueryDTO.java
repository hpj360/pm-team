package com.redteam.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 报告分页查询请求 DTO
 *
 * <p>用于 {@code GET /api/v1/reports} 接口的多条件分页查询。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "报告查询请求")
public class ReportQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码（默认 1）
     */
    @Schema(description = "当前页码", example = "1", defaultValue = "1")
    private Long current = 1L;

    /**
     * 每页大小（默认 10）
     */
    @Schema(description = "每页大小", example = "10", defaultValue = "10")
    private Long size = 10L;

    /**
     * 报告类型（可选）
     */
    @Schema(description = "报告类型", example = "TASK_SUMMARY")
    private String reportType;

    /**
     * 报告状态（可选）
     */
    @Schema(description = "报告状态", example = "COMPLETED")
    private String status;

    /**
     * 关联任务ID（可选）
     */
    @Schema(description = "关联任务ID")
    private String taskId;

    /**
     * 关联目标ID（可选）
     */
    @Schema(description = "关联目标ID")
    private String targetId;

    /**
     * 关键词（按报告名称模糊匹配）
     */
    @Schema(description = "报告名称关键词（模糊匹配）")
    private String keyword;
}
