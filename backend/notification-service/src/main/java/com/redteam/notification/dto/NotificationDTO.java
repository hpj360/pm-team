package com.redteam.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 发送通知请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "发送通知请求")
public class NotificationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 接收用户ID
     */
    @Schema(description = "接收用户ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "接收用户ID不能为空")
    private Long userId;

    /**
     * 发送者ID（系统通知为 0L 或不填）
     */
    @Schema(description = "发送者ID", example = "0")
    private Long senderId;

    /**
     * 通知标题
     */
    @Schema(description = "通知标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "任务已完成")
    @NotBlank(message = "通知标题不能为空")
    @Size(max = 200, message = "通知标题长度不能超过200")
    private String title;

    /**
     * 通知内容
     */
    @Schema(description = "通知内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "您提交的分析任务已完成")
    @NotBlank(message = "通知内容不能为空")
    @Size(max = 2000, message = "通知内容长度不能超过2000")
    private String content;

    /**
     * 通知类型：TASK / FILE / SYSTEM / SECURITY / ALERT
     */
    @Schema(description = "通知类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "TASK")
    @NotBlank(message = "通知类型不能为空")
    @Pattern(regexp = "TASK|FILE|SYSTEM|SECURITY|ALERT", message = "通知类型必须为 TASK/FILE/SYSTEM/SECURITY/ALERT")
    private String type;

    /**
     * 通知级别：INFO / WARN / ERROR / CRITICAL
     */
    @Schema(description = "通知级别", requiredMode = Schema.RequiredMode.REQUIRED, example = "INFO")
    @NotBlank(message = "通知级别不能为空")
    @Pattern(regexp = "INFO|WARN|ERROR|CRITICAL", message = "通知级别必须为 INFO/WARN/ERROR/CRITICAL")
    private String level;

    /**
     * 通知通道：IN_APP / EMAIL / IM / ALL
     */
    @Schema(description = "通知通道", requiredMode = Schema.RequiredMode.REQUIRED, example = "IN_APP")
    @NotBlank(message = "通知通道不能为空")
    @Pattern(regexp = "IN_APP|EMAIL|IM|ALL", message = "通知通道必须为 IN_APP/EMAIL/IM/ALL")
    private String channel;

    /**
     * 关联业务ID
     */
    @Schema(description = "关联业务ID", example = "task-uuid-1234")
    private String relatedId;

    /**
     * 关联业务类型
     */
    @Schema(description = "关联业务类型", example = "TASK")
    private String relatedType;

    /**
     * 过期时间（可选，超过此时间未读可被清理）
     */
    @Schema(description = "过期时间")
    private java.time.LocalDateTime expiredTime;

    /**
     * 元数据 JSON（扩展字段）
     */
    @Schema(description = "元数据JSON", example = "{\"key\":\"value\"}")
    @Size(max = 1024, message = "元数据长度不能超过1024")
    private String metadata;
}
