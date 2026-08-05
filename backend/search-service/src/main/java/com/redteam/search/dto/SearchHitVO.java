package com.redteam.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 检索命中结果 VO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "检索命中结果")
public class SearchHitVO implements Serializable {

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
     * 文件类型
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
     * 高亮字段（key=字段名，value=高亮片段列表）
     */
    @Schema(description = "高亮字段")
    private Map<String, List<String>> highlight;

    /**
     * 相关度评分（混合检索为 RRF 融合分数）
     */
    @Schema(description = "相关度评分")
    private Float score;

    /**
     * 命中的检索类型：KEYWORD / VECTOR / HYBRID
     */
    @Schema(description = "命中的检索类型")
    private String searchType;

    /**
     * 上传时间
     */
    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    /**
     * 目标 ID
     */
    @Schema(description = "目标 ID")
    private Long targetId;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表")
    private List<String> tags;
}
