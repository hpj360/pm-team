package com.redteam.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务返回 VO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "任务返回VO")
public class TaskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 任务ID（UUID，业务主键）
     */
    @Schema(description = "任务ID")
    private String taskId;

    /**
     * 任务名称
     */
    @Schema(description = "任务名称")
    private String taskName;

    /**
     * 任务类型
     */
    @Schema(description = "任务类型")
    private String taskType;

    /**
     * 任务状态
     */
    @Schema(description = "任务状态")
    private String status;

    /**
     * 优先级
     */
    @Schema(description = "优先级")
    private Integer priority;

    /**
     * 目标ID
     */
    @Schema(description = "目标ID")
    private String targetId;

    /**
     * 关联文件ID（逗号分隔）
     */
    @Schema(description = "关联文件ID")
    private String fileIds;

    /**
     * 负责人ID
     */
    @Schema(description = "负责人ID")
    private Long ownerId;

    /**
     * 截止时间
     */
    @Schema(description = "截止时间")
    private LocalDateTime deadline;

    /**
     * 任务进度（0-100）
     */
    @Schema(description = "任务进度")
    private Integer progress;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /**
     * 任务描述
     */
    @Schema(description = "任务描述")
    private String description;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 创建人ID
     */
    @Schema(description = "创建人ID")
    private Long createBy;

    /**
     * 更新人ID
     */
    @Schema(description = "更新人ID")
    private Long updateBy;
}
