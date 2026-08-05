package com.redteam.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 任务分页查询 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "任务分页查询DTO")
public class TaskQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    @Schema(description = "当前页码", example = "1")
    @Min(value = 1, message = "页码最小为1")
    private Long pageNum = 1L;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小", example = "10")
    @Min(value = 1, message = "每页大小最小为1")
    @Max(value = 100, message = "每页大小最大为100")
    private Long pageSize = 10L;

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
     * 目标ID
     */
    @Schema(description = "目标ID")
    private String targetId;

    /**
     * 负责人ID
     */
    @Schema(description = "负责人ID")
    private Long ownerId;

    /**
     * 关键词（匹配任务名称、描述）
     */
    @Schema(description = "关键词")
    private String keyword;
}
