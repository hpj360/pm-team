package com.redteam.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件检索请求 DTO
 *
 * <p>统一封装关键字 / 向量 / 混合三种检索类型的入参，由 {@code searchType} 决定检索路由。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "文件检索请求")
public class SearchRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 检索类型：KEYWORD / VECTOR / HYBRID
     */
    public static final String TYPE_KEYWORD = "KEYWORD";
    /**
     * 检索类型：向量检索
     */
    public static final String TYPE_VECTOR = "VECTOR";
    /**
     * 检索类型：混合检索
     */
    public static final String TYPE_HYBRID = "HYBRID";

    /**
     * 查询关键字（KEYWORD / HYBRID 必填；VECTOR 可选作为兜底）
     */
    @Schema(description = "查询关键字")
    private String query;

    /**
     * 检索类型：KEYWORD / VECTOR / HYBRID
     */
    @Schema(description = "检索类型", defaultValue = "KEYWORD")
    private String searchType = TYPE_KEYWORD;

    /**
     * 文件类型过滤
     */
    @Schema(description = "文件类型过滤")
    private String fileType;

    /**
     * 目标 ID 过滤
     */
    @Schema(description = "目标 ID 过滤")
    private Long targetId;

    /**
     * 敏感等级过滤（1-低，2-中，3-高）
     */
    @Schema(description = "敏感等级过滤")
    private Integer sensitiveLevel;

    /**
     * 标签过滤（多标签为 OR 关系）
     */
    @Schema(description = "标签过滤")
    private List<String> tags;

    /**
     * 起始日期（含）
     */
    @Schema(description = "起始日期")
    private LocalDateTime dateFrom;

    /**
     * 结束日期（含）
     */
    @Schema(description = "结束日期")
    private LocalDateTime dateTo;

    /**
     * 页码（从 1 开始）
     */
    @Schema(description = "页码", defaultValue = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小", defaultValue = "10")
    @Min(value = 1, message = "每页大小不能小于 1")
    @Max(value = 100, message = "每页大小不能超过 100")
    private Integer pageSize = 10;

    /**
     * 最小相关度（向量检索用，0-1）
     */
    @Schema(description = "最小相关度（向量检索用）")
    private Float minScore;

    /**
     * 当前操作用户 ID（由网关注入）
     */
    @Schema(description = "操作用户 ID", hidden = true)
    private Long userId;

    /**
     * 布尔组合条件列表（与 query 字段配合使用，支持 AND/OR/NOT 逻辑组合）
     */
    @Schema(description = "布尔组合条件列表（AND/OR/NOT）")
    private List<BooleanCondition> booleanConditions;

    /**
     * 二次检索关键词（在已有结果集中进一步筛选）
     */
    @Schema(description = "二次检索关键词")
    private String refineQuery;

    /**
     * 二次检索范围（文件ID列表，限定搜索范围）
     */
    @Schema(description = "二次检索文件ID范围")
    private List<Long> refineFileIds;

    /**
     * 布尔组合条件
     */
    @Data
    @Schema(description = "布尔组合条件")
    public static class BooleanCondition implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 逻辑操作符：AND / OR / NOT */
        @Schema(description = "逻辑操作符", allowableValues = {"AND", "OR", "NOT"})
        private String logic;

        /** 搜索字段：fileName / textContent / tags / fileType */
        @Schema(description = "搜索字段", allowableValues = {"fileName", "textContent", "tags", "fileType"})
        private String field;

        /** 搜索值 */
        @Schema(description = "搜索值")
        private String value;

        public static final String LOGIC_AND = "AND";
        public static final String LOGIC_OR = "OR";
        public static final String LOGIC_NOT = "NOT";
    }
}
