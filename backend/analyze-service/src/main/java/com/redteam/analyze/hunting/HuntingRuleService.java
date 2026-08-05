package com.redteam.analyze.hunting;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.redteam.analyze.hunting.entity.HuntingRuleEntity;
import com.redteam.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 狩猎规则服务
 *
 * <p>支持 Sigma 规则与 YARA 规则的导入、编辑、测试与统计：</p>
 * <ul>
 *   <li>{@link #importSigmaRule(String)}：解析 Sigma YAML 并入库</li>
 *   <li>{@link #importYaraRule(String)}：导入 YARA 规则（版本管理）</li>
 *   <li>{@link #testRule(String, String)}：对指定文件测试规则命中</li>
 *   <li>{@link #listRules()} / {@link #getRule(String)}：查询规则</li>
 *   <li>{@link #getRuleStats(String)}：规则命中统计</li>
 *   <li>{@link #updateRule(String, HuntingRuleEntity)}：编辑规则</li>
 * </ul>
 *
 * <p>规则与 ATT&CK 技术双向关联：规则可关联多个 techniqueId，
 * 反向可通过 {@link #findRulesByTechnique(String)} 查询。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class HuntingRuleService {

    /**
     * ISO 时间格式化器
     */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Sigma title 提取正则
     */
    private static final Pattern SIGMA_TITLE_PATTERN =
            Pattern.compile("(?m)^title:\\s*(.+?)\\s*$");

    /**
     * Sigma description 提取正则
     */
    private static final Pattern SIGMA_DESC_PATTERN =
            Pattern.compile("(?m)^description:\\s*(.+?)\\s*$");

    /**
     * Sigma author 提取正则
     */
    private static final Pattern SIGMA_AUTHOR_PATTERN =
            Pattern.compile("(?m)^author:\\s*(.+?)\\s*$");

    /**
     * Sigma level 提取正则
     */
    private static final Pattern SIGMA_LEVEL_PATTERN =
            Pattern.compile("(?m)^level:\\s*(.+?)\\s*$");

    /**
     * Sigma tags 提取正则（attack.tXXXX）
     */
    private static final Pattern SIGMA_ATTACK_TAG_PATTERN =
            Pattern.compile("attack\\.t(\\d{4}(?:\\.\\d{3})?)", Pattern.CASE_INSENSITIVE);

    /**
     * YARA rule 名称提取正则
     */
    private static final Pattern YARA_NAME_PATTERN =
            Pattern.compile("(?i)rule\\s+(\\w+)");

    /**
     * 规则存储（id -> entity）
     */
    private final Map<String, HuntingRuleEntity> ruleStore = new ConcurrentHashMap<>();

    /**
     * YARA 规则名称 -> 当前版本号（版本管理）
     */
    private final Map<String, AtomicInteger> yaraVersionCounter = new ConcurrentHashMap<>();

    /**
     * 导入 Sigma 规则
     *
     * @param content Sigma YAML 内容
     * @return 规则ID
     */
    public String importSigmaRule(String content) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException("Sigma 规则内容不能为空");
        }
        HuntingRuleEntity entity = new HuntingRuleEntity();
        entity.setId("rule-sigma-" + IdUtil.fastSimpleUUID().substring(0, 12));
        entity.setType(HuntingRuleEntity.TYPE_SIGMA);
        entity.setContent(content);
        entity.setEnabled(true);
        entity.setMatchCount(0);
        entity.setTestCount(0);

        // 解析 Sigma YAML（简化版正则提取关键字段）
        entity.setName(extractByPattern(content, SIGMA_TITLE_PATTERN, "Sigma-Untitled"));
        entity.setDescription(extractByPattern(content, SIGMA_DESC_PATTERN, ""));
        entity.setAuthor(extractByPattern(content, SIGMA_AUTHOR_PATTERN, "unknown"));
        String level = extractByPattern(content, SIGMA_LEVEL_PATTERN, "medium");
        entity.setSeverity(normalizeSeverity(level));

        // 提取 tags + attack 技术 ID
        List<String> tags = new ArrayList<>();
        List<String> techniqueIds = new ArrayList<>();
        Matcher tagMatcher = SIGMA_ATTACK_TAG_PATTERN.matcher(content);
        while (tagMatcher.find()) {
            String techId = "T" + tagMatcher.group(1).toUpperCase();
            if (!techniqueIds.contains(techId)) {
                techniqueIds.add(techId);
            }
            String tag = tagMatcher.group(0).toLowerCase();
            if (!tags.contains(tag)) {
                tags.add(tag);
            }
        }
        entity.setTags(tags);
        entity.setAttackTechniqueIds(techniqueIds);
        entity.setVersion(1);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        ruleStore.put(entity.getId(), entity);
        log.info("Sigma 规则导入: id={}, name={}, techniques={}", entity.getId(), entity.getName(), techniqueIds);
        return entity.getId();
    }

    /**
     * 导入 YARA 规则（版本管理）
     *
     * @param content YARA 规则源码
     * @return 规则ID
     */
    public String importYaraRule(String content) {
        if (StrUtil.isBlank(content)) {
            throw new BusinessException("YARA 规则内容不能为空");
        }
        Matcher nameMatcher = YARA_NAME_PATTERN.matcher(content);
        String ruleName = nameMatcher.find() ? nameMatcher.group(1) : "yara-untitled";

        HuntingRuleEntity entity = new HuntingRuleEntity();
        entity.setId("rule-yara-" + IdUtil.fastSimpleUUID().substring(0, 12));
        entity.setType(HuntingRuleEntity.TYPE_YARA);
        entity.setName(ruleName);
        entity.setContent(content);
        entity.setDescription("YARA rule: " + ruleName);
        entity.setAuthor("unknown");
        entity.setSeverity("medium");
        entity.setEnabled(true);
        entity.setMatchCount(0);
        entity.setTestCount(0);
        entity.setTags(new ArrayList<>());
        entity.setAttackTechniqueIds(new ArrayList<>());

        // 版本管理：同名规则版本递增
        int version = yaraVersionCounter.computeIfAbsent(ruleName, k -> new AtomicInteger(0))
                .incrementAndGet();
        entity.setVersion(version);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        ruleStore.put(entity.getId(), entity);
        log.info("YARA 规则导入: id={}, name={}, version={}", entity.getId(), ruleName, version);
        return entity.getId();
    }

    /**
     * 测试规则对指定文件的命中
     *
     * <p>简化版测试逻辑：检查规则内容中的关键字符串是否出现在文件内容中。
     * 生产环境应调用真实 Sigma/YARA 引擎。</p>
     *
     * @param ruleId 规则ID
     * @param fileId 文件ID
     * @return 测试结果（含 matched / matchedStrings / costMs）
     */
    public Map<String, Object> testRule(String ruleId, String fileId) {
        HuntingRuleEntity rule = getRuleInternal(ruleId);
        rule.setTestCount(rule.getTestCount() + 1);
        rule.setUpdateTime(LocalDateTime.now());

        long start = System.currentTimeMillis();
        // 模拟文件内容（生产环境应读取真实文件）
        String mockFileContent = "powershell -enc SGVsbG8= rundll32 malicious.dll,Entry malicious-update.example-evil.com 45.155.205.233";

        boolean matched = false;
        List<String> matchedStrings = new ArrayList<>();
        if (rule.getContent() != null) {
            // 提取规则中的字符串字面量（$a = "xxx"）
            java.util.regex.Pattern strPattern = java.util.regex.Pattern.compile("\"([^\"]+)\"");
            Matcher m = strPattern.matcher(rule.getContent());
            while (m.find()) {
                String s = m.group(1);
                if (s.length() >= 4 && mockFileContent.toLowerCase().contains(s.toLowerCase())) {
                    matched = true;
                    matchedStrings.add(s);
                }
            }
            // 规则名出现在文件中也算命中（YARA 规则名匹配）
            if (!matched && mockFileContent.toLowerCase().contains(rule.getName().toLowerCase())) {
                matched = true;
                matchedStrings.add(rule.getName());
            }
        }
        long costMs = System.currentTimeMillis() - start;

        if (matched) {
            rule.setMatchCount(rule.getMatchCount() + 1);
            rule.setLastMatchTime(LocalDateTime.now());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ruleId", ruleId);
        result.put("fileId", fileId);
        result.put("matched", matched);
        result.put("matchedStrings", matchedStrings);
        result.put("costMs", costMs);
        result.put("testedAt", LocalDateTime.now().format(ISO_FORMATTER));
        log.info("规则测试: ruleId={}, fileId={}, matched={}", ruleId, fileId, matched);
        return result;
    }

    /**
     * 列出全部规则
     *
     * @return 规则列表
     */
    public List<HuntingRuleEntity> listRules() {
        return new ArrayList<>(ruleStore.values());
    }

    /**
     * 获取规则
     *
     * @param ruleId 规则ID
     * @return 规则实体
     */
    public HuntingRuleEntity getRule(String ruleId) {
        return getRuleInternal(ruleId);
    }

    /**
     * 编辑规则
     *
     * @param ruleId   规则ID
     * @param updating 待更新字段（name/description/severity/enabled/tags/attackTechniqueIds/content）
     * @return 更新后的规则
     */
    public HuntingRuleEntity updateRule(String ruleId, HuntingRuleEntity updating) {
        HuntingRuleEntity rule = getRuleInternal(ruleId);
        if (updating == null) {
            return rule;
        }
        if (StrUtil.isNotBlank(updating.getName())) {
            rule.setName(updating.getName());
        }
        if (StrUtil.isNotBlank(updating.getDescription())) {
            rule.setDescription(updating.getDescription());
        }
        if (StrUtil.isNotBlank(updating.getSeverity())) {
            rule.setSeverity(updating.getSeverity());
        }
        if (updating.getTags() != null) {
            rule.setTags(updating.getTags());
        }
        if (updating.getAttackTechniqueIds() != null) {
            rule.setAttackTechniqueIds(updating.getAttackTechniqueIds());
        }
        if (StrUtil.isNotBlank(updating.getContent())) {
            rule.setContent(updating.getContent());
        }
        rule.setEnabled(updating.isEnabled());
        rule.setUpdateTime(LocalDateTime.now());
        return rule;
    }

    /**
     * 获取规则统计
     *
     * @param ruleId 规则ID
     * @return 统计信息（matchCount / testCount / version / lastMatchTime）
     */
    public Map<String, Object> getRuleStats(String ruleId) {
        HuntingRuleEntity rule = getRuleInternal(ruleId);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("ruleId", rule.getId());
        stats.put("name", rule.getName());
        stats.put("type", rule.getType());
        stats.put("version", rule.getVersion());
        stats.put("matchCount", rule.getMatchCount());
        stats.put("testCount", rule.getTestCount());
        stats.put("matchRate", rule.getTestCount() > 0
                ? Math.round(rule.getMatchCount() * 100.0 / rule.getTestCount()) / 100.0
                : 0.0);
        stats.put("lastMatchTime", rule.getLastMatchTime() != null
                ? rule.getLastMatchTime().format(ISO_FORMATTER) : null);
        stats.put("techniqueIds", rule.getAttackTechniqueIds());
        return stats;
    }

    /**
     * 根据 ATT&CK 技术反向查询关联规则
     *
     * @param techniqueId 技术 ID
     * @return 关联规则列表
     */
    public List<HuntingRuleEntity> findRulesByTechnique(String techniqueId) {
        if (StrUtil.isBlank(techniqueId)) {
            return Collections.emptyList();
        }
        return ruleStore.values().stream()
                .filter(r -> r.getAttackTechniqueIds() != null
                        && r.getAttackTechniqueIds().stream()
                        .anyMatch(t -> t.equalsIgnoreCase(techniqueId)
                                || (t.contains(".") && t.split("\\.")[0].equalsIgnoreCase(techniqueId))
                                || (techniqueId.contains(".") && techniqueId.split("\\.")[0].equalsIgnoreCase(t))))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 删除规则
     *
     * @param ruleId 规则ID
     */
    public void deleteRule(String ruleId) {
        if (StrUtil.isBlank(ruleId)) {
            return;
        }
        ruleStore.remove(ruleId);
        log.info("规则删除: ruleId={}", ruleId);
    }

    // ==================== 内部方法 ====================

    /**
     * 获取规则（内部，不存在抛异常）
     */
    private HuntingRuleEntity getRuleInternal(String ruleId) {
        if (StrUtil.isBlank(ruleId)) {
            throw new BusinessException("规则ID不能为空");
        }
        HuntingRuleEntity rule = ruleStore.get(ruleId);
        if (rule == null) {
            throw new BusinessException("狩猎规则不存在: " + ruleId);
        }
        return rule;
    }

    /**
     * 正则提取字段
     */
    private String extractByPattern(String content, Pattern pattern, String defaultValue) {
        Matcher m = pattern.matcher(content);
        if (m.find()) {
            return m.group(1).trim().replaceAll("^['\"]|['\"]$", "");
        }
        return defaultValue;
    }

    /**
     * 标准化严重等级
     */
    private String normalizeSeverity(String level) {
        if (level == null) {
            return "medium";
        }
        String l = level.toLowerCase().trim();
        switch (l) {
            case "informational":
            case "info":
                return "info";
            case "low":
                return "low";
            case "medium":
            case "moderate":
                return "medium";
            case "high":
                return "high";
            case "critical":
                return "critical";
            default:
                return "medium";
        }
    }
}
