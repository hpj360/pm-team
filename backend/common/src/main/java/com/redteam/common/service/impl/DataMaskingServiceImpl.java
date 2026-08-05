package com.redteam.common.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.redteam.common.entity.DataMaskingRuleEntity;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.DataMaskingRuleMapper;
import com.redteam.common.result.ResultCode;
import com.redteam.common.service.DataMaskingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 数据脱敏服务实现
 *
 * <p>基于密级分级的正则替换实现文本/JSON 脱敏。脱敏规则按密级缓存于内存，
 * 避免每次脱敏都查询数据库；规则的增删改会自动失效缓存。</p>
 *
 * <p>JSON 脱敏策略：递归遍历 JSON 树，对所有字符串值执行 {@link #maskText}，
 * 非字符串值（数字、布尔、null）保持原样，序列化回 JSON 字符串返回。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataMaskingServiceImpl implements DataMaskingService {

    /**
     * 启用状态常量
     */
    private static final int ENABLED = 1;

    /**
     * 脱敏规则缓存：key=密级，value=该密级下启用的规则列表
     *
     * <p>使用 ConcurrentHashMap 保证多线程并发读取安全，{@code computeIfAbsent}
     * 保证同一密级只查询一次数据库。规则变更时调用 {@link #invalidateCache} 清空。</p>
     */
    private final Map<String, List<DataMaskingRuleEntity>> ruleCache = new ConcurrentHashMap<>();

    private final DataMaskingRuleMapper dataMaskingRuleMapper;
    private final ObjectMapper objectMapper;

    // ==================== 脱敏执行 ====================

    /**
     * 对文本执行脱敏
     */
    @Override
    public String maskText(String text, String classificationLevel) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        List<DataMaskingRuleEntity> rules = getEnabledRules(classificationLevel);
        if (rules == null || rules.isEmpty()) {
            return text;
        }
        String masked = text;
        for (DataMaskingRuleEntity rule : rules) {
            try {
                masked = Pattern.compile(rule.getPattern())
                        .matcher(masked)
                        .replaceAll(rule.getReplacement());
            } catch (Exception e) {
                log.warn("脱敏规则应用失败: ruleId={}, ruleName={}, pattern={}",
                        rule.getId(), rule.getRuleName(), rule.getPattern(), e);
            }
        }
        return masked;
    }

    /**
     * 对 JSON 字符串执行脱敏
     */
    @Override
    public String maskJson(String json, String classificationLevel) {
        if (StrUtil.isBlank(json)) {
            return json;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            maskJsonNode(root, classificationLevel);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("JSON 脱敏失败，返回原文", e);
            return json;
        }
    }

    /**
     * 测试规则效果
     */
    @Override
    public String testRule(String pattern, String replacement, String testText) {
        if (StrUtil.isBlank(pattern)) {
            return testText;
        }
        if (testText == null) {
            return null;
        }
        try {
            return Pattern.compile(pattern).matcher(testText).replaceAll(replacement);
        } catch (Exception e) {
            log.warn("规则测试失败: pattern={}, replacement={}", pattern, replacement, e);
            throw BusinessException.of(ResultCode.PARAM_ERROR, "正则表达式无效: " + e.getMessage());
        }
    }

    // ==================== 规则 CRUD ====================

    /**
     * 规则列表查询（参数为 null 时不筛选）
     */
    @Override
    public List<DataMaskingRuleEntity> listRules(String ruleType, String classificationLevel, Integer enabled) {
        LambdaQueryWrapper<DataMaskingRuleEntity> wrapper = new LambdaQueryWrapper<>();
        if (ruleType != null) {
            wrapper.eq(DataMaskingRuleEntity::getRuleType, ruleType);
        }
        if (classificationLevel != null) {
            wrapper.eq(DataMaskingRuleEntity::getClassificationLevel, classificationLevel);
        }
        if (enabled != null) {
            wrapper.eq(DataMaskingRuleEntity::getEnabled, enabled);
        }
        wrapper.orderByAsc(DataMaskingRuleEntity::getId);
        return dataMaskingRuleMapper.selectList(wrapper);
    }

    /**
     * 创建规则
     */
    @Override
    public DataMaskingRuleEntity createRule(DataMaskingRuleEntity rule) {
        if (rule == null || StrUtil.isBlank(rule.getRuleName())) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "规则名称不能为空");
        }
        if (StrUtil.isBlank(rule.getPattern())) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "正则表达式不能为空");
        }
        if (StrUtil.isBlank(rule.getReplacement())) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "替换模板不能为空");
        }
        // 设置默认值
        if (rule.getEnabled() == null) {
            rule.setEnabled(ENABLED);
        }
        if (StrUtil.isBlank(rule.getClassificationLevel())) {
            rule.setClassificationLevel("CONFIDENTIAL");
        }
        dataMaskingRuleMapper.insert(rule);
        invalidateCache();
        log.info("创建脱敏规则成功: id={}, ruleName={}", rule.getId(), rule.getRuleName());
        return rule;
    }

    /**
     * 更新规则
     */
    @Override
    public DataMaskingRuleEntity updateRule(Long id, DataMaskingRuleEntity rule) {
        if (rule == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "规则参数不能为空");
        }
        DataMaskingRuleEntity existing = dataMaskingRuleMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "脱敏规则不存在");
        }
        // 复制可修改字段，保留 id 与创建时间
        if (StrUtil.isNotBlank(rule.getRuleName())) {
            existing.setRuleName(rule.getRuleName());
        }
        if (StrUtil.isNotBlank(rule.getRuleType())) {
            existing.setRuleType(rule.getRuleType());
        }
        if (StrUtil.isNotBlank(rule.getPattern())) {
            existing.setPattern(rule.getPattern());
        }
        if (StrUtil.isNotBlank(rule.getReplacement())) {
            existing.setReplacement(rule.getReplacement());
        }
        if (StrUtil.isNotBlank(rule.getClassificationLevel())) {
            existing.setClassificationLevel(rule.getClassificationLevel());
        }
        if (rule.getEnabled() != null) {
            existing.setEnabled(rule.getEnabled());
        }
        if (rule.getDescription() != null) {
            existing.setDescription(rule.getDescription());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        dataMaskingRuleMapper.updateById(existing);
        invalidateCache();
        log.info("更新脱敏规则成功: id={}, ruleName={}", id, existing.getRuleName());
        return existing;
    }

    /**
     * 删除规则
     */
    @Override
    public void deleteRule(Long id) {
        DataMaskingRuleEntity existing = dataMaskingRuleMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "脱敏规则不存在");
        }
        dataMaskingRuleMapper.deleteById(id);
        invalidateCache();
        log.info("删除脱敏规则成功: id={}, ruleName={}", id, existing.getRuleName());
    }

    /**
     * 启用/禁用切换
     */
    @Override
    public void toggleRule(Long id) {
        DataMaskingRuleEntity existing = dataMaskingRuleMapper.selectById(id);
        if (existing == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "脱敏规则不存在");
        }
        Integer current = existing.getEnabled();
        existing.setEnabled(current != null && current == ENABLED ? 0 : ENABLED);
        existing.setUpdatedAt(LocalDateTime.now());
        dataMaskingRuleMapper.updateById(existing);
        invalidateCache();
        log.info("切换脱敏规则状态: id={}, enabled={} -> {}", id, current, existing.getEnabled());
    }

    // ==================== 私有方法 ====================

    /**
     * 获取指定密级的启用规则（带缓存）
     *
     * @param classificationLevel 密级
     * @return 启用规则列表
     */
    private List<DataMaskingRuleEntity> getEnabledRules(String classificationLevel) {
        if (StrUtil.isBlank(classificationLevel)) {
            return List.of();
        }
        return ruleCache.computeIfAbsent(classificationLevel,
                level -> dataMaskingRuleMapper.selectEnabledByClassificationLevel(level));
    }

    /**
     * 失效规则缓存（规则变更时调用）
     */
    private void invalidateCache() {
        ruleCache.clear();
    }

    /**
     * 递归遍历 JSON 树，对所有字符串值执行脱敏
     *
     * @param node                JSON 节点
     * @param classificationLevel 密级
     */
    private void maskJsonNode(JsonNode node, String classificationLevel) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            // 收集字段名后再修改，避免遍历过程中并发修改
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                JsonNode child = objectNode.get(fieldName);
                if (child.isTextual()) {
                    objectNode.set(fieldName, TextNode.valueOf(maskText(child.asText(), classificationLevel)));
                } else if (child.isContainerNode()) {
                    maskJsonNode(child, classificationLevel);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode child = arrayNode.get(i);
                if (child.isTextual()) {
                    arrayNode.set(i, TextNode.valueOf(maskText(child.asText(), classificationLevel)));
                } else if (child.isContainerNode()) {
                    maskJsonNode(child, classificationLevel);
                }
            }
        }
    }
}
