package com.redteam.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 目标画像DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "目标画像")
public class TargetProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标ID
     */
    @Schema(description = "目标ID")
    private Long id;

    /**
     * 目标名称
     */
    @Schema(description = "目标名称")
    private String name;

    /**
     * 目标类型
     */
    @Schema(description = "目标类型")
    private Integer type;

    /**
     * 目标描述
     */
    @Schema(description = "目标描述")
    private String description;

    /**
     * 关联文件数量
     */
    @Schema(description = "关联文件数量")
    private Integer fileCount;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表")
    private List<String> tags;

    /**
     * 风险等级
     */
    @Schema(description = "风险等级")
    private Integer riskLevel;

    /**
     * 基本信息
     */
    @Schema(description = "基本信息")
    private Map<String, Object> basicInfo;

    /**
     * 联系方式
     */
    @Schema(description = "联系方式")
    private List<ContactInfo> contacts;

    /**
     * 关联目标
     */
    @Schema(description = "关联目标")
    private List<RelatedTarget> relatedTargets;

    /**
     * 活动时间线
     */
    @Schema(description = "活动时间线")
    private List<ActivityTimeline> timeline;

    /**
     * 关键词云
     */
    @Schema(description = "关键词云")
    private List<KeywordWeight> keywordCloud;

    /**
     * 文件类型分布
     */
    @Schema(description = "文件类型分布")
    private Map<String, Integer> fileTypeDistribution;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 联系方式信息
     */
    @Data
    @Schema(description = "联系方式信息")
    public static class ContactInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "类型（1-邮箱，2-手机，3-地址，4-其他）")
        private Integer type;

        @Schema(description = "内容")
        private String content;

        @Schema(description = "来源")
        private String source;
    }

    /**
     * 关联目标
     */
    @Data
    @Schema(description = "关联目标")
    public static class RelatedTarget implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "目标ID")
        private Long targetId;

        @Schema(description = "目标名称")
        private String targetName;

        @Schema(description = "关联类型")
        private String relationType;

        @Schema(description = "关联强度")
        private Double strength;
    }

    /**
     * 活动时间线
     */
    @Data
    @Schema(description = "活动时间线")
    public static class ActivityTimeline implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "时间")
        private LocalDateTime time;

        @Schema(description = "事件描述")
        private String event;

        @Schema(description = "来源文件ID")
        private Long fileId;
    }

    /**
     * 关键词权重
     */
    @Data
    @Schema(description = "关键词权重")
    public static class KeywordWeight implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "关键词")
        private String keyword;

        @Schema(description = "权重")
        private Integer weight;
    }
}
