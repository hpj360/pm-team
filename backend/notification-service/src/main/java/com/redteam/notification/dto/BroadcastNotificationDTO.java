package com.redteam.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 广播通知请求 DTO
 *
 * <p>支持向多个用户批量发送相同通知，常用于系统公告、安全告警广播等场景。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "广播通知请求")
public class BroadcastNotificationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 接收用户ID列表
     */
    @Schema(description = "接收用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "接收用户列表不能为空")
    private List<Long> userIds;

    /**
     * 发送者ID（系统通知为 0L）
     */
    @Schema(description = "发送者ID", example = "0")
    private Long senderId;

    /**
     * 通知标题
     */
    @Schema(description = "通知标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "系统维护通知")
    @NotBlank(message = "通知标题不能为空")
    @Size(max = 200, message = "通知标题长度不能超过200")
    private String title;

    /**
     * 通知内容
     */
    @Schema(description = "通知内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "系统将于今晚 22:00 维护")
    @NotBlank(message = "通知内容不能为空")
    @Size(max = 2000, message = "通知内容长度不能超过2000")
    private String content;

    /**
     * 通知类型
     */
    @Schema(description = "通知类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "SYSTEM")
    @NotBlank(message = "通知类型不能为空")
    @Pattern(regexp = "TASK|FILE|SYSTEM|SECURITY|ALERT", message = "通知类型必须为 TASK/FILE/SYSTEM/SECURITY/ALERT")
    private String type;

    /**
     * 通知级别
     */
    @Schema(description = "通知级别", requiredMode = Schema.RequiredMode.REQUIRED, example = "INFO")
    @NotBlank(message = "通知级别不能为空")
    @Pattern(regexp = "INFO|WARN|ERROR|CRITICAL", message = "通知级别必须为 INFO/WARN/ERROR/CRITICAL")
    private String level;

    /**
     * 通知通道
     */
    @Schema(description = "通知通道", requiredMode = Schema.RequiredMode.REQUIRED, example = "IN_APP")
    @NotBlank(message = "通知通道不能为空")
    @Pattern(regexp = "IN_APP|EMAIL|IM|ALL", message = "通知通道必须为 IN_APP/EMAIL/IM/ALL")
    private String channel;

    /**
     * 关联业务ID
     */
    @Schema(description = "关联业务ID")
    private String relatedId;

    /**
     * 关联业务类型
     */
    @Schema(description = "关联业务类型")
    private String relatedType;

    /**
     * 过期时间
     */
    @Schema(description = "过期时间")
    private LocalDateTime expiredTime;

    /**
     * 元数据 JSON
     */
    @Schema(description = "元数据JSON")
    @Size(max = 1024, message = "元数据长度不能超过1024")
    private String metadata;
}
