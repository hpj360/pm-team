package com.redteam.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 报告统计 DTO
 *
 * <p>用于仪表盘展示，包含总数、按状态/类型/格式维度的统计结果。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "报告统计")
public class ReportStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报告总数
     */
    @Schema(description = "报告总数")
    private Long total;

    /**
     * 已完成报告数
     */
    @Schema(description = "已完成报告数")
    private Long completedCount;

    /**
     * 生成中报告数
     */
    @Schema(description = "生成中报告数")
    private Long generatingCount;

    /**
     * 待生成报告数
     */
    @Schema(description = "待生成报告数")
    private Long pendingCount;

    /**
     * 失败报告数
     */
    @Schema(description = "失败报告数")
    private Long failedCount;

    /**
     * 按报告类型分组（key=类型，value=数量）
     */
    @Schema(description = "按报告类型分组")
    private Map<String, Long> byType;

    /**
     * 按报告状态分组（key=状态，value=数量）
     */
    @Schema(description = "按报告状态分组")
    private Map<String, Long> byStatus;

    /**
     * 按报告格式分组（key=格式，value=数量）
     */
    @Schema(description = "按报告格式分组")
    private Map<String, Long> byFormat;

    /**
     * 完成率（百分比）
     */
    @Schema(description = "完成率（百分比）")
    private Double completionRate;

    /**
     * 共享报告数
     */
    @Schema(description = "共享报告数")
    private Long sharedCount;
}
