package com.redteam.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批实例实体类
 *
 * <p>对应数据库表 {@code workflow_instance}，记录每笔业务的审批实例当前状态及所处节点。</p>
 *
 * <p>状态取值：{@code PENDING} / {@code APPROVED} / {@code REJECTED} / {@code CANCELLED}</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("workflow_instance")
public class WorkflowInstanceEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工作流定义ID
     */
    private Long workflowId;

    /**
     * 工作流名称（冗余，便于查询展示）
     */
    private String workflowName;

    /**
     * 业务ID（如文件ID/任务ID）
     */
    private String businessId;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 提交人ID
     */
    private Long submitterId;

    /**
     * 提交人姓名
     */
    private String submitterName;

    /**
     * 状态：PENDING/APPROVED/REJECTED/CANCELLED
     */
    private String status;

    /**
     * 当前节点ID
     */
    private String currentNodeId;

    /**
     * 当前节点名称
     */
    private String currentNodeName;

    /**
     * 当前审批人ID列表（逗号分隔）
     */
    private String currentApprovers;

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
