package com.redteam.analyze.service;

import com.redteam.analyze.client.MispClient;
import com.redteam.analyze.config.MispProperties;
import com.redteam.analyze.dto.MispAttribute;
import com.redteam.analyze.dto.MispEvent;
import com.redteam.analyze.entity.IoCEntity;
import com.redteam.analyze.entity.IocType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MISP 同步服务
 *
 * <p>负责平台 IOC 与 MISP 事件的双向同步：</p>
 * <ul>
 *   <li>{@link #syncIocToMisp(Long)}：单个 IOC 推送至 MISP（创建 Event + Attribute）</li>
 *   <li>{@link #syncAllIocsToMisp()}：定时批量推送（默认每日 02:00）</li>
 *   <li>{@link #pullMispEvents()}：定时拉取 MISP 事件写入平台 IOC 库（默认每小时）</li>
 * </ul>
 *
 * <p>当 {@link MispProperties#isEnabled()} 为 false 时，所有同步方法静默返回，
 * 不抛异常，用于 MISP 不可用场景的优雅降级。</p>
 *
 * <p>IOC 类型 → MISP attribute type 映射：</p>
 * <ul>
 *   <li>IP → ip-src</li>
 *   <li>DOMAIN → domain</li>
 *   <li>URL → url</li>
 *   <li>MD5 → md5</li>
 *   <li>SHA256 → sha256</li>
 *   <li>EMAIL → email-src</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MispSyncService {

    /**
     * 日期格式（MISP date 字段：YYYY-MM-DD）
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 默认威胁等级（MISP: 1=高/2=中/3=低/4=未定义）
     */
    private static final String DEFAULT_THREAT_LEVEL = "3";

    /**
     * 默认分析状态（MISP: 0=初始/1=已分析/2=最终）
     */
    private static final String DEFAULT_ANALYSIS = "0";

    /**
     * 数据来源标记
     */
    private static final String SOURCE_MISP = "MISP";

    /**
     * MISP 客户端
     */
    private final MispClient mispClient;

    /**
     * MISP 配置属性
     */
    private final MispProperties mispProperties;

    /**
     * IOC 服务
     */
    private final IoCService ioCService;

    /**
     * 最近一次推送同步时间
     */
    private volatile LocalDateTime lastSyncTime;

    /**
     * 最近一次拉取同步时间
     */
    private volatile LocalDateTime lastPullTime;

    /**
     * 最近一次同步错误信息（用于 status 端点展示）
     */
    private volatile String lastError;

    /**
     * 单个 IOC 推送至 MISP
     *
     * <p>流程：根据 IOC 类型构建 MISP Event + Attribute，调用 {@link MispClient#addEvent}。
     * 同步成功后回填 {@code IoCEntity.mispEventId}。</p>
     *
     * @param iocId 平台 IOC ID
     * @return 创建的 MISP 事件，MISP 未启用或 IOC 不存在时返回 null
     */
    public MispEvent syncIocToMisp(Long iocId) {
        if (!mispProperties.isEnabled()) {
            log.debug("MISP 未启用，syncIocToMisp 静默返回: iocId={}", iocId);
            return null;
        }
        if (iocId == null) {
            log.warn("syncIocToMisp 失败：iocId 为空");
            return null;
        }
        IoCEntity ioc = ioCService.getById(iocId);
        if (ioc == null) {
            log.warn("syncIocToMisp 失败：IOC 不存在: iocId={}", iocId);
            return null;
        }
        String attrType = mapIocTypeToMispAttribute(ioc.getIocType());
        if (attrType == null) {
            log.warn("syncIocToMisp 失败：不支持的 IOC 类型: iocId={}, type={}", iocId, ioc.getIocType());
            return null;
        }
        try {
            MispEvent event = buildEvent(ioc, attrType);
            MispEvent created = mispClient.addEvent(event);
            // 回填 MISP 事件 ID
            if (created != null && created.getId() != null) {
                ioc.setMispEventId(created.getId());
                ioCService.saveOrUpdateIoc(ioc);
            }
            lastSyncTime = LocalDateTime.now();
            lastError = null;
            log.info("syncIocToMisp 成功: iocId={}, mispEventId={}", iocId,
                    created == null ? null : created.getId());
            return created;
        } catch (Exception e) {
            lastError = e.getMessage();
            log.error("syncIocToMisp 失败: iocId={}", iocId, e);
            return null;
        }
    }

    /**
     * 批量推送全部 IOC 至 MISP（定时任务，默认每日 02:00）
     *
     * <p>单个 IOC 同步失败不影响其他 IOC，最终返回成功/失败计数。</p>
     *
     * @return 同步结果统计（total / success / fail）
     */
    @Scheduled(cron = "${misp.sync-cron:0 0 2 * * ?}")
    public Map<String, Object> syncAllIocsToMisp() {
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("total", 0);
        stat.put("success", 0);
        stat.put("fail", 0);
        if (!mispProperties.isEnabled()) {
            log.debug("MISP 未启用，syncAllIocsToMisp 静默返回");
            return stat;
        }
        List<IoCEntity> iocs = ioCService.listAll();
        if (iocs == null || iocs.isEmpty()) {
            log.info("syncAllIocsToMisp：无 IOC 需要同步");
            return stat;
        }
        int success = 0;
        int fail = 0;
        for (IoCEntity ioc : iocs) {
            try {
                MispEvent created = syncIocToMisp(ioc.getId());
                if (created != null && created.getId() != null) {
                    success++;
                } else {
                    fail++;
                }
            } catch (Exception e) {
                fail++;
                log.error("syncAllIocsToMisp 单条失败: iocId={}", ioc.getId(), e);
            }
        }
        stat.put("total", iocs.size());
        stat.put("success", success);
        stat.put("fail", fail);
        log.info("syncAllIocsToMisp 完成: {}", stat);
        return stat;
    }

    /**
     * 拉取 MISP 事件写入平台 IOC 库（定时任务，默认每小时）
     *
     * <p>流程：列出 MISP 全部事件 → 拉取每个事件的属性 → 反向映射为 IoCEntity →
     * 调用 {@link IoCService#saveOrUpdateIoc} 写入。同类型同值的 IOC 自动去重更新。</p>
     *
     * @return 拉取结果统计（events / attributes / saved）
     */
    @Scheduled(cron = "${misp.pull-cron:0 0 * * * ?}")
    public Map<String, Object> pullMispEvents() {
        Map<String, Object> stat = new LinkedHashMap<>();
        stat.put("events", 0);
        stat.put("attributes", 0);
        stat.put("saved", 0);
        if (!mispProperties.isEnabled()) {
            log.debug("MISP 未启用，pullMispEvents 静默返回");
            return stat;
        }
        try {
            List<MispEvent> events = mispClient.listEvents();
            if (events == null || events.isEmpty()) {
                log.info("pullMispEvents：MISP 无事件");
                lastPullTime = LocalDateTime.now();
                lastError = null;
                return stat;
            }
            int totalAttr = 0;
            int saved = 0;
            for (MispEvent event : events) {
                if (event.getId() == null) {
                    continue;
                }
                List<MispAttribute> attrs = mispClient.listAttributes(event.getId());
                if (attrs == null || attrs.isEmpty()) {
                    continue;
                }
                totalAttr += attrs.size();
                for (MispAttribute attr : attrs) {
                    IoCEntity ioc = mapMispAttributeToIoc(attr, event);
                    if (ioc == null) {
                        continue;
                    }
                    try {
                        ioCService.saveOrUpdateIoc(ioc);
                        saved++;
                    } catch (Exception e) {
                        log.warn("pullMispEvents 保存 IOC 失败: type={}, value={}",
                                ioc.getIocType(), ioc.getIocValue(), e);
                    }
                }
            }
            stat.put("events", events.size());
            stat.put("attributes", totalAttr);
            stat.put("saved", saved);
            lastPullTime = LocalDateTime.now();
            lastError = null;
            log.info("pullMispEvents 完成: {}", stat);
            return stat;
        } catch (Exception e) {
            lastError = e.getMessage();
            log.error("pullMispEvents 失败", e);
            return stat;
        }
    }

    // ==================== 内部映射方法 ====================

    /**
     * 构建 MISP Event（含单个 Attribute）
     *
     * @param ioc      IOC 实体
     * @param attrType MISP attribute type
     * @return MISP Event
     */
    private MispEvent buildEvent(IoCEntity ioc, String attrType) {
        MispEvent event = new MispEvent();
        event.setInfo("红方平台 IOC: " + ioc.getIocType() + " " + ioc.getIocValue());
        event.setThreatLevelId(ioc.getThreatLevel() != null ? ioc.getThreatLevel() : DEFAULT_THREAT_LEVEL);
        event.setAnalysis(DEFAULT_ANALYSIS);
        event.setDate(LocalDate.now().format(DATE_FORMATTER));

        MispAttribute attr = new MispAttribute();
        attr.setType(attrType);
        attr.setValue(ioc.getIocValue());
        attr.setCategory(mapIocTypeToMispCategory(ioc.getIocType()));
        attr.setToIds("1");
        attr.setComment(ioc.getDescription() != null ? ioc.getDescription() : "from redteam platform");

        List<MispAttribute> attrs = new ArrayList<>();
        attrs.add(attr);
        event.setAttributes(attrs);
        return event;
    }

    /**
     * 平台 IOC 类型 → MISP attribute type 映射
     *
     * @param iocType IOC 类型
     * @return MISP attribute type，不支持的类型返回 null
     */
    private String mapIocTypeToMispAttribute(String iocType) {
        if (iocType == null) {
            return null;
        }
        switch (iocType.trim().toUpperCase()) {
            case IocType.IP:
            case "IPV4":
            case "IPV6":
                return "ip-src";
            case IocType.DOMAIN:
            case "DOMAIN-NAME":
                return "domain";
            case IocType.URL:
                return "url";
            case IocType.MD5:
                return "md5";
            case IocType.SHA256:
            case "SHA-256":
                return "sha256";
            case IocType.EMAIL:
            case "EMAIL-ADDR":
            case "EMAIL-ADDRESS":
                return "email-src";
            default:
                return null;
        }
    }

    /**
     * 平台 IOC 类型 → MISP attribute category 映射
     *
     * @param iocType IOC 类型
     * @return MISP category
     */
    private String mapIocTypeToMispCategory(String iocType) {
        if (iocType == null) {
            return "Other";
        }
        switch (iocType.trim().toUpperCase()) {
            case IocType.IP:
            case "IPV4":
            case "IPV6":
            case IocType.DOMAIN:
            case "DOMAIN-NAME":
            case IocType.URL:
                return "Network activity";
            case IocType.MD5:
            case IocType.SHA256:
            case "SHA-256":
                return "Artifacts dropped";
            case IocType.EMAIL:
            case "EMAIL-ADDR":
                return "Payload delivery";
            default:
                return "Other";
        }
    }

    /**
     * MISP attribute type → 平台 IOC 类型 反向映射
     *
     * @param attrType MISP attribute type
     * @return 平台 IOC 类型，不支持的类型返回 null
     */
    private String mapMispAttributeToIocType(String attrType) {
        if (attrType == null) {
            return null;
        }
        switch (attrType.trim().toLowerCase()) {
            case "ip-src":
            case "ip-dst":
            case "ip-src|port":
            case "ip-dst|port":
                return IocType.IP;
            case "domain":
            case "hostname":
                return IocType.DOMAIN;
            case "url":
                return IocType.URL;
            case "md5":
                return IocType.MD5;
            case "sha256":
                return IocType.SHA256;
            case "email-src":
            case "email-dst":
            case "email":
                return IocType.EMAIL;
            default:
                return null;
        }
    }

    /**
     * 将 MISP Attribute 反向映射为 IoCEntity
     *
     * @param attr  MISP 属性
     * @param event 所属 MISP 事件
     * @return IOC 实体，不支持的属性类型返回 null
     */
    private IoCEntity mapMispAttributeToIoc(MispAttribute attr, MispEvent event) {
        if (attr == null || attr.getValue() == null) {
            return null;
        }
        String iocType = mapMispAttributeToIocType(attr.getType());
        if (iocType == null) {
            return null;
        }
        IoCEntity ioc = new IoCEntity();
        ioc.setIocType(iocType);
        ioc.setIocValue(attr.getValue());
        ioc.setDescription(attr.getComment() != null ? attr.getComment() : event.getInfo());
        ioc.setSource(SOURCE_MISP);
        ioc.setThreatLevel(event.getThreatLevelId() != null ? event.getThreatLevelId() : DEFAULT_THREAT_LEVEL);
        ioc.setMispEventId(event.getId());
        return ioc;
    }

    /**
     * 获取最近一次推送同步时间
     *
     * @return 同步时间，未同步过返回 null
     */
    public LocalDateTime getLastSyncTime() {
        return lastSyncTime;
    }

    /**
     * 获取最近一次拉取同步时间
     *
     * @return 拉取时间，未拉取过返回 null
     */
    public LocalDateTime getLastPullTime() {
        return lastPullTime;
    }

    /**
     * 获取最近一次同步错误信息
     *
     * @return 错误信息，无错误返回 null
     */
    public String getLastError() {
        return lastError;
    }
}
