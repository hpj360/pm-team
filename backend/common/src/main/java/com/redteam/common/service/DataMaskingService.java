package com.redteam.common.service;

import com.redteam.common.entity.DataMaskingRuleEntity;

import java.util.List;

/**
 * 数据脱敏服务接口
 *
 * <p>提供基于密级分级的文本/JSON 脱敏能力，以及脱敏规则的 CRUD 管理。
 * 脱敏规则按文件密级（PUBLIC/INTERNAL/CONFIDENTIAL/SECRET）匹配，使用正则替换执行脱敏。</p>
 *
 * @author 红方团队
 */
public interface DataMaskingService {

    /**
     * 对文本执行脱敏
     *
     * @param text               原始文本
     * @param classificationLevel 文件密级
     * @return 脱敏后的文本
     */
    String maskText(String text, String classificationLevel);

    /**
     * 对 JSON 字符串执行脱敏（遍历所有字符串值）
     *
     * @param json               原始 JSON 字符串
     * @param classificationLevel 文件密级
     * @return 脱敏后的 JSON 字符串
     */
    String maskJson(String json, String classificationLevel);

    /**
     * 获取规则列表（参数为 null 时不筛选）
     *
     * @param ruleType            规则类型：PHONE/IDCARD/IP/EMAIL/CUSTOM（可空）
     * @param classificationLevel 适用密级（可空）
     * @param enabled             启用状态：0禁用 1启用（可空）
     * @return 规则列表
     */
    List<DataMaskingRuleEntity> listRules(String ruleType, String classificationLevel, Integer enabled);

    /**
     * 创建规则
     *
     * @param rule 规则实体
     * @return 创建后的规则实体
     */
    DataMaskingRuleEntity createRule(DataMaskingRuleEntity rule);

    /**
     * 更新规则
     *
     * @param id   规则ID
     * @param rule 规则实体
     * @return 更新后的规则实体
     */
    DataMaskingRuleEntity updateRule(Long id, DataMaskingRuleEntity rule);

    /**
     * 删除规则
     *
     * @param id 规则ID
     */
    void deleteRule(Long id);

    /**
     * 启用/禁用规则切换
     *
     * @param id 规则ID
     */
    void toggleRule(Long id);

    /**
     * 测试规则效果（不落库，仅预览替换结果）
     *
     * @param pattern     正则表达式
     * @param replacement 替换模板
     * @param testText    测试文本
     * @return 脱敏后的文本
     */
    String testRule(String pattern, String replacement, String testText);
}
