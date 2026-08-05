package com.redteam.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.client.LlmClient;
import com.redteam.ai.config.LlmConfig;
import com.redteam.common.entity.AttackChainEntity;
import com.redteam.common.mapper.AttackChainMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 攻击链自动推理服务
 *
 * <p>基于文件上下文、NER 实体、标签及关系图谱数据，调用 LLM 推理可能的攻击链路径。</p>
 *
 * <p>降级策略：</p>
 * <ul>
 *   <li>LLM 不可用 → status=2, errorMessage="LLM 服务不可用"</li>
 *   <li>profile-service 不可用 → 用空关系数据继续推理（不阻塞）</li>
 *   <li>JSON 解析失败 → 原文作为 reasoning, attackPaths 为空数组</li>
 * </ul>
 *
 * @author 红方团队
 */
@Service
@Slf4j
public class AttackChainInferenceService {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private AttackChainMapper attackChainMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LlmConfig llmConfig;

    /**
     * profile-service 服务地址，用于获取关系图谱数据
     */
    @Value("${profile.service.url:http://localhost:8085}")
    private String profileServiceUrl;

    /**
     * JSON 序列化/反序列化工具
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 系统 Prompt 模板
     */
    private static final String SYSTEM_PROMPT = """
            你是一名红方攻击路径分析专家。基于以下信息，推理可能的攻击链路径。

            ## 文件上下文
            %s

            ## 识别到的实体
            %s

            ## 标签
            %s

            ## 关系图谱数据
            %s

            ## 输出要求
            请以 JSON 格式输出：
            {
              "attackPaths": [
                {
                  "path": "入口点 → 横向移动 → 目标",
                  "steps": ["步骤1: ...", "步骤2: ..."],
                  "entities": ["涉及的实体1", "涉及的实体2"]
                }
              ],
              "confidence": "HIGH/MEDIUM/LOW",
              "reasoning": "推理过程说明"
            }

            注意：
            - 攻击链应基于实际识别到的实体和关系
            - 如果数据不足以推理，confidence 设为 LOW 并说明原因
            - 最多输出 3 条可能的攻击路径
            """;

