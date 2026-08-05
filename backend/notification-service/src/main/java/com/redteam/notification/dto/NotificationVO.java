package com.redteam.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知返回 VO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "通知返回对象")
public class NotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通知业务ID
     */
    @Schema(description = "通知业务ID", example = "notif-uuid-1234")
    private String notificationId;

    /**
     * 接收用户ID
     */
    @Schema(description = "接收用户ID", example = "1001")
    private Long userId;

    /**
     * 发送者ID
     */
    @Schema(description = "发送者ID", example = "0")
    private Long senderId;

    /**
     * 通知标题
     */
    @Schema(description = "通知标题", example = "任务已完成")
    private String title;

    /**
     * 通知内容
     */
    @Schema(description = "通知内容", example = "您提交的分析任务已完成")
    private String content;

    /**
     * 通知类型
     */
    @Schema(description = "通知类型", example = "TASK")
    private String type;

    /**
     * 通知级别
     */
    @Schema(description = "通知级别", example = "INFO")
    private String level;

    /**
     * 通知通道
     */
    @Schema(description = "通知通道", example = "IN_APP")
    private String channel;

    /**
     * 是否已读（0-未读，1-已读）
     */
    @Schema(description = "是否已读", example = "0")
    private Integer isRead;

    /**
     * 阅读时间
     */
    @Schema(description = "阅读时间")
    private LocalDateTime readTime;

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
     * 发送状态：PENDING/SENT/FAILED
     */
    @Schema(description = "发送状态", example = "SENT")
    private String sendStatus;

    /**
     * 重试次数
     */
    @Schema(description = "重试次数", example = "0")
    private Integer retryCount;

    /**
     * 过期时间
     */
    @Schema(description = "过期时间")
    private LocalDateTime expiredTime;

    /**
     * 元数据 JSON
     */
    @Schema(description = "元数据JSON")
    private String metadata;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
