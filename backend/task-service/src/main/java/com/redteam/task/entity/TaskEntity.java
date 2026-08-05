package com.redteam.task.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务实体类
 *
 * <p>红方任务管理核心实体，涵盖任务基本信息、状态、优先级、关联资源等。
 * 任务类型支持侦察、利用、投递、后渗透、报告五个阶段。</p>
 *
 * <p>状态机：</p>
 * <ul>
 *   <li>{@code PENDING} - 待执行</li>
 *   <li>{@code RUNNING} - 执行中</li>
 *   <li>{@code PAUSED} - 已暂停</li>
 *   <li>{@code COMPLETED} - 已完成</li>
 *   <li>{@code FAILED} - 已失败</li>
 *   <li>{@code CANCELLED} - 已取消</li>
 * </ul>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("redteam_tasks")
public class TaskEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID（UUID，业务主键，对外暴露的唯一标识）
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 任务名称
     */
    @TableField("task_name")
    private String taskName;

    /**
     * 任务类型：RECON-侦察 / EXPLOIT-利用 / DELIVERY-投递 / POST_EXPLOIT-后渗透 / REPORT-报告
     */
    @TableField("task_type")
    private String taskType;

    /**
     * 任务状态：PENDING-待执行 / RUNNING-执行中 / PAUSED-已暂停 / COMPLETED-已完成 / FAILED-已失败 / CANCELLED-已取消
     */
    @TableField("status")
    private String status;

    /**
     * 优先级（1-5，1最高）
     */
    @TableField("priority")
    private Integer priority;

    /**
     * 关联目标ID
     */
    @TableField("target_id")
    private String targetId;

    /**
     * 关联文件ID（逗号分隔的多个文件ID）
     */
    @TableField("file_ids")
    private String fileIds;

    /**
     * 负责人ID
     */
    @TableField("owner_id")
    private Long ownerId;

    /**
     * 任务截止时间
     */
    @TableField("deadline")
    private LocalDateTime deadline;

    /**
     * 任务进度（0-100，百分比）
     */
    @TableField("progress")
    private Integer progress;

    /**
     * 开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 任务描述
     */
    @TableField("description")
    private String description;
}
