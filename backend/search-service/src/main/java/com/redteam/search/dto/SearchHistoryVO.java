package com.redteam.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检索历史记录 VO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "检索历史记录")
public class SearchHistoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录 ID
     */
    @Schema(description = "记录 ID")
    private Long id;

    /**
     * 用户 ID
     */
    @Schema(description = "用户 ID")
    private Long userId;

    /**
     * 检索类型：KEYWORD / VECTOR / HYBRID
     */
    @Schema(description = "检索类型")
    private String searchType;

    /**
     * 查询文本
     */
    @Schema(description = "查询文本")
    private String queryText;

    /**
     * 过滤条件（JSON 字符串）
     */
    @Schema(description = "过滤条件")
    private String filters;

    /**
     * 命中数量
     */
    @Schema(description = "命中数量")
    private Integer resultCount;

    /**
     * 响应耗时（毫秒）
     */
    @Schema(description = "响应耗时（毫秒）")
    private Long responseTimeMs;

    /**
     * 检索时间
     */
    @Schema(description = "检索时间")
    private LocalDateTime createdAt;
}
