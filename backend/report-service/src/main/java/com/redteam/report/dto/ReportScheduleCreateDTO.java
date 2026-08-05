package com.redteam.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 定时报告创建请求 DTO
 *
 * <p>用于 {@code POST /api/report/schedules} 接口的请求体，配置一条定时报告任务。
 * 创建后调度器会立即注册到 Spring {@link org.springframework.scheduling.TaskScheduler}。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "定时报告创建请求")
public class ReportScheduleCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报告名称
     */
    @NotBlank(message = "报告名称不能为空")
    @Schema(description = "报告名称", example = "每周渗透测试报告", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reportName;

    /**
     * 报告类型
     */
    @NotBlank(message = "报告类型不能为空")
    @Pattern(regexp = "PENETRATION_TEST|VULNERABILITY_SCAN|ATTACK_CHAIN|TARGET_PROFILE|TASK_SUMMARY",
            message = "报告类型必须是 PENETRATION_TEST/VULNERABILITY_SCAN/ATTACK_CHAIN/TARGET_PROFILE/TASK_SUMMARY 之一")
    @Schema(description = "报告类型", example = "PENETRATION_TEST", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reportType;

    /**
     * Cron 表达式（Spring 6 风格，6 字段：秒 分 时 日 月 周）
     */
    @NotBlank(message = "Cron 表达式不能为空")
    @Schema(description = "Cron 表达式（Spring 6 风格：秒 分 时 日 月 周）",
            example = "0 0 9 * * MON", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cronExpression;

    /**
     * 收件人邮箱列表（多个以英文逗号分隔）
     */
    @NotBlank(message = "收件人不能为空")
    @Schema(description = "收件人邮箱列表（多个以英文逗号分隔）",
            example = "alice@redteam.com,bob@redteam.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recipients;

    /**
     * 使用的 Thymeleaf 模板名（可空，空时按 reportType 自动匹配）
     */
    @Schema(description = "模板名称（可空，空时按 reportType 自动匹配）", example = "penetration-test")
    private String templateName;

    /**
     * 关联目标ID（可空）
     */
    @Schema(description = "关联目标ID", example = "2001")
    private Long targetId;

    /**
     * 推送通道：EMAIL/SLACK/DINGTALK/ALL，默认 EMAIL
     */
    @Schema(description = "推送通道：EMAIL/SLACK/DINGTALK/ALL", example = "EMAIL")
    private String webhookType;

    /**
     * 创建人标识（可空，由调用方传入）
     */
    @Schema(description = "创建人标识", example = "admin")
    private String createdBy;
}
