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
 * 工作流定义实体类
 *
 * <p>对应数据库表 {@code workflow_definition}，存储工作流名称、业务类型、节点与边的 JSON 描述。
 * {@code nodes_json}/{@code edges_json} 由 Jackson 序列化为 {@code List<WorkflowNodeDTO>} /
 * {@code List<WorkflowEdgeDTO>}，本实体仅以字符串形式原样存储。</p>
 *
 * <p>业务类型取值：{@code FILE_REVIEW} / {@code TASK_APPROVAL} / {@code REPORT_REVIEW}</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("workflow_definition")
public class WorkflowDefinitionEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工作流名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 业务类型：FILE_REVIEW/TASK_APPROVAL/REPORT_REVIEW
     */
    private String businessType;

    /**
     * 节点定义JSON
     */
    private String nodesJson;

    /**
     * 边定义JSON
     */
    private String edgesJson;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 创建人姓名
     */
    private String createdByName;

    /**
     * 启用：0/1
     */
    private Integer enabled;

    /**
     * 版本号
     */
    private Integer version;

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
