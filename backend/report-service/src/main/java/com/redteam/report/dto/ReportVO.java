package com.redteam.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 报告返回 VO
 *
 * <p>用于报告生成、查询接口的响应数据。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "报告信息")
public class ReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报告ID
     */
    @Schema(description = "报告ID", example = "rpt-3f2a1b8c-...")
    private String reportId;

    /**
     * 报告名称
     */
    @Schema(description = "报告名称", example = "渗透测试报告-2026Q3")
    private String reportName;

    /**
     * 报告类型
     */
    @Schema(description = "报告类型", example = "TASK_SUMMARY")
    private String reportType;

    /**
     * 关联任务ID
     */
    @Schema(description = "关联任务ID")
    private String taskId;

    /**
     * 关联目标ID
     */
    @Schema(description = "关联目标ID")
    private String targetId;

    /**
     * 模板ID
     */
    @Schema(description = "模板ID")
    private String templateId;

    /**
     * 报告格式
     */
    @Schema(description = "报告格式", example = "PDF")
    private String format;

    /**
     * 文件路径
     */
    @Schema(description = "文件存储路径")
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小（字节）", example = "2048576")
    private Long fileSize;

    /**
     * 报告状态
     */
    @Schema(description = "报告状态（PENDING/GENERATING/COMPLETED/FAILED）", example = "COMPLETED")
    private String status;

    /**
     * 生成人ID
     */
    @Schema(description = "生成人ID")
    private Long generatedBy;

    /**
     * 生成时间
     */
    @Schema(description = "生成完成时间")
    private LocalDateTime generatedAt;

    /**
     * 报告摘要（生成完成后自动提取）
     */
    @Schema(description = "报告摘要")
    private String summary;

    /**
     * 元数据 JSON
     */
    @Schema(description = "元数据 JSON")
    private String metadata;

    /**
     * 版本号（每次重新生成递增）
     */
    @Schema(description = "版本号", example = "1")
    private Integer version;

    /**
     * 是否共享（0-否，1-是）
     */
    @Schema(description = "是否共享（0-否，1-是）", example = "0")
    private Integer isShared;

    /**
     * 共享给的用户ID列表（逗号分隔）
     */
    @Schema(description = "共享给的用户ID列表（逗号分隔）")
    private String sharedWith;

    /**
     * 失败原因（生成失败时记录）
     */
    @Schema(description = "失败原因")
    private String failureReason;

    /**
     * 创建时间
     */
    @Schema(description = "记录创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "记录更新时间")
    private LocalDateTime updateTime;
}
