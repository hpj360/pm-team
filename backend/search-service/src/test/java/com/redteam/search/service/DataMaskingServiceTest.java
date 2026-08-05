package com.redteam.search.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.entity.DataMaskingRuleEntity;
import com.redteam.common.mapper.DataMaskingRuleMapper;
import com.redteam.common.service.DataMaskingService;
import com.redteam.common.service.impl.DataMaskingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 数据脱敏服务单元测试
 *
 * <p>使用 Mockito mock {@link DataMaskingRuleMapper}，配合真实 {@link ObjectMapper}，
 * 验证 {@link DataMaskingServiceImpl} 的文本脱敏、JSON 脱敏、多规则组合及规则预览能力。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataMaskingServiceTest {

    @Mock
    private DataMaskingRuleMapper dataMaskingRuleMapper;

    private DataMaskingService dataMaskingService;

    @BeforeEach
    void setUp() {
        // 使用真实 ObjectMapper 以验证 JSON 遍历逻辑
        ObjectMapper objectMapper = new ObjectMapper();
        dataMaskingService = new DataMaskingServiceImpl(dataMaskingRuleMapper, objectMapper);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用脱敏规则
     */
    private DataMaskingRuleEntity buildRule(Long id, String name, String type,
                                            String pattern, String replacement, String level) {
        DataMaskingRuleEntity rule = new DataMaskingRuleEntity();
        rule.setId(id);
        rule.setRuleName(name);
        rule.setRuleType(type);
        rule.setPattern(pattern);
        rule.setReplacement(replacement);
        rule.setClassificationLevel(level);
        rule.setEnabled(1);
        return rule;
    }

    // ==================== maskText ====================

    /**
     * 用例1：手机号脱敏
     */
    @Test
    @DisplayName("手机号脱敏：中间4位替换为****")
    void testMaskText_Phone() {
        DataMaskingRuleEntity phoneRule = buildRule(1L, "手机号脱敏", "PHONE",
                "(\\d{3})\\d{4}(\\d{4})", "$1****$2", "CONFIDENTIAL");
        when(dataMaskingRuleMapper.selectEnabledByClassificationLevel(eq("CONFIDENTIAL")))
                .thenReturn(Collections.singletonList(phoneRule));

        String result = dataMaskingService.maskText("联系方式：13812345678", "CONFIDENTIAL");

        assertEquals("联系方式：138****5678", result, "手机号中间4位应被替换为****");
    }

    /**
     * 用例2：IP 地址脱敏
     */
    @Test
    @DisplayName("IP脱敏：第三段替换为x")
    void testMaskText_IP() {
        DataMaskingRuleEntity ipRule = buildRule(3L, "IP地址脱敏", "IP",
                "(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\.(\\d{1,3})", "$1.$2.x.$3", "SECRET");
        when(dataMaskingRuleMapper.selectEnabledByClassificationLevel(eq("SECRET")))
                .thenReturn(Collections.singletonList(ipRule));

        String result = dataMaskingService.maskText("目标IP：192.168.1.100", "SECRET");

        assertEquals("目标IP：192.168.x.100", result, "IP第三段应被替换为x");
    }

    /**
     * 用例3：无匹配规则不脱敏
     */
    @Test
    @DisplayName("无匹配规则：文本保持原样")
    void testMaskText_NoRules() {
        when(dataMaskingRuleMapper.selectEnabledByClassificationLevel(eq("PUBLIC")))
                .thenReturn(Collections.emptyList());

        String original = "敏感信息：13812345678，邮箱abc@test.com";
        String result = dataMaskingService.maskText(original, "PUBLIC");

        assertEquals(original, result, "无匹配规则时文本应保持原样");
    }

    /**
     * 用例4：多规则组合脱敏（手机号 + 邮箱）
     */
    @Test
    @DisplayName("多规则组合脱敏：手机号与邮箱同时脱敏")
    void testMaskText_MultipleRules() {
        DataMaskingRuleEntity phoneRule = buildRule(1L, "手机号脱敏", "PHONE",
                "(\\d{3})\\d{4}(\\d{4})", "$1****$2", "CONFIDENTIAL");
        DataMaskingRuleEntity emailRule = buildRule(4L, "邮箱脱敏", "EMAIL",
                "(\\w{1,2})\\w*@(\\w+)", "$1***@$2", "CONFIDENTIAL");
        List<DataMaskingRuleEntity> rules = Arrays.asList(phoneRule, emailRule);
        when(dataMaskingRuleMapper.selectEnabledByClassificationLevel(eq("CONFIDENTIAL")))
                .thenReturn(rules);

        String result = dataMaskingService.maskText("联系13812345678或abc@test.com", "CONFIDENTIAL");

        assertEquals("联系138****5678或ab***@test.com", result, "手机号与邮箱应同时被脱敏");
    }

    // ==================== maskJson ====================

    /**
     * 用例5：JSON 脱敏（仅字符串值脱敏，非字符串值保持原样）
     */
    @Test
    @DisplayName("JSON脱敏：字符串值脱敏，数字保持原样")
    void testMaskJson() throws Exception {
        DataMaskingRuleEntity phoneRule = buildRule(1L, "手机号脱敏", "PHONE",
                "(\\d{3})\\d{4}(\\d{4})", "$1****$2", "CONFIDENTIAL");
        when(dataMaskingRuleMapper.selectEnabledByClassificationLevel(eq("CONFIDENTIAL")))
                .thenReturn(Collections.singletonList(phoneRule));

        String json = "{\"phone\":\"13812345678\",\"name\":\"张三\",\"count\":5}";
        String result = dataMaskingService.maskJson(json, "CONFIDENTIAL");

        assertNotNull(result, "脱敏后的 JSON 不应为空");
        JsonNode resultNode = new ObjectMapper().readTree(result);

        // 字符串值 phone 应被脱敏
        assertTrue(resultNode.get("phone").isTextual(), "phone 字段应为字符串");
        assertEquals("138****5678", resultNode.get("phone").asText(), "phone 字段应被脱敏");

        // 不匹配规则的字符串值 name 保持原样
        assertEquals("张三", resultNode.get("name").asText(), "name 字段应保持原样");

        // 数字值 count 保持原样
        assertTrue(resultNode.get("count").isNumber(), "count 字段应仍为数字");
        assertEquals(5, resultNode.get("count").asInt(), "count 字段值应保持不变");

        // 不应包含原始手机号
        assertFalse(result.contains("13812345678"), "脱敏后的 JSON 不应包含原始手机号");
    }

    // ==================== testRule ====================

    /**
     * 用例6：规则测试预览
     */
    @Test
    @DisplayName("规则测试预览：用给定pattern和replacement执行替换")
    void testTestRule() {
        String pattern = "(\\d{3})\\d{4}(\\d{4})";
        String replacement = "$1****$2";
        String testText = "13812345678";

        String result = dataMaskingService.testRule(pattern, replacement, testText);

        assertEquals("138****5678", result, "规则预览应返回脱敏后的文本");
    }
}