    /**
     * 推理攻击链
     *
     * @param fileId      文件ID
     * @param nerEntities NER 实体列表
     * @param tags        标签列表
     * @param fileContext 文件上下文（摘要或关键段落）
     * @return 推理结果实体
     */
    public AttackChainEntity inferAttackChain(Long fileId,
                                              List<Map<String, Object>> nerEntities,
                                              List<String> tags,
                                              String fileContext) {
        log.info("开始推理攻击链, fileId={}", fileId);

        // 初始化推理记录，状态置为生成中
        AttackChainEntity entity = new AttackChainEntity();
        entity.setFileId(fileId);
        entity.setStatus(0);
        entity.setAttackPaths("[]");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        try {
            // 先插入数据库，记录生成中状态
            attackChainMapper.insert(entity);
        } catch (Exception e) {
            log.warn("插入攻击链记录失败，继续推理不阻塞, fileId={}", fileId, e);
        }

        try {
            // 1. 检查 LLM 是否可用
            if (!llmClient.isAvailable()) {
                log.warn("LLM 服务不可用, fileId={}", fileId);
                return markFailed(entity, "LLM 服务不可用");
            }

            // 2. 获取关系图谱数据（降级：失败时用空数据继续）
            String relationData = fetchRelationData(extractEntityValues(nerEntities));

            // 3. 构建 Prompt
            String systemPrompt = String.format(SYSTEM_PROMPT,
                    nullSafe(fileContext),
                    nullSafe(nerEntities),
                    nullSafe(tags),
                    relationData.isEmpty() ? "无可用关系数据" : relationData);

            String userPrompt = "请基于上述信息推理攻击链路径，严格按 JSON 格式输出。";

            // 4. 调用 LLM
            String llmResponse = llmClient.chat(systemPrompt, userPrompt);
            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("LLM 返回空响应, fileId={}", fileId);
                return markFailed(entity, "LLM 返回空响应");
            }

            // 5. 解析 LLM 返回的 JSON
            return parseAndPersist(entity, llmResponse);

        } catch (Exception e) {
            log.error("攻击链推理异常, fileId={}", fileId, e);
            return markFailed(entity, "推理异常: " + e.getMessage());
        }
    }

    /**
     * 获取关系数据（调用 profile-service）
     *
     * <p>降级策略：profile-service 不可用时返回空字符串，不阻塞推理流程。</p>
     *
     * @param entityValues 实体值列表
     * @return 关系数据 JSON 字符串，失败返回空字符串
     */
    private String fetchRelationData(List<String> entityValues) {
        if (entityValues == null || entityValues.isEmpty()) {
            log.debug("无实体值，跳过关系数据查询");
            return "";
        }
        try {
            String url = profileServiceUrl + "/api/profile/relations/query";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<List<String>> request = new HttpEntity<>(entityValues, headers);

            String response = restTemplate.postForObject(url, request, String.class);
            log.debug("关系数据查询成功, 实体数={}, 响应长度={}", entityValues.size(),
                    response == null ? 0 : response.length());
            return response == null ? "" : response;
        } catch (Exception e) {
            log.warn("调用 profile-service 获取关系数据失败，使用空关系数据继续推理: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 获取文件已有推理结果
     *
     * @param fileId 文件ID
     * @return 推理结果实体，无结果返回 null
     */
    public AttackChainEntity getByFileId(Long fileId) {
        try {
            return attackChainMapper.selectByFileId(fileId);
        } catch (Exception e) {
            log.error("查询攻击链推理结果异常, fileId={}", fileId, e);
            return null;
        }
    }

    /**
     * 解析 LLM 响应并持久化结果
     *
     * <p>降级策略：JSON 解析失败时，原文作为 reasoning，attackPaths 置为空数组。</p>
     *
     * @param entity      推理记录实体
     * @param llmResponse LLM 响应文本
     * @return 更新后的实体
     */
    private AttackChainEntity parseAndPersist(AttackChainEntity entity, String llmResponse) {
        String cleanJson = stripMarkdownFence(llmResponse);
        String attackPaths = "[]";
        String confidence = "LOW";
        String reasoning = llmResponse;

        try {
            JsonNode root = objectMapper.readTree(cleanJson);
            JsonNode pathsNode = root.path("attackPaths");
            if (pathsNode.isArray()) {
                attackPaths = objectMapper.writeValueAsString(pathsNode);
            }
            JsonNode confidenceNode = root.path("confidence");
            if (!confidenceNode.isMissingNode() && !confidenceNode.asText().isBlank()) {
                confidence = confidenceNode.asText().toUpperCase();
            }
            JsonNode reasoningNode = root.path("reasoning");
            if (!reasoningNode.isMissingNode() && !reasoningNode.asText().isBlank()) {
                reasoning = reasoningNode.asText();
            }
        } catch (Exception e) {
            log.warn("LLM 响应 JSON 解析失败，使用原文作为 reasoning, fileId={}", entity.getFileId(), e);
            // 降级：attackPaths 保持空数组，reasoning 使用原文
        }

        entity.setAttackPaths(attackPaths);
        entity.setConfidence(confidence);
        entity.setReasoning(reasoning);
        entity.setModel(llmConfig.getModel());
        entity.setTokensUsed(estimateTokens(llmResponse));
        entity.setStatus(1);
        entity.setErrorMessage(null);
        entity.setUpdatedAt(LocalDateTime.now());

        try {
            attackChainMapper.updateById(entity);
        } catch (Exception e) {
            log.warn("更新攻击链推理结果失败, fileId={}", entity.getFileId(), e);
        }
        return entity;
    }

    /**
     * 标记推理失败
     *
     * @param entity      推理记录实体
     * @param errorMessage 错误信息
     * @return 更新后的实体
     */
    private AttackChainEntity markFailed(AttackChainEntity entity, String errorMessage) {
        entity.setStatus(2);
        entity.setErrorMessage(errorMessage);
        entity.setUpdatedAt(LocalDateTime.now());
        try {
            attackChainMapper.updateById(entity);
        } catch (Exception e) {
            log.warn("更新攻击链失败状态失败, fileId={}", entity.getFileId(), e);
        }
        return entity;
    }

    /**
     * 从 NER 实体列表中提取实体值
     *
     * @param nerEntities NER 实体列表
     * @return 实体值列表
     */
    private List<String> extractEntityValues(List<Map<String, Object>> nerEntities) {
        if (nerEntities == null || nerEntities.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (Map<String, Object> entity : nerEntities) {
            Object value = entity.get("value");
            if (value == null) {
                value = entity.get("text");
            }
            if (value == null) {
                value = entity.get("name");
            }
            if (value != null && !value.toString().isBlank()) {
                values.add(value.toString());
            }
        }
        return values;
    }

    /**
     * 去除 LLM 输出中可能存在的 Markdown 代码块标记
     *
     * @param text LLM 响应文本
     * @return 去除标记后的文本
     */
    private String stripMarkdownFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            // 去掉首行 ```json 或 ```
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            // 去掉结尾 ```
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }

    /**
     * 粗略估算 token 用量（4 字符 ≈ 1 token）
     *
     * @param text 文本
     * @return 估算的 token 数
     */
    private Integer estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 4;
    }

    /**
     * 对象安全转字符串
     *
     * @param obj 对象
     * @return 字符串，null 返回 "无"
     */
    private String nullSafe(Object obj) {
        if (obj == null) {
            return "无";
        }
        try {
            if (obj instanceof String s) {
                return s.isBlank() ? "无" : s;
            }
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
