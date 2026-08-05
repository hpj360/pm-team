package com.redteam.parse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * YARA 规则实体类
 *
 * <p>对应数据库表 t_yara_rule，存储 YARA 规则源码、类别与启用状态。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yara_rule")
public class YaraRuleEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 规则名称（唯一）
     */
    private String ruleName;

    /**
     * 规则内容（YARA 规则源码）
     */
    private String ruleContent;

    /**
     * 规则内容哈希（用于检测变更、缓存编译产物）
     */
    private String ruleHash;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 严重级别：INFO/LOW/MEDIUM/HIGH/CRITICAL
     */
    private String severity;

    /**
     * 规则类别：MALWARE/EXPLOIT/LEAK/CREDENTIAL/OTHER
     */
    private String category;

    /**
     * 是否启用
     */
    private Boolean enabled;
}
