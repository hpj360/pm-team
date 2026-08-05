package com.redteam.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 通知统计 DTO
 *
 * <p>用于仪表盘展示，包含总数、未读数、按类型/级别/通道维度的统计结果。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "通知统计")
public class NotificationStatsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 通知总数
     */
    @Schema(description = "通知总数")
    private Long total;

    /**
     * 未读通知数
     */
    @Schema(description = "未读通知数")
    private Long unreadCount;

    /**
     * 已读通知数
     */
    @Schema(description = "已读通知数")
    private Long readCount;

    /**
     * 按通知类型分组（key=类型，value=数量）
     */
    @Schema(description = "按类型分组")
    private Map<String, Long> byType;

    /**
     * 按通知级别分组（key=级别，value=数量）
     */
    @Schema(description = "按级别分组")
    private Map<String, Long> byLevel;

    /**
     * 按通知通道分组（key=通道，value=数量）
     */
    @Schema(description = "按通道分组")
    private Map<String, Long> byChannel;

    /**
     * 按发送状态分组（key=状态，value=数量）
     */
    @Schema(description = "按发送状态分组")
    private Map<String, Long> bySendStatus;

    /**
     * 已读率（百分比）
     */
    @Schema(description = "已读率（百分比）")
    private Double readRate;
}
