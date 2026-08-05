package com.redteam.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报告草稿 VO
 *
 * <p>封装 LLM 生成的报告结论段落与建议行动，包含调用元数据（模型、token 用量、
 * 是否使用 LLM）及降级/错误信息。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "报告草稿")
public class ReportDraft implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报告ID
     */
    @Schema(description = "报告ID")
    private Long reportId;

    /**
     * LLM 生成的结论段落
     */
    @Schema(description = "LLM 生成的结论段落")
    private String conclusion;

    /**
     * 建议行动列表
     */
    @Schema(description = "建议行动列表")
    private List<String> recommendations;

    /**
     * 使用的模型名称
     */
    @Schema(description = "使用的模型名称")
    private String model;

    /**
     * 消耗的 token 数
     */
    @Schema(description = "消耗的 token 数")
    private Integer tokensUsed;

    /**
     * 是否使用了 LLM 生成（false 表示降级为模板文本）
     */
    @Schema(description = "是否使用了 LLM 生成")
    private boolean llmUsed;

    /**
     * 错误信息（LLM 降级、JSON 解析失败等）
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
