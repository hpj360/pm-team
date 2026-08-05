package com.redteam.analyze.hunting;

import com.redteam.analyze.hunting.entity.HuntingRuleEntity;
import com.redteam.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HuntingRuleService 单元测试
 *
 * @author 红方团队
 */
class HuntingRuleServiceTest {

    private HuntingRuleService service;

    /**
     * 示例 Sigma 规则（含 attack.t1059.001 tag）
     */
    private static final String SIGMA_RULE = "title: Detect PowerShell Encoded Command\n" +
            "description: Detects encoded PowerShell command execution\n" +
            "author: redteam-analyst\n" +
            "level: high\n" +
            "tags:\n" +
            "  - attack.execution\n" +
            "  - attack.t1059.001\n" +
            "logsource:\n" +
            "  product: windows\n" +
            "  service: powershell\n" +
            "detection:\n" +
            "  selection:\n" +
            "    CommandLine|contains: 'powershell -enc'\n" +
            "  condition: selection";

    /**
     * 示例 YARA 规则
     */
    private static final String YARA_RULE = "rule CobaltStrike_Beacon {\n" +
            "  strings:\n" +
            "    $a = \"powershell -enc\" ascii\n" +
            "    $b = \"malicious-update.example-evil.com\" ascii\n" +
            "  condition:\n" +
            "    any of them\n" +
            "}";

    @BeforeEach
    void setUp() {
        service = new HuntingRuleService();
    }

    // ==================== importSigmaRule ====================

    @Test
    @DisplayName("importSigmaRule: 正常导入解析字段")
    void importSigma_normal_parsesFields() {
        String id = service.importSigmaRule(SIGMA_RULE);
        assertNotNull(id);
        assertTrue(id.startsWith("rule-sigma-"));
        HuntingRuleEntity rule = service.getRule(id);
        assertEquals("Detect PowerShell Encoded Command", rule.getName());
        assertEquals("redteam-analyst", rule.getAuthor());
        assertEquals("high", rule.getSeverity());
        assertEquals(HuntingRuleEntity.TYPE_SIGMA, rule.getType());
        assertTrue(rule.isEnabled());
    }

    @Test
    @DisplayName("importSigmaRule: 自动提取 attack.t1059.001 技术")
    void importSigma_extractsAttackTechnique() {
        String id = service.importSigmaRule(SIGMA_RULE);
        HuntingRuleEntity rule = service.getRule(id);
        assertTrue(rule.getAttackTechniqueIds().contains("T1059.001"));
        assertTrue(rule.getTags().stream().anyMatch(t -> t.contains("attack.t1059")));
    }

    @Test
    @DisplayName("importSigmaRule: 空内容抛异常")
    void importSigma_blank_throws() {
        assertThrows(BusinessException.class, () -> service.importSigmaRule(null));
        assertThrows(BusinessException.class, () -> service.importSigmaRule(""));
        assertThrows(BusinessException.class, () -> service.importSigmaRule("   "));
    }

    @Test
    @DisplayName("importSigmaRule: 缺少 title 时使用默认值")
    void importSigma_missingTitle_usesDefault() {
        String content = "description: minimal rule\nlevel: low\ndetection:\n  condition: selection";
        String id = service.importSigmaRule(content);
        HuntingRuleEntity rule = service.getRule(id);
        assertEquals("Sigma-Untitled", rule.getName());
        assertEquals("low", rule.getSeverity());
    }

    // ==================== importYaraRule ====================

    @Test
    @DisplayName("importYaraRule: 正常导入提取规则名")
    void importYara_normal_parsesName() {
        String id = service.importYaraRule(YARA_RULE);
        assertNotNull(id);
        assertTrue(id.startsWith("rule-yara-"));
        HuntingRuleEntity rule = service.getRule(id);
        assertEquals("CobaltStrike_Beacon", rule.getName());
        assertEquals(HuntingRuleEntity.TYPE_YARA, rule.getType());
        assertEquals(1, rule.getVersion());
    }

    @Test
    @DisplayName("importYaraRule: 同名规则版本递增")
    void importYara_versionIncrement() {
        String id1 = service.importYaraRule(YARA_RULE);
        String id2 = service.importYaraRule(YARA_RULE);
        HuntingRuleEntity r1 = service.getRule(id1);
        HuntingRuleEntity r2 = service.getRule(id2);
        assertEquals(1, r1.getVersion());
        assertEquals(2, r2.getVersion());
    }

    @Test
    @DisplayName("importYaraRule: 空内容抛异常")
    void importYara_blank_throws() {
        assertThrows(BusinessException.class, () -> service.importYaraRule(null));
        assertThrows(BusinessException.class, () -> service.importYaraRule(""));
    }

    // ==================== testRule ====================

