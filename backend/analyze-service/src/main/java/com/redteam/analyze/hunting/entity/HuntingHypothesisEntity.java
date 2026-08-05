package com.redteam.analyze.hunting.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 狩猎假设实体（内存模型）
 *
 * <p>记录威胁狩猎的假设、关联 ATT&CK 技术、验证状态与命中清单。</p>
 *
 * <p>状态：{@code DRAFT → VALIDATING → CONFIRMED / REFUTED}</p>
 *
 * @author 红方团队
 */
@Data
public class HuntingHypothesisEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态：草稿
     */
    public static final String STATUS_DRAFT = "DRAFT";

    /**
     * 状态：验证中
     */
    public static final String STATUS_VALIDATING = "VALIDATING";

    /**
     * 状态：已确认（命中）
     */
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    /**
     * 状态：已否定（未命中）
     */
    public static final String STATUS_REFUTED = "REFUTED";

    /**
     * 假设ID
     */
    private String id;

    /**
     * 假设描述
     */
    private String description;

    /**
     * 关联 ATT&CK 技术 ID
     */
    private String techniqueId;

    /**
     * 创建人ID
     */
    private Long userId;

    /**
     * 创建人姓名
     */
    private String userName;

    /**
     * 当前状态
     */
    private String status;

    /**
     * 置信度（0-1）
     */
    private Double confidence;

    /**
     * 命中清单（元素形如 {entityType, entityId, description, score}）
     */
    private List<HuntingHit> hits = new ArrayList<>();

    /**
     * 推荐 IOC 列表
     */
    private List<String> recommendedIocs = new ArrayList<>();

    /**
     * 验证时间
     */
    private LocalDateTime validatedTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 命中项
     */
    @Data
    public static class HuntingHit implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 实体类型（FILE / NETWORK / ENTITY）
         */
        private String entityType;

        /**
         * 实体ID
         */
        private String entityId;

        /**
         * 实体名称
         */
        private String entityName;

        /**
         * 命中描述
         */
        private String description;

        /**
         * 命中评分（0-1）
         */
        private Double score;

        /**
         * 命中证据
         */
        private String evidence;

        public HuntingHit() {
        }

        public HuntingHit(String entityType, String entityId, String entityName,
                          String description, Double score, String evidence) {
            this.entityType = entityType;
            this.entityId = entityId;
            this.entityName = entityName;
            this.description = description;
            this.score = score;
            this.evidence = evidence;
        }
    }
}
