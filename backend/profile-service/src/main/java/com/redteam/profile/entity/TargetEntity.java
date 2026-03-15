package com.redteam.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 目标实体类
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_target")
public class TargetEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 目标名称
     */
    private String name;

    /**
     * 目标类型（1-个人，2-组织，3-网站，4-IP，5-域名，6-其他）
     */
    private Integer type;

    /**
     * 目标描述
     */
    private String description;

    /**
     * 关联文件数量
     */
    private Integer fileCount;

    /**
     * 标签列表
     */
    private String tags;

    /**
     * 画像数据（JSON格式）
     */
    private String profileData;

    /**
     * 画像状态（0-未生成，1-生成中，2-已生成，3-生成失败）
     */
    private Integer profileStatus;

    /**
     * 风险等级（1-低，2-中，3-高）
     */
    private Integer riskLevel;

    /**
     * 是否关注（0-否，1-是）
     */
    private Integer isFollowed;
}
