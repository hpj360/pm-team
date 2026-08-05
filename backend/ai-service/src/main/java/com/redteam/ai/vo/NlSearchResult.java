package com.redteam.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 自然语言搜索结果 VO
 *
 * <p>封装自然语言搜索的完整结果，包括原始自然语言输入、LLM 解析的结构化条件、
 * search-service 返回的搜索结果，以及降级/错误信息。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "自然语言搜索结果")
public class NlSearchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 原始自然语言输入
     */
    @Schema(description = "原始自然语言输入")
    private String naturalLanguageQuery;

    /**
     * LLM 解析的结构化搜索条件
     */
    @Schema(description = "LLM 解析的结构化搜索条件")
    private Map<String, Object> parsedConditions;

    /**
     * 搜索结果列表（来自 search-service）
     */
    @Schema(description = "搜索结果列表")
    private List<Map<String, Object>> results;

    /**
     * 命中总数
     */
    @Schema(description = "命中总数")
    private Long total;

    /**
     * 是否使用了 LLM 解析（false 表示降级为简单关键词搜索）
     */
    @Schema(description = "是否使用了 LLM 解析")
    private boolean llmUsed;

    /**
     * 错误信息（LLM 降级、search-service 不可用等）
     */
    @Schema(description = "错误信息")
    private String errorMessage;
}
