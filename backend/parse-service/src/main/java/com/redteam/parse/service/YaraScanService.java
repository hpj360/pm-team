package com.redteam.parse.service;

import com.redteam.parse.dto.YaraMatchVO;
import com.redteam.parse.dto.YaraRuleDTO;
import com.redteam.parse.entity.YaraRuleEntity;

import java.util.List;

/**
 * YARA 规则扫描服务接口
 *
 * <p>提供 YARA 规则的 CRUD、启用/禁用，以及对文件/文本的扫描能力。
 * 扫描失败时返回空列表（降级），不影响主解析流程。</p>
 *
 * @author 红方团队
 */
public interface YaraScanService {

    /**
     * 获取所有启用的规则
     *
     * @return 启用规则列表
     */
    List<YaraRuleEntity> listEnabledRules();

    /**
     * 创建 YARA 规则
     *
     * @param dto 规则 DTO
     * @return 创建后的规则实体
     */
    YaraRuleEntity createRule(YaraRuleDTO dto);

    /**
     * 更新规则
     *
     * @param id  规则ID
     * @param dto 规则 DTO
     * @return 更新后的规则实体
     */
    YaraRuleEntity updateRule(Long id, YaraRuleDTO dto);

    /**
     * 删除规则（逻辑删除）
     *
     * @param id 规则ID
     */
    void deleteRule(Long id);

    /**
     * 启用规则
     *
     * @param id 规则ID
     */
    void enableRule(Long id);

    /**
     * 禁用规则
     *
     * @param id 规则ID
     */
    void disableRule(Long id);

    /**
     * 扫描文件
     *
     * @param fileId   文件ID
     * @param filePath 文件本地路径
     * @return YARA 匹配结果列表
     */
    List<YaraMatchVO> scanFile(Long fileId, String filePath);

    /**
     * 扫描文本内容
     *
     * @param fileId 文件ID
     * @param text   文本内容
     * @return YARA 匹配结果列表
     */
    List<YaraMatchVO> scanText(Long fileId, String text);
}
