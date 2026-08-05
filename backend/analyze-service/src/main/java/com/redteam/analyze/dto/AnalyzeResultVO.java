package com.redteam.analyze.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分析结果视图对象
 *
 * <p>对外暴露的分析结果 VO，封装任务元信息与结果详情。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "分析结果视图")
public class AnalyzeResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @Schema(description = "任务ID")
    private Long taskId;

    /**
     * 文件ID
     */
    @Schema(description = "文件ID")
    private Long fileId;

    /**
     * 分析类型
     */
    @Schema(description = "分析类型")
    private Integer analyzeType;

    /**
     * 分析状态（0-待分析，1-分析中，2-已完成，3-失败）
     */
    @Schema(description = "分析状态")
    private Integer status;

    /**
     * 分析进度（0-100）
     */
    @Schema(description = "分析进度")
    private Integer progress;

    /**
     * 分析结果 JSON
     */
    @Schema(description = "分析结果JSON")
    private String resultJson;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    /**
     * 分析耗时（毫秒）
     */
    @Schema(description = "分析耗时（毫秒）")
    private Long duration;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 完成时间
     */
    @Schema(description = "完成时间")
    private LocalDateTime finishTime;
}
