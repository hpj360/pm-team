package com.redteam.analyze.hunting;

import com.redteam.analyze.hunting.entity.HuntingHypothesisEntity;
import lombok.Data;

import java.util.List;

/**
 * 狩猎假设详情 VO（含验证结果与 ATT&CK 技术元数据）
 *
 * @author 红方团队
 */
@Data
public class HypothesisVO {

    /**
     * 假设ID
     */
    private String id;

    /**
     * 假设描述
     */
    private String description;

    /**
     * 关联 ATT&CK 技术 ID
     */
    private String techniqueId;

    /**
     * 关联 ATT&CK 技术名称
     */
    private String techniqueName;

    /**
     * 关联 ATT&CK 战术
     */
    private String tactic;

    /**
     * 战术中文名
     */
    private String tacticName;

    /**
     * 创建人ID
     */
    private Long userId;

    /**
     * 创建人姓名
     */
    private String userName;

    /**
     * 当前状态
     */
    private String status;

    /**
     * 置信度（0-1）
     */
    private Double confidence;

    /**
     * 命中清单
     */
    private List<HuntingHypothesisEntity.HuntingHit> hits;

    /**
     * 推荐 IOC 列表
     */
    private List<String> recommendedIocs;

    /**
     * 验证时间（ISO 字符串）
     */
    private String validatedTime;

    /**
     * 创建时间（ISO 字符串）
     */
    private String createTime;

    /**
     * 更新时间（ISO 字符串）
     */
    private String updateTime;
}
