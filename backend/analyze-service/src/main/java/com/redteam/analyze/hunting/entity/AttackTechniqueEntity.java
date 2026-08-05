package com.redteam.analyze.hunting.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * ATT&CK 技术实体（内存模型）
 *
 * <p>对应 MITRE ATT&CK Enterprise 矩阵的技术节点。当前版本采用内置数据初始化
 * （见 {@code AttackMatrixInitializer}），后续可平滑替换为基于数据库的实现。</p>
 *
 * <p>覆盖 14 战术 × N 技术（子集，可扩展至完整 193 技术）。</p>
 *
 * @author 红方团队
 */
@Data
public class AttackTechniqueEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 技术 ID（如 T1059）
     */
    private String techniqueId;

    /**
     * 技术名称
     */
    private String name;

    /**
     * 技术描述
     */
    private String description;

    /**
     * 所属战术（tactic，如 execution / persistence）
     */
    private String tactic;

    /**
     * 战术中文名（如 执行 / 持久化）
     */
    private String tacticName;

    /**
     * 子技术 ID（如 T1059.001），可为空
     */
    private String subTechniqueId;

    /**
     * 是否子技术
     */
    private boolean subTechnique;

    /**
     * 数据源（用于狩猎假设检索）
     */
    private String dataSource;

    public AttackTechniqueEntity() {
    }

    public AttackTechniqueEntity(String techniqueId, String name, String description,
                                 String tactic, String tacticName, String dataSource) {
        this.techniqueId = techniqueId;
        this.name = name;
        this.description = description;
        this.tactic = tactic;
        this.tacticName = tacticName;
        this.dataSource = dataSource;
        this.subTechnique = techniqueId != null && techniqueId.contains(".");
    }
}
