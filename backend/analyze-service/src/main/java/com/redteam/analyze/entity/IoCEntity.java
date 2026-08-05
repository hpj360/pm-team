package com.redteam.analyze.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * IOC（威胁指标）实体
 *
 * <p>存储平台中的恶意指标数据（IP / 域名 / URL / 文件哈希 / 邮箱等），
 * 用于 MISP 集成场景下的指标同步与去重。</p>
 *
 * <p>当前版本采用内存 Mock 数据源（见 {@code IoCServiceImpl}），
 * 与 {@code StixExportController} 的 Mock 数据风格保持一致，
 * 后续可平滑替换为基于 IoCMapper 的持久化实现。</p>
 *
 * <p>IOC 类型常量见 {@link IocType}。</p>
 *
 * @author 红方团队
 */
@Data
public class IoCEntity {

    /**
     * IOC ID
     */
    private Long id;

    /**
     * IOC 类型（IP / DOMAIN / URL / MD5 / SHA256 / EMAIL）
     */
    private String iocType;

    /**
     * IOC 值（如 1.2.3.4 / evil.com / 哈希值）
     */
    private String iocValue;

    /**
     * IOC 描述
     */
    private String description;

    /**
     * 数据来源（如 MISP / STIX / 手动录入 / 沙箱分析）
     */
    private String source;

    /**
     * 威胁等级（1=高 / 2=中 / 3=低 / 4=未定义，对齐 MISP threat_level_id）
     */
    private String threatLevel;

    /**
     * 首次发现时间
     */
    private LocalDateTime firstSeen;

    /**
     * 最近发现时间
     */
    private LocalDateTime lastSeen;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * MISP 事件 ID（同步至 MISP 后回填）
     */
    private String mispEventId;
}
