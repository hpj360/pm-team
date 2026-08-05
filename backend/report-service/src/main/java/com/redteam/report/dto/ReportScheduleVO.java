package com.redteam.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时报告响应 VO
 *
 * <p>用于 {@code GET /api/report/schedules} 列表与详情接口的响应，
 * 包含最近一次执行时间和执行状态。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "定时报告响应")
public class ReportScheduleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @Schema(description = "主键ID", example = "1")
    private Long id;

    /**
     * 报告名称
     */
    @Schema(description = "报告名称", example = "每周渗透测试报告")
    private String reportName;

    /**
     * 报告类型
     */
    @Schema(description = "报告类型", example = "PENETRATION_TEST")
    private String reportType;

    /**
     * Cron 表达式
     */
    @Schema(description = "Cron 表达式", example = "0 0 9 * * MON")
    private String cronExpression;

    /**
     * 收件人邮箱列表（多个以英文逗号分隔）
     */
    @Schema(description = "收件人邮箱列表", example = "alice@redteam.com,bob@redteam.com")
    private String recipients;

    /**
     * 模板名称
     */
    @Schema(description = "模板名称", example = "penetration-test")
    private String templateName;

    /**
     * 关联目标ID
     */
    @Schema(description = "关联目标ID", example = "2001")
    private Long targetId;

    /**
     * 状态（ACTIVE/INACTIVE）
     */
    @Schema(description = "状态", example = "ACTIVE")
    private String status;

    /**
     * 最近一次执行时间
     */
    @Schema(description = "最近一次执行时间")
    private LocalDateTime lastRunTime;

    /**
     * 最近一次执行状态（SUCCESS/FAILED/RUNNING）
     */
    @Schema(description = "最近一次执行状态", example = "SUCCESS")
    private String lastRunStatus;

    /**
     * 推送通道：EMAIL/SLACK/DINGTALK/ALL，默认 EMAIL
     */
    @Schema(description = "推送通道：EMAIL/SLACK/DINGTALK/ALL", example = "EMAIL")
    private String webhookType;

    /**
     * 创建人标识
     */
    @Schema(description = "创建人标识", example = "admin")
    private String createdBy;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
