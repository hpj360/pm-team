package com.redteam.common.stix;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * STIX 2.1 Bundle（顶层容器）
 *
 * <p>STIX 2.1 规范要求 Bundle 顶层结构为：
 * {@code {"type":"bundle","id":"bundle--UUID","objects":[...]}}。</p>
 *
 * <p>objects 字段使用 {@code List<Map<String, Object>>} 以容纳不同类型的 STIX 对象
 * （indicator / threat-actor / attack-pattern / relationship 等）。</p>
 *
 * @author 红方团队
 */
@Data
public class StixBundle implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 对象类型，固定为 "bundle"
     */
    private String type = "bundle";

    /**
     * Bundle 唯一标识，格式：bundle--{UUID}
     */
    private String id;

    /**
     * Bundle 包含的 STIX 对象列表
     */
    private List<Map<String, Object>> objects;
}
