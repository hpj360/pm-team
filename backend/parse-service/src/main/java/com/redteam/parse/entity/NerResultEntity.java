package com.redteam.parse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * NER 实体识别结果实体类
 *
 * <p>对应数据库表 t_ner_result，存储 security-BERT 或正则兜底识别出的安全实体。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_ner_result")
public class NerResultEntity extends BaseEntity {

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
     * 实体文本
     */
    private String entityText;

    /**
     * 实体类型：IP/DOMAIN/URL/EMAIL/HASH/CVE/TOOL/EXPLOIT
     */
    private String entityType;

    /**
     * 实体标签
     */
    private String entityLabel;

    /**
     * 起始位置
     */
    private Integer startPos;

    /**
     * 结束位置
     */
    private Integer endPos;

    /**
     * 置信度 0-1
     */
    private Float confidence;
}
