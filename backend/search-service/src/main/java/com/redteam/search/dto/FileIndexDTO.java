package com.redteam.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文件索引 DTO
 *
 * <p>索引文件时传递给检索服务的统一数据结构，包含 ES 索引所需的全字段与 Milvus 向量化所需的文本。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "文件索引数据")
public class FileIndexDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件 ID
     */
    @Schema(description = "文件 ID")
    private Long fileId;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String fileName;

    /**
     * 文件类型（扩展名）
     */
    @Schema(description = "文件类型")
    private String fileType;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    /**
     * 文件 SM3 指纹
     */
    @Schema(description = "文件 SM3 指纹")
    private String fileSm3;

    /**
     * 文本内容（用于 ES 全文索引与 Milvus 向量化）
     */
    @Schema(description = "文本内容")
    private String textContent;

    /**
     * 关联目标 ID
     */
    @Schema(description = "关联目标 ID")
    private Long targetId;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表")
    private List<String> tags;

    /**
     * 敏感等级（1-低，2-中，3-高）
     */
    @Schema(description = "敏感等级")
    private Integer sensitiveLevel;

    /**
     * 是否公开（0-否，1-是）
     */
    @Schema(description = "是否公开")
    private Integer isPublic;

    /**
     * NER 实体列表（key=entityText, value=entityType）
     */
    @Schema(description = "NER 实体列表")
    private List<Map<String, String>> nerEntities;

    /**
     * YARA 匹配列表（key=ruleName, value=severity）
     */
    @Schema(description = "YARA 匹配列表")
    private List<Map<String, String>> yaraMatches;

    /**
     * 上传时间
     */
    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
