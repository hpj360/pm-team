package com.redteam.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知分页查询 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "通知分页查询请求")
public class NotificationQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码，默认 1
     */
    @Schema(description = "当前页码", example = "1")
    private Long pageNum = 1L;

    /**
     * 每页大小，默认 10
     */
    @Schema(description = "每页大小", example = "10")
    private Long pageSize = 10L;

    /**
     * 通知类型：TASK / FILE / SYSTEM / SECURITY / ALERT
     */
    @Schema(description = "通知类型", example = "TASK")
    private String type;

    /**
     * 通知级别：INFO / WARN / ERROR / CRITICAL
     */
    @Schema(description = "通知级别", example = "INFO")
    private String level;

    /**
     * 是否已读（0-未读，1-已读），null 表示不限制
     */
    @Schema(description = "是否已读（0-未读，1-已读）", example = "0")
    private Integer isRead;

    /**
     * 查询开始时间
     */
    @Schema(description = "查询开始时间")
    private LocalDateTime startTime;

    /**
     * 查询结束时间
     */
    @Schema(description = "查询结束时间")
    private LocalDateTime endTime;
}
