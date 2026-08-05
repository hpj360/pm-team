package com.redteam.parse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * YARA 扫描结果实体类
 *
 * <p>对应数据库表 t_yara_scan_result，记录单条规则对单个文件的扫描结果。
 * 通过 (file_id, rule_id) 唯一约束实现幂等。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yara_scan_result")
public class YaraScanResultEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 规则名称
     */
    private String ruleName;

    /**
     * 是否匹配
     */
    private Boolean matched;

    /**
     * 匹配字符串 JSON 数组
     */
    private String matchedStrings;

    /**
     * 严重级别
     */
    private String severity;

    /**
     * 规则类别
     */
    private String category;

    /**
     * 扫描时间
     */
    private LocalDateTime scannedAt;
}
