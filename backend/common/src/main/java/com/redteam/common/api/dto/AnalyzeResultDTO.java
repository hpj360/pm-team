package com.redteam.common.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文件分析结果DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "文件分析结果")
public class AnalyzeResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分析任务ID
     */
    @Schema(description = "分析任务ID")
    private Long taskId;

    /**
     * 文件ID
     */
    @Schema(description = "文件ID")
    private Long fileId;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String filename;

    /**
     * 分析类型
     */
    @Schema(description = "分析类型")
    private Integer analyzeType;

    /**
     * 分析状态（0-待分析，1-分析中，2-已完成，3-失败）
     */
    @Schema(description = "分析状态（0-待分析，1-分析中，2-已完成，3-失败）")
    private Integer status;

    /**
     * 分析进度（0-100）
     */
    @Schema(description = "分析进度（0-100）")
    private Integer progress;

    /**
     * 敏感信息列表
     */
    @Schema(description = "敏感信息列表")
    private List<SensitiveInfo> sensitiveInfos;

    /**
     * 关键词列表
     */
    @Schema(description = "关键词列表")
    private List<KeywordInfo> keywords;

    /**
     * 实体列表
     */
    @Schema(description = "实体列表")
    private List<EntityInfo> entities;

    /**
     * 情感分析结果
     */
    @Schema(description = "情感分析结果")
    private SentimentInfo sentiment;

    /**
     * 文本摘要
     */
    @Schema(description = "文本摘要")
    private String summary;

    /**
     * 元数据
     */
    @Schema(description = "元数据")
    private Map<String, Object> metadata;

    /**
     * OCR识别文本
     */
    @Schema(description = "OCR识别文本")
    private String ocrText;

    /**
     * 向量嵌入ID
     */
    @Schema(description = "向量嵌入ID")
    private String embeddingId;

    /**
     * 分析耗时（毫秒）
     */
    @Schema(description = "分析耗时（毫秒）")
    private Long duration;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMessage;

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

    /**
     * 敏感信息
     */
    @Data
    @Schema(description = "敏感信息")
    public static class SensitiveInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 敏感信息类型（1-邮箱，2-手机号，3-身份证，4-银行卡，5-IP地址，6-域名，7-其他）
         */
        @Schema(description = "敏感信息类型")
        private Integer type;

        /**
         * 敏感信息内容
         */
        @Schema(description = "敏感信息内容")
        private String content;

        /**
         * 位置（起始位置）
         */
        @Schema(description = "位置（起始位置）")
        private Integer position;

        /**
         * 置信度（0-1）
         */
        @Schema(description = "置信度（0-1）")
        private Double confidence;
    }

    /**
     * 关键词信息
     */
    @Data
    @Schema(description = "关键词信息")
    public static class KeywordInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 关键词
         */
        @Schema(description = "关键词")
        private String keyword;

        /**
         * 词频
         */
        @Schema(description = "词频")
        private Integer frequency;

        /**
         * 权重
         */
        @Schema(description = "权重")
        private Double weight;
    }

    /**
     * 实体信息
     */
    @Data
    @Schema(description = "实体信息")
    public static class EntityInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 实体类型（1-人名，2-地名，3-机构名，4-时间，5-其他）
         */
        @Schema(description = "实体类型")
        private Integer type;

        /**
         * 实体名称
         */
        @Schema(description = "实体名称")
        private String name;

        /**
         * 出现次数
         */
        @Schema(description = "出现次数")
        private Integer count;
    }

    /**
     * 情感分析信息
     */
    @Data
    @Schema(description = "情感分析信息")
    public static class SentimentInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 情感倾向（1-正面，2-负面，3-中性）
         */
        @Schema(description = "情感倾向（1-正面，2-负面，3-中性）")
        private Integer sentiment;

        /**
         * 情感得分（-1到1）
         */
        @Schema(description = "情感得分（-1到1）")
        private Double score;

        /**
         * 置信度
         */
        @Schema(description = "置信度")
        private Double confidence;
    }
}
