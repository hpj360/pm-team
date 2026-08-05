package com.redteam.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 数据脱敏规则实体类
 *
 * <p>对应数据库表 {@code data_masking_rule}，存储按密级分级的脱敏规则。
 * 规则类型支持 PHONE/IDCARD/IP/EMAIL/CUSTOM，通过正则表达式 + 替换模板执行脱敏。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("data_masking_rule")
public class DataMaskingRuleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 规则类型：PHONE/IDCARD/IP/EMAIL/CUSTOM
     */
    private String ruleType;

    /**
     * 正则表达式
     */
    private String pattern;

    /**
     * 替换模板，如 $1****$2
     */
    private String replacement;

    /**
     * 适用密级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET
     */
    private String classificationLevel;

    /**
     * 启用状态：0禁用 1启用
     */
    private Integer enabled;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
