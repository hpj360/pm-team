package com.redteam.parse.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * NER 实体识别结果 VO
 *
 * <p>红方重点关注的实体类型：IP/域名/URL/邮箱/哈希/CVE/漏洞/漏洞利用代码。</p>
 *
 * @author 红方团队
 */
@Data
public class NerEntityVO implements Serializable {

    private static final long serialVersionUID = 1L;

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