    @Test
    @DisplayName("testRule: 命中规则更新 matchCount")
    void testRule_matched_incrementsMatchCount() {
        // YARA 规则包含 "powershell -enc"，模拟文件内容也包含 → 命中
        String ruleId = service.importYaraRule(YARA_RULE);
        Map<String, Object> result = service.testRule(ruleId, "file-001");
        assertTrue((Boolean) result.get("matched"));
        assertFalse(((List<?>) result.get("matchedStrings")).isEmpty());
        HuntingRuleEntity rule = service.getRule(ruleId);
        assertEquals(1, rule.getMatchCount());
        assertEquals(1, rule.getTestCount());
        assertNotNull(rule.getLastMatchTime());
    }

    @Test
    @DisplayName("testRule: 未命中不更新 matchCount")
    void testRule_notMatched_keepMatchCount() {
        String content = "rule NoMatch_Rule {\n  strings:\n    $a = \"definitely_not_present_xyz\"\n  condition:\n    $a\n}";
        String ruleId = service.importYaraRule(content);
        Map<String, Object> result = service.testRule(ruleId, "file-001");
        assertFalse((Boolean) result.get("matched"));
        HuntingRuleEntity rule = service.getRule(ruleId);
        assertEquals(0, rule.getMatchCount());
        assertEquals(1, rule.getTestCount());
    }

    @Test
    @DisplayName("testRule: 不存在的规则抛异常")
    void testRule_notFound_throws() {
        assertThrows(BusinessException.class, () -> service.testRule("non-existent", "file-1"));
    }

    // ==================== listRules / getRule ====================

    @Test
    @DisplayName("listRules: 返回全部规则")
    void listRules_returnsAll() {
        service.importSigmaRule(SIGMA_RULE);
        service.importYaraRule(YARA_RULE);
        List<HuntingRuleEntity> rules = service.listRules();
        assertEquals(2, rules.size());
    }

    @Test
    @DisplayName("getRule: 不存在抛异常")
    void getRule_notFound_throws() {
        assertThrows(BusinessException.class, () -> service.getRule("non-existent"));
        assertThrows(BusinessException.class, () -> service.getRule(null));
    }

    // ==================== updateRule ====================

    @Test
    @DisplayName("updateRule: 更新字段生效")
    void updateRule_fieldsUpdated() {
        String id = service.importSigmaRule(SIGMA_RULE);
        HuntingRuleEntity updating = new HuntingRuleEntity();
        updating.setName("Updated Name");
        updating.setSeverity("critical");
        updating.setEnabled(false);
        HuntingRuleEntity updated = service.updateRule(id, updating);
        assertEquals("Updated Name", updated.getName());
        assertEquals("critical", updated.getSeverity());
        assertFalse(updated.isEnabled());
    }

    // ==================== getRuleStats ====================

    @Test
    @DisplayName("getRuleStats: 返回命中率与版本")
    void getRuleStats_returnsStats() {
        String id = service.importYaraRule(YARA_RULE);
        service.testRule(id, "f1"); // 命中
        service.testRule(id, "f2"); // 命中
        Map<String, Object> stats = service.getRuleStats(id);
        assertEquals(2, stats.get("testCount"));
        assertEquals(2, stats.get("matchCount"));
        assertEquals(1.0, stats.get("matchRate"));
        assertEquals(1, stats.get("version"));
    }

    @Test
    @DisplayName("getRuleStats: testCount=0 时 matchRate=0")
    void getRuleStats_zeroTest_matchRateZero() {
        String id = service.importYaraRule(YARA_RULE);
        Map<String, Object> stats = service.getRuleStats(id);
        assertEquals(0, stats.get("testCount"));
        assertEquals(0.0, stats.get("matchRate"));
    }

    // ==================== findRulesByTechnique ====================

    @Test
    @DisplayName("findRulesByTechnique: 按 attack.t1059.001 反查规则")
    void findRulesByTechnique_match() {
        service.importSigmaRule(SIGMA_RULE); // 含 T1059.001
        List<HuntingRuleEntity> rules = service.findRulesByTechnique("T1059.001");
        assertFalse(rules.isEmpty());
        assertTrue(rules.stream().anyMatch(r -> r.getAttackTechniqueIds().contains("T1059.001")));
    }

    @Test
    @DisplayName("findRulesByTechnique: 父技术 T1059 反查子技术规则")
    void findRulesByTechnique_parentMatch() {
        service.importSigmaRule(SIGMA_RULE); // 含 T1059.001
        List<HuntingRuleEntity> rules = service.findRulesByTechnique("T1059");
        assertFalse(rules.isEmpty());
    }

    @Test
    @DisplayName("findRulesByTechnique: 空参数返回空")
    void findRulesByTechnique_empty_returnsEmpty() {
        service.importSigmaRule(SIGMA_RULE);
        assertTrue(service.findRulesByTechnique(null).isEmpty());
        assertTrue(service.findRulesByTechnique("").isEmpty());
    }

    // ==================== deleteRule ====================

    @Test
    @DisplayName("deleteRule: 删除后不可查询")
    void deleteRule_removed() {
        String id = service.importSigmaRule(SIGMA_RULE);
        service.deleteRule(id);
        assertThrows(BusinessException.class, () -> service.getRule(id));
    }
}
