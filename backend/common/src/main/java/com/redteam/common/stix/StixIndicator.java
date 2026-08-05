package com.redteam.common.stix;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * STIX 2.1 Indicator（指标对象，由 IOC 转换而来）
 *
 * <p>对应 STIX 2.1 中的 indicator SDO，包含基于模式表达的可观察对象。</p>
 *
 * @author 红方团队
 */
@Data
public class StixIndicator implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 对象类型，固定为 "indicator"
     */
    private String type = "indicator";

    /**
     * 唯一标识，格式：indicator--{UUID}
     */
    private String id;

    /**
     * 指标名称
     */
    private String name;

    /**
     * 描述信息
     */
    private String description;

    /**
     * STIX 模式表达式，如 "[ipv4-addr:value = '1.2.3.4']"
     */
    private String pattern;

    /**
     * 模式类型，固定为 "stix"
     */
    private String patternType = "stix";

    /**
     * 有效起始时间（ISO 8601 格式）
     */
    private String validFrom;

    /**
     * 标签列表，如 ["malicious-activity"]
     */
    private List<String> labels;
}
