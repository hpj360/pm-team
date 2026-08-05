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
 * 审批意见实体类
 *
 * <p>对应数据库表 {@code workflow_review}，记录每次审批决定及意见。</p>
 *
 * <p>决定取值：{@code APPROVE} / {@code REJECT}</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("workflow_review")
public class WorkflowReviewEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 实例ID
     */
    private Long instanceId;

    /**
     * 节点ID
     */
    private String nodeId;

    /**
     * 审批人ID
     */
    private Long reviewerId;

    /**
     * 审批人姓名
     */
    private String reviewerName;

    /**
     * 决定：APPROVE/REJECT
     */
    private String decision;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
