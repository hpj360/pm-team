package com.redteam.analyze.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.common.stix.StixAttackPattern;
import com.redteam.common.stix.StixBundle;
import com.redteam.common.stix.StixIndicator;
import com.redteam.common.stix.StixThreatActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * STIX 2.1 导出服务
 *
 * <p>负责将平台中的 IOC / APT / TTP 数据转换为符合 STIX 2.1 规范的 Bundle JSON。</p>
 *
 * <p>支持以下转换：</p>
 * <ul>
 *   <li>IOC → indicator（含 STIX pattern 表达式）</li>
 *   <li>APT 组织 → threat-actor</li>
 *   <li>TTP → attack-pattern</li>
 *   <li>混合导出时构建 relationship：APT uses TTP、APT targets IOC</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StixExportService {

    /**
     * STIX 2.1 规范的 indicator 默认标签
     */
    private static final List<String> DEFAULT_INDICATOR_LABELS =
            Collections.singletonList("malicious-activity");

    /**
     * STIX 2.1 规范的 threat-actor 默认类型
     */
    private static final List<String> DEFAULT_THREAT_ACTOR_TYPES =
            Collections.singletonList("nation-state");

    /**
     * ISO 8601 日期时间格式化器（含时区偏移）
     */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 用于序列化 STIX 对象为 Map 的 ObjectMapper
     */
    private final ObjectMapper objectMapper;

    // ==================== 对外导出方法 ====================

    /**
     * 导出 IOC 为 STIX Bundle
     *
     * @param iocList IOC 列表（包含 type/value/description 等字段）
     * @return STIX Bundle JSON 字符串
     */
    public String exportIocsToStix(List<Map<String, Object>> iocList) {
        log.info("导出 IOC 为 STIX Bundle: count={}", iocList == null ? 0 : iocList.size());
        List<Map<String, Object>> objects = new ArrayList<>();
        if (iocList != null) {
            for (Map<String, Object> ioc : iocList) {
                Map<String, Object> indicatorMap = buildIndicator(ioc);
                if (indicatorMap != null) {
                    objects.add(indicatorMap);
                }
            }
        }
        return serializeBundle(objects);
    }

    /**
     * 导出 APT 组织为 STIX Bundle
     *
     * @param aptList APT 组织列表（包含 name/description 等字段）
     * @return STIX Bundle JSON 字符串
     */
    public String exportAptsToStix(List<Map<String, Object>> aptList) {
        log.info("导出 APT 组织为 STIX Bundle: count={}", aptList == null ? 0 : aptList.size());
        List<Map<String, Object>> objects = new ArrayList<>();
        if (aptList != null) {
            for (Map<String, Object> apt : aptList) {
                Map<String, Object> threatActorMap = buildThreatActor(apt);
                if (threatActorMap != null) {
                    objects.add(threatActorMap);
                }
            }
        }
        return serializeBundle(objects);
    }

    /**
     * 导出 TTP 为 STIX Bundle
     *
     * @param ttpList TTP 列表（包含 name/description/externalReferences 等字段）
     * @return STIX Bundle JSON 字符串
     */
    public String exportTtpsToStix(List<Map<String, Object>> ttpList) {
        log.info("导出 TTP 为 STIX Bundle: count={}", ttpList == null ? 0 : ttpList.size());
        List<Map<String, Object>> objects = new ArrayList<>();
        if (ttpList != null) {
            for (Map<String, Object> ttp : ttpList) {
                Map<String, Object> attackPatternMap = buildAttackPattern(ttp);
                if (attackPatternMap != null) {
                    objects.add(attackPatternMap);
                }
            }
        }
        return serializeBundle(objects);
    }

    /**
     * 混合导出（IOC + APT + TTP + 关系）
     *
     * <p>构建关系：</p>
     * <ul>
     *   <li>APT → uses → TTP</li>
     *   <li>APT → targets → IOC</li>
     * </ul>
     *
     * @param iocList IOC 列表
     * @param aptList APT 组织列表
     * @param ttpList TTP 列表
     * @return STIX Bundle JSON 字符串
     */
    public String exportAllToStix(List<Map<String, Object>> iocList,
                                  List<Map<String, Object>> aptList,
                                  List<Map<String, Object>> ttpList) {
        log.info("混合导出 STIX Bundle: ioc={}, apt={}, ttp={}",
                iocList == null ? 0 : iocList.size(),
                aptList == null ? 0 : aptList.size(),
                ttpList == null ? 0 : ttpList.size());

        List<Map<String, Object>> objects = new ArrayList<>();

        // 1. 构建 Indicator 对象并记录 ID
        List<String> indicatorIds = new ArrayList<>();
        if (iocList != null) {
            for (Map<String, Object> ioc : iocList) {
                Map<String, Object> indicatorMap = buildIndicator(ioc);
                if (indicatorMap != null) {
                    objects.add(indicatorMap);
                    indicatorIds.add((String) indicatorMap.get("id"));
                }
            }
        }

        // 2. 构建 ThreatActor 对象并记录 ID
        List<String> threatActorIds = new ArrayList<>();
        if (aptList != null) {
            for (Map<String, Object> apt : aptList) {
                Map<String, Object> threatActorMap = buildThreatActor(apt);
                if (threatActorMap != null) {
                    objects.add(threatActorMap);
                    threatActorIds.add((String) threatActorMap.get("id"));
                }
            }
        }

        // 3. 构建 AttackPattern 对象并记录 ID
        List<String> attackPatternIds = new ArrayList<>();
        if (ttpList != null) {
            for (Map<String, Object> ttp : ttpList) {
                Map<String, Object> attackPatternMap = buildAttackPattern(ttp);
                if (attackPatternMap != null) {
                    objects.add(attackPatternMap);
                    attackPatternIds.add((String) attackPatternMap.get("id"));
                }
            }
        }

        // 4. 构建关系：APT → uses → TTP
        for (String aptId : threatActorIds) {
            for (String ttpId : attackPatternIds) {
                objects.add(buildRelationship(aptId, ttpId, "uses"));
            }
        }

        // 5. 构建关系：APT → targets → IOC
        for (String aptId : threatActorIds) {
            for (String iocId : indicatorIds) {
                objects.add(buildRelationship(aptId, iocId, "targets"));
            }
        }

        return serializeBundle(objects);
    }

    // ==================== 内部构建方法 ====================

    /**
     * 构建关系对象（Relationship）
     *
     * @param sourceId     源对象 ID
     * @param targetId     目标对象 ID
     * @param relationType 关系类型（如 uses / targets）
     * @return STIX relationship 对象 Map
     */
    private Map<String, Object> buildRelationship(String sourceId, String targetId, String relationType) {
        Map<String, Object> relationship = new LinkedHashMap<>();
        relationship.put("type", "relationship");
        relationship.put("id", "relationship--" + UUID.randomUUID());
        relationship.put("relationship_type", relationType);
        relationship.put("source_ref", sourceId);
        relationship.put("target_ref", targetId);
        relationship.put("created", nowIso());
        relationship.put("modified", nowIso());
        return relationship;
    }

    /**
     * 将 IOC Map 转换为 STIX Indicator Map
     *
     * @param ioc IOC 数据
     * @return Indicator Map，无法识别类型时返回 null
     */
    private Map<String, Object> buildIndicator(Map<String, Object> ioc) {
        if (ioc == null) {
            return null;
        }
        String type = asString(ioc.get("type"));
        String value = asString(ioc.get("value"));
        if (type == null || value == null) {
            log.warn("IOC 缺少 type 或 value 字段，跳过: {}", ioc);
            return null;
        }
        String pattern = buildPattern(type, value);
        if (pattern == null) {
            log.warn("不支持的 IOC 类型，跳过: type={}", type);
            return null;
        }

        StixIndicator indicator = new StixIndicator();
        indicator.setId("indicator--" + UUID.randomUUID());
        indicator.setName(asString(ioc.get("name")) != null
                ? asString(ioc.get("name"))
                : (type + ":" + value));
        indicator.setDescription(asString(ioc.get("description")));
        indicator.setPattern(pattern);
        indicator.setValidFrom(nowIso());
        indicator.setLabels(DEFAULT_INDICATOR_LABELS);
        return objectMapper.convertValue(indicator, Map.class);
    }

    /**
     * 将 APT Map 转换为 STIX ThreatActor Map
     *
     * @param apt APT 数据
     * @return ThreatActor Map
     */
    private Map<String, Object> buildThreatActor(Map<String, Object> apt) {
        if (apt == null) {
            return null;
        }
        String name = asString(apt.get("name"));
        if (name == null) {
            log.warn("APT 缺少 name 字段，跳过: {}", apt);
            return null;
        }

        StixThreatActor threatActor = new StixThreatActor();
        threatActor.setId("threat-actor--" + UUID.randomUUID());
        threatActor.setName(name);
        threatActor.setDescription(asString(apt.get("description")));
        threatActor.setThreatActorTypes(asStringList(apt.get("threatActorTypes"), DEFAULT_THREAT_ACTOR_TYPES));
        threatActor.setAliases(asStringList(apt.get("aliases"), null));
        return objectMapper.convertValue(threatActor, Map.class);
    }

    /**
     * 将 TTP Map 转换为 STIX AttackPattern Map
     *
     * @param ttp TTP 数据
     * @return AttackPattern Map
     */
    private Map<String, Object> buildAttackPattern(Map<String, Object> ttp) {
        if (ttp == null) {
            return null;
        }
        String name = asString(ttp.get("name"));
        if (name == null) {
            log.warn("TTP 缺少 name 字段，跳过: {}", ttp);
            return null;
        }

        StixAttackPattern attackPattern = new StixAttackPattern();
        attackPattern.setId("attack-pattern--" + UUID.randomUUID());
        attackPattern.setName(name);
        attackPattern.setDescription(asString(ttp.get("description")));
        attackPattern.setExternalReferences(asStringList(ttp.get("externalReferences"), null));
        return objectMapper.convertValue(attackPattern, Map.class);
    }

    /**
     * 根据 IOC 类型与值构建 STIX pattern 表达式
     *
     * @param type  IOC 类型（IP/Domain/URL/MD5/SHA256/Email）
     * @param value IOC 值
     * @return STIX pattern，不支持类型返回 null
     */
    private String buildPattern(String type, String value) {
        if (type == null || value == null) {
            return null;
        }
        // 兼容大小写与"FileHash"前缀
        String normalized = type.trim().toUpperCase();
        switch (normalized) {
            case "IP":
            case "IPV4":
                return "[ipv4-addr:value = '" + value + "']";
            case "DOMAIN":
            case "DOMAIN-NAME":
                return "[domain-name:value = '" + value + "']";
            case "URL":
                return "[url:value = '" + value + "']";
            case "MD5":
            case "FILEHASH(MD5)":
            case "FILEHASH-MD5":
                return "[file:hashes.'MD5' = '" + value + "']";
            case "SHA256":
            case "SHA-256":
            case "FILEHASH(SHA256)":
            case "FILEHASH-SHA256":
                return "[file:hashes.'SHA-256' = '" + value + "']";
            case "EMAIL":
            case "EMAIL-ADDR":
                return "[email-addr:value = '" + value + "']";
            default:
                return null;
        }
    }

    /**
     * 序列化 Bundle 为 JSON 字符串
     *
     * @param objects STIX 对象列表
     * @return JSON 字符串
     */
    private String serializeBundle(List<Map<String, Object>> objects) {
        StixBundle bundle = new StixBundle();
        bundle.setId("bundle--" + UUID.randomUUID());
        bundle.setObjects(objects);
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(bundle);
        } catch (JsonProcessingException e) {
            log.error("序列化 STIX Bundle 失败", e);
            throw new BusinessException(ResultCode.FAIL, "STIX Bundle 序列化失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前时间的 ISO 8601 字符串
     *
     * @return ISO 8601 时间字符串
     */
    private String nowIso() {
        return OffsetDateTime.now(ZoneOffset.UTC).format(ISO_FORMATTER);
    }

    /**
     * 安全转换为字符串
     *
     * @param obj 原始对象
     * @return 字符串，null 时返回 null
     */
    private String asString(Object obj) {
        if (obj == null) {
            return null;
        }
        String s = obj.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 安全转换为字符串列表
     *
     * @param obj          原始对象
     * @param defaultValue 默认值（原值为空时使用）
     * @return 字符串列表
     */
    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object obj, List<String> defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<Object>) obj) {
                if (item != null) {
                    String s = item.toString().trim();
                    if (!s.isEmpty()) {
                        result.add(s);
                    }
                }
            }
            return result.isEmpty() ? defaultValue : result;
        }
        // 单值转单元素列表
        String s = obj.toString().trim();
        return s.isEmpty() ? defaultValue : Collections.singletonList(s);
    }
}
