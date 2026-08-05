package com.redteam.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 目标画像 DTO
 *
 * <p>聚合目标基本信息、组织架构、技术资产、攻击面、历史事件、关联目标等完整画像数据。
 * 用于前端画像详情页及画像导出。</p>
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
     * 所属行业
     */
    @Schema(description = "所属行业")
    private String industry;

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
     * 风险等级（1-低，2-中，3-高）
     */
    @Schema(description = "风险等级")
    private Integer riskLevel;

    /**
     * 画像状态（0-未生成，1-生成中，2-已生成，3-生成失败）
     */
    @Schema(description = "画像状态")
    private Integer profileStatus;

    /**
     * 是否关注
     */
    @Schema(description = "是否关注")
    private Integer isFollowed;

    /**
     * 基本信息（动态字段，来自 t_target.basic_info 或解析自实体字段）
     */
    @Schema(description = "基本信息")
    private Map<String, Object> basicInfo;

    /**
     * 组织架构（解析自 orgStructure JSON）
     */
    @Schema(description = "组织架构")
    private List<Map<String, Object>> orgStructure;

    /**
     * 技术资产（解析自 techAssets JSON）
     */
    @Schema(description = "技术资产")
    private List<TechAsset> techAssets;

    /**
     * 攻击面（解析自 attackSurface JSON）
     */
    @Schema(description = "攻击面")
    private AttackSurface attackSurface;

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
     * 历史事件
     */
    @Schema(description = "历史事件")
    private List<HistoryEvent> historyEvents;

    /**
     * 关联文件 ID 列表
     */
    @Schema(description = "关联文件ID列表")
    private List<Long> relatedFileIds;

    /**
     * 关联 IOC 列表
     */
    @Schema(description = "关联IOC列表")
    private List<String> relatedIocs;

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
     * 技术资产信息
     */
    @Data
    @Schema(description = "技术资产")
    public static class TechAsset implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "资产类型（DOMAIN/IP/CERT/SUBDOMAIN/URL）")
        private String assetType;

        @Schema(description = "资产值")
        private String value;

        @Schema(description = "首次发现时间")
        private LocalDateTime firstSeen;

        @Schema(description = "最后发现时间")
        private LocalDateTime lastSeen;

        @Schema(description = "来源")
        private String source;
    }

    /**
     * 攻击面信息
     */
    @Data
    @Schema(description = "攻击面")
    public static class AttackSurface implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "开放端口列表")
        private List<Integer> openPorts;

        @Schema(description = "暴露服务列表")
        private List<String> services;

        @Schema(description = "入口点列表")
        private List<String> entryPoints;

        @Schema(description = "风险评分")
        private Double riskScore;
    }

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
     * 历史事件
     */
    @Data
    @Schema(description = "历史事件")
    public static class HistoryEvent implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "事件ID")
        private Long eventId;

        @Schema(description = "事件时间")
        private LocalDateTime eventTime;

        @Schema(description = "事件类型")
        private String eventType;

        @Schema(description = "事件描述")
        private String description;

        @Schema(description = "来源文件ID")
        private Long fileId;
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
