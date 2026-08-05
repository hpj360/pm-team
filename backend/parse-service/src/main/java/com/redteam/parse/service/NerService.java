package com.redteam.parse.service;

import com.redteam.parse.dto.NerEntityVO;

import java.util.List;
import java.util.Map;

/**
 * NER 实体识别服务接口
 *
 * <p>基于 security-BERT 模型进行安全领域实体识别，模型加载失败时降级到正则兜底方案。
 * 红方重点关注的实体类型：IP/域名/URL/邮箱/哈希/CVE/工具/漏洞利用代码。</p>
 *
 * @author 红方团队
 */
public interface NerService {

    /**
     * 从文本中提取实体
     *
     * @param text 文本内容
     * @return 实体列表
     */
    List<NerEntityVO> extractEntities(String text);

    /**
     * 从文件中提取实体
     *
     * @param fileId   文件ID
     * @param filePath 文件路径
     * @return 实体列表
     */
    List<NerEntityVO> extractEntitiesFromFile(Long fileId, String filePath);

    /**
     * 预加载模型
     */
    void preloadModel();

    /**
     * 获取模型状态信息（用于健康检查端点）
     *
     * <p>返回字段：</p>
     * <ul>
     *   <li>{@code status}：READY（模型就绪）/ FALLBACK（正则兜底）/ FAILED（模型加载失败）</li>
     *   <li>{@code modelPath}：模型路径</li>
     *   <li>{@code lastError}：最近一次错误信息（可能为 null）</li>
     * </ul>
     *
     * @return 状态信息 Map
     */
    Map<String, Object> getModelStatus();
}
