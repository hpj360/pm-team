package com.redteam.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知实体类
 *
 * <p>对应数据库表 redteam_notifications，记录站内信、邮件、IM 等多通道通知。
 * 增强字段支持发送者、过期时间、元数据、发送状态与重试次数，覆盖广播与重试场景。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("redteam_notifications")
public class NotificationEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 通知业务ID（UUID）
     */
    @TableField("notification_id")
    private String notificationId;

    /**
     * 接收用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 发送者ID（系统通知为 0L，用户消息为实际用户ID）
     */
    @TableField("sender_id")
    private Long senderId;

    /**
     * 通知标题
     */
    @TableField("title")
    private String title;

    /**
     * 通知内容
     */
    @TableField("content")
    private String content;

    /**
     * 通知类型：TASK / FILE / SYSTEM / SECURITY / ALERT
     */
    @TableField("type")
    private String type;

    /**
     * 通知级别：INFO / WARN / ERROR / CRITICAL
     */
    @TableField("level")
    private String level;

    /**
     * 通知通道：IN_APP / EMAIL / IM / ALL
     */
    @TableField("channel")
    private String channel;

    /**
     * 是否已读（0-未读，1-已读）
     */
    @TableField("is_read")
    private Integer isRead;

    /**
     * 阅读时间
     */
    @TableField("read_time")
    private LocalDateTime readTime;

    /**
     * 关联业务ID
     */
    @TableField("related_id")
    private String relatedId;

    /**
     * 关联业务类型
     */
    @TableField("related_type")
    private String relatedType;

    /**
     * 发送状态：PENDING-待发送 / SENT-已发送 / FAILED-发送失败
     */
    @TableField("send_status")
    private String sendStatus;

    /**
     * 重试次数（最大 3 次）
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 过期时间（超过此时间未读的通知可被清理）
     */
    @TableField("expired_time")
    private LocalDateTime expiredTime;

    /**
     * 元数据 JSON（扩展字段，用于存储附加业务信息）
     */
    @TableField("metadata")
    private String metadata;
}
