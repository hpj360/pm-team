package com.redteam.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 知识库索引请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "知识库索引请求")
public class KnowledgeIndexRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文档标题
     */
    @Schema(description = "文档标题")
    private String title;

    /**
     * 文档内容
     */
    @Schema(description = "文档内容")
    private String content;

    /**
     * 来源（ATT&CK / CVE / APT / REPORT）
     */
    @Schema(description = "来源")
    private String source;

    /**
     * 元数据
     */
    @Schema(description = "元数据键值对")
    private Map<String, Object> metadata;
}
