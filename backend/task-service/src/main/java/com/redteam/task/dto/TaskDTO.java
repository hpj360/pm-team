package com.redteam.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务创建/更新 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "任务创建/更新DTO")
public class TaskDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务名称
     */
    @Schema(description = "任务名称", example = "目标A侦察任务")
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称长度不能超过128")
    private String taskName;

    /**
     * 任务类型：RECON/EXPLOIT/DELIVERY/POST_EXPLOIT/REPORT
     */
    @Schema(description = "任务类型", example = "RECON",
            allowableValues = {"RECON", "EXPLOIT", "DELIVERY", "POST_EXPLOIT", "REPORT"})
    @NotBlank(message = "任务类型不能为空")
    @Pattern(regexp = "RECON|EXPLOIT|DELIVERY|POST_EXPLOIT|REPORT",
            message = "任务类型必须为 RECON/EXPLOIT/DELIVERY/POST_EXPLOIT/REPORT")
    private String taskType;

    /**
     * 优先级（1-5，1最高）
     */
    @Schema(description = "优先级(1-5)", example = "3")
    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级最小为1")
    @Max(value = 5, message = "优先级最大为5")
    private Integer priority;

    /**
     * 目标ID
     */
    @Schema(description = "目标ID", example = "target-001")
    private String targetId;

    /**
     * 关联文件ID（逗号分隔）
     */
    @Schema(description = "关联文件ID（逗号分隔）", example = "1001,1002,1003")
    @Size(max = 512, message = "关联文件ID长度不能超过512")
    private String fileIds;

    /**
     * 负责人ID
     */
    @Schema(description = "负责人ID", example = "1001")
    @NotNull(message = "负责人ID不能为空")
    private Long ownerId;

    /**
     * 截止时间
     */
    @Schema(description = "截止时间")
    private LocalDateTime deadline;

    /**
     * 任务进度（0-100，仅更新接口可设置）
     */
    @Schema(description = "任务进度(0-100)", example = "0")
    @Min(value = 0, message = "进度不能小于0")
    @Max(value = 100, message = "进度不能大于100")
    private Integer progress;

    /**
     * 任务描述
     */
    @Schema(description = "任务描述")
    @Size(max = 1024, message = "任务描述长度不能超过1024")
    private String description;
}
