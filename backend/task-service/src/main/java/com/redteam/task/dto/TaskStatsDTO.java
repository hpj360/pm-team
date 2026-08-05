package com.redteam.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 任务统计 DTO
 *
 * <p>用于仪表盘展示，包含按状态、优先级、负责人维度的统计结果。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "任务统计")
public class TaskStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 总任务数
     */
    @Schema(description = "总任务数")
    private Long total;

    /**
     * 按状态分组（key=状态，value=数量）
     */
    @Schema(description = "按状态分组")
    private Map<String, Long> byStatus;

    /**
     * 按优先级分组（key=优先级，value=数量）
     */
    @Schema(description = "按优先级分组")
    private Map<String, Long> byPriority;

    /**
     * 按负责人分组（key=负责人ID，value=数量）
     */
    @Schema(description = "按负责人分组")
    private Map<String, Long> byOwner;

    /**
     * 已完成任务数
     */
    @Schema(description = "已完成任务数")
    private Long completedCount;

    /**
     * 进行中任务数
     */
    @Schema(description = "进行中任务数")
    private Long runningCount;

    /**
     * 待办任务数
     */
    @Schema(description = "待办任务数")
    private Long pendingCount;

    /**
     * 完成率（百分比）
     */
    @Schema(description = "完成率（百分比）")
    private Double completionRate;
}
