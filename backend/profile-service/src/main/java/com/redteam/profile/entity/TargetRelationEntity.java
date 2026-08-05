package com.redteam.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 目标关系实体类
 *
 * <p>对应数据库表 {@code t_target_relation}，记录目标与目标之间的关系，用于关系图谱构建。</p>
 *
 * <p>关系类型示例：</p>
 * <ul>
 *   <li>{@code AFFILIATED} - 隶属关系（个人→组织）</li>
 *   <li>{@code SUBDOMAIN} - 子域名关系（子域名→主域名）</li>
 *   <li>{@code RESOLVES_TO} - 解析关系（域名→IP）</li>
 *   <li>{@code RELATED} - 一般关联</li>
 *   <li>{@code OWNS} - 持有关系（个人→资产）</li>
 * </ul>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_target_relation")
public class TargetRelationEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 源目标ID
     */
    private Long sourceId;

    /**
     * 目标目标ID
     */
    private Long targetId;

    /**
     * 关系类型（AFFILIATED/SUBDOMAIN/RESOLVES_TO/RELATED/OWNS 等）
     */
    private String relationType;

    /**
     * 关系权重（0.0-1.0，表示关联强度）
     */
    private Double weight;

    /**
     * 关系描述
     */
    private String description;
}
