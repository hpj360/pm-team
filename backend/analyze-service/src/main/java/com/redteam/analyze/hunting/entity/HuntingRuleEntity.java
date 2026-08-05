package com.redteam.analyze.hunting.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 狩猎规则实体（内存模型）
 *
 * <p>支持 Sigma 规则与 YARA 规则两类，与 ATT&CK 技术双向关联。</p>
 *
 * @author 红方团队
 */
@Data
public class HuntingRuleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 规则类型：Sigma
     */
    public static final String TYPE_SIGMA = "SIGMA";

    /**
     * 规则类型：YARA
     */
    public static final String TYPE_YARA = "YARA";

    /**
     * 规则ID
     */
    private String id;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 规则类型（SIGMA / YARA）
     */
    private String type;

    /**
     * 规则内容（源码）
     */
    private String content;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 作者
     */
    private String author;

    /**
     * 严重等级（info/low/medium/high/critical）
     */
    private String severity;

    /**
     * 标签
     */
    private List<String> tags = new ArrayList<>();

    /**
     * 关联 ATT&CK 技术 ID 列表（双向关联）
     */
    private List<String> attackTechniqueIds = new ArrayList<>();

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 版本号（YARA 版本管理）
     */
    private Integer version = 1;

    /**
     * 命中次数
     */
    private Integer matchCount = 0;

    /**
     * 测试次数
     */
    private Integer testCount = 0;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 最近命中时间
     */
    private LocalDateTime lastMatchTime;
}
