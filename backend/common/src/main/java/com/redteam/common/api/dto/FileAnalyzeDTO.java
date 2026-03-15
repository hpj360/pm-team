package com.redteam.common.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 文件分析请求DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "文件分析请求")
public class FileAnalyzeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @Schema(description = "文件ID")
    private Long fileId;

    /**
     * 文件ID列表（批量分析）
     */
    @Schema(description = "文件ID列表（批量分析）")
    private List<Long> fileIds;

    /**
     * 分析类型（1-敏感信息提取，2-关键词提取，3-实体识别，4-情感分析，5-全文分析）
     */
    @Schema(description = "分析类型（1-敏感信息提取，2-关键词提取，3-实体识别，4-情感分析，5-全文分析）")
    private Integer analyzeType;

    /**
     * 是否生成向量嵌入
     */
    @Schema(description = "是否生成向量嵌入")
    private Boolean generateEmbedding;

    /**
     * 是否提取元数据
     */
    @Schema(description = "是否提取元数据")
    private Boolean extractMetadata;

    /**
     * 是否进行OCR识别
     */
    @Schema(description = "是否进行OCR识别")
    private Boolean enableOcr;

    /**
     * 自定义分析参数（JSON格式）
     */
    @Schema(description = "自定义分析参数（JSON格式）")
    private String customParams;
}
