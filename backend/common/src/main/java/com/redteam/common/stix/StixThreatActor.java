package com.redteam.common.stix;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * STIX 2.1 ThreatActor（威胁行为者，由 APT 组织转换而来）
 *
 * @author 红方团队
 */
@Data
public class StixThreatActor implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 对象类型，固定为 "threat-actor"
     */
    private String type = "threat-actor";

    /**
     * 唯一标识，格式：threat-actor--{UUID}
     */
    private String id;

    /**
     * 威胁行为者名称
     */
    private String name;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 威胁行为者类型，如 ["nation-state"]
     */
    private List<String> threatActorTypes;

    /**
     * 别名列表
     */
    private List<String> aliases;
}
