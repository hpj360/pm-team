package com.redteam.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 报告草稿生成请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "报告草稿生成请求")
public class GenerateDraftRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 统计数据 JSON（文件数 / 标签分布 / IOC 数等）
     */
    @Schema(description = "统计数据 JSON（文件数 / 标签分布 / IOC 数等）")
    private String statsJson;

    /**
     * 文件列表 JSON（最多 20 条）
     */
    @Schema(description = "文件列表 JSON（最多 20 条）")
    private String fileListJson;

    /**
     * 标签分布 JSON
     */
    @Schema(description = "标签分布 JSON")
    private String tagDistributionJson;
}
