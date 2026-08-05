package com.redteam.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 目标实体类
 *
 * <p>对应数据库表 {@code t_target}，记录红方行动目标的基本信息、攻击面、技术资产等。
 * 目标类型支持个人、组织、网站、IP、域名、其他六类。</p>
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
     * 所属行业（金融/互联网/能源/制造业/政府/教育/医疗/其他）
     */
    private String industry;

    /**
     * 目标描述
     */
    private String description;

    /**
     * 攻击面信息（JSON 字符串，记录暴露的端口、服务、入口点等）
     */
    private String attackSurface;

    /**
     * 技术资产信息（JSON 字符串，记录域名、IP、证书、子域名等）
     */
    private String techAssets;

    /**
     * 组织架构（JSON 字符串，记录人员、岗位、汇报关系等）
     */
    private String orgStructure;

    /**
     * 关联文件数量
     */
    private Integer fileCount;

    /**
     * 标签列表（逗号分隔）
     */
    private String tags;

    /**
     * 画像数据（JSON 格式）
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
