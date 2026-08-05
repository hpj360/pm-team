package com.redteam.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * Agent 自主分析请求 DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "Agent 自主分析请求")
public class AgentAnalysisRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 自然语言分析请求
     */
    @Schema(description = "自然语言分析请求", example = "分析最近一周与 APT28 相关的钓鱼文件")
    private String query;

    /**
     * 用户ID（可选，默认从上下文获取）
     */
    @Schema(description = "用户ID")
    private Long userId;
}
