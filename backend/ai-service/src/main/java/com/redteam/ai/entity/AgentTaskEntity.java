package com.redteam.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent 自主分析任务实体类
 *
 * <p>对应数据库表 ai_agent_task，记录 Agent 自主分析任务的查询、状态、结论、
 * 证据链、引用文件、置信度及推理轨迹。</p>
 *
 * <p>状态流转：PENDING → RUNNING → COMPLETED / FAILED</p>
 *
 * @author 红方团队
 */
@Data
@TableName("ai_agent_task")
public class AgentTaskEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID（UUID 字符串，主键）
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String taskId;

    /**
     * 用户分析请求（自然语言）
     */
    private String query;

    /**
     * 提交用户ID
     */
    private Long userId;

    /**
     * 任务状态：PENDING / RUNNING / COMPLETED / FAILED
     */
    private String status;

    /**
     * 最终结论
     */
    private String conclusion;

    /**
     * 证据链 JSON 数组
     */
    private String evidenceChainJson;

    /**
     * 引用文件 JSON 数组
     */
    private String referencedFilesJson;

    /**
     * 置信度（0.0 ~ 1.0）
     */
    private Double confidence;

    /**
     * 推理轨迹 JSON 数组
     */
    private String tracesJson;

    /**
     * 错误信息（FAILED 状态时填充）
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}
