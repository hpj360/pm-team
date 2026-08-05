package com.redteam.common.stix;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * STIX 2.1 AttackPattern（攻击模式，由 TTP 转换而来）
 *
 * @author 红方团队
 */
@Data
public class StixAttackPattern implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 对象类型，固定为 "attack-pattern"
     */
    private String type = "attack-pattern";

    /**
     * 唯一标识，格式：attack-pattern--{UUID}
     */
    private String id;

    /**
     * 攻击模式名称
     */
    private String name;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 外部引用列表（如 MITRE ATT&CK 引用）
     */
    private List<String> externalReferences;
}
