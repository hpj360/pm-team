package com.redteam.analyze.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 沙箱分析报告视图对象
 *
 * <p>封装 Cuckoo 沙箱分析报告的关键信息，沙箱不可用时承载降级结果。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "沙箱分析报告")
public class SandboxReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 沙箱任务ID
     */
    @Schema(description = "沙箱任务ID")
    private String taskId;

    /**
     * 文件ID
     */
    @Schema(description = "文件ID")
    private Long fileId;

    /**
     * 分析状态（PENDING/RUNNING/COMPLETED/FAILED/DEGRADED）
     */
    @Schema(description = "分析状态")
    private String status;

    /**
     * 威胁评分（0-10，越高越危险）
     */
    @Schema(description = "威胁评分")
    private Double score;

    /**
     * 报告摘要
     */
    @Schema(description = "报告摘要")
    private String summary;

    /**
     * 威胁名称列表
     */
    @Schema(description = "威胁名称列表")
    private List<String> threats;

    /**
     * YARA 命中签名
     */
    @Schema(description = "YARA命中签名")
    private List<String> signatures;

    /**
     * 网络行为信息
     */
    @Schema(description = "网络行为信息")
    private Map<String, Object> networkInfo;

    /**
     * 静态分析信息（降级时填充）
     */
    @Schema(description = "静态分析信息")
    private Map<String, Object> staticInfo;

    /**
     * 是否降级结果
     */
    @Schema(description = "是否降级结果")
    private Boolean degraded;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMessage;
}
