package com.redteam.common.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * NER 实体识别结果 DTO（公共模块版本）
 *
 * <p>红方重点关注的实体类型：IP/域名/URL/邮箱/哈希/CVE/漏洞/漏洞利用代码。
 * 用于跨服务传递 NER 识别结果，例如 ai-service 接收 parse-service 识别结果生成威胁摘要。</p>
 *
 * <p>注：parse-service 中存在同名 {@code com.redteam.parse.dto.NerEntityVO}，
 * 本类用于在不依赖 parse-service 的服务（如 ai-service）中传递 NER 结果。</p>
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
