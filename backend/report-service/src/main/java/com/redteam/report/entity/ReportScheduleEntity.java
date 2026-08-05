package com.redteam.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.FieldFill;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时报告配置实体类
 *
 * <p>对应数据库表 {@code report_schedule}，记录每条定时报告的 cron 表达式、收件人、
 * 使用的模板及最近一次执行的状态。调度器在启动时加载所有 {@code ACTIVE} 记录并注册到
 * Spring {@link org.springframework.scheduling.TaskScheduler}。</p>
 *
 * <p>状态取值：{@code ACTIVE} / {@code INACTIVE}</p>
 * <p>最近执行状态取值：{@code SUCCESS} / {@code FAILED} / {@code RUNNING} / {@code null}（未执行过）</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("report_schedule")
public class ReportScheduleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 报告名称
     */
    private String reportName;

    /**
     * 报告类型（PENETRATION_TEST/VULNERABILITY_SCAN/ATTACK_CHAIN/TARGET_PROFILE/TASK_SUMMARY）
     */
    private String reportType;

    /**
     * Cron 表达式（Spring 6 风格，6 字段：秒 分 时 日 月 周）
     */
    private String cronExpression;

    /**
     * 收件人邮箱列表（多个以英文逗号分隔）
     */
    private String recipients;

    /**
     * 使用的 Thymeleaf 模板名（可空，空时按 reportType 自动匹配）
     */
    private String templateName;

    /**
     * 关联目标ID（可空）
     */
    private Long targetId;

    /**
     * 状态（ACTIVE/INACTIVE），默认 ACTIVE
     */
    private String status;

    /**
     * 最近一次执行时间
     */
    private LocalDateTime lastRunTime;

    /**
     * 最近一次执行状态（SUCCESS/FAILED/RUNNING）
     */
    private String lastRunStatus;

    /**
     * 推送通道：EMAIL/SLACK/DINGTALK/ALL，默认 EMAIL
     */
    private String webhookType;

    /**
     * 创建人标识
     */
    private String createdBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
