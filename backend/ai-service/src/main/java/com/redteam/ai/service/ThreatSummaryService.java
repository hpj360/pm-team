package com.redteam.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.client.LlmClient;
import com.redteam.ai.config.LlmConfig;
import com.redteam.common.api.dto.NerEntityVO;
import com.redteam.common.entity.ThreatSummaryEntity;
import com.redteam.common.mapper.ThreatSummaryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 威胁摘要服务
 *
 * <p>基于 LLM 大模型对文件内容、NER 实体、标签等信息进行综合分析，
 * 生成结构化的威胁摘要（摘要 / 关键发现 / 建议行动）。</p>
 *
 * <p>降级策略：
 * <ol>
 *   <li>先创建 status=0（生成中）的记录</li>
 *   <li>调用 LLM，若返回 null（不可用）则更新 status=2 并记录 errorMessage</li>
 *   <li>LLM 正常返回时解析 JSON，更新 status=1</li>
 *   <li>JSON 解析失败时，将原始文本作为 summary，status=1</li>
 *   <li>整个方法用 try-catch 包裹，异常时 status=2</li>
 * </ol>
 * </p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class ThreatSummaryService {

    /**
     * 文本输入最大长度（超过则截断）
     */
    private static final int MAX_TEXT_LENGTH = 4000;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ThreatSummaryMapper summaryMapper;

    @Autowired
    private LlmConfig llmConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 为文件生成威胁摘要
     *
     * @param fileId      文件ID
     * @param textContent 文件文本（截断到前 4000 字符）
     * @param fileName    文件名
     * @param fileType    文件类型
     * @param nerEntities NER 实体列表
     * @param tags        标签列表
     * @return 威胁摘要实体（含最终状态与内容）
     */
    public ThreatSummaryEntity generateSummary(Long fileId, String textContent,
                                                String fileName, String fileType,
                                                List<NerEntityVO> nerEntities, List<String> tags) {
        // 截断文本到前 4000 字符
        String truncatedText = truncateText(textContent);

        // 1. 先创建 status=0（生成中）的记录
        ThreatSummaryEntity entity = new ThreatSummaryEntity();
        entity.setFileId(fileId);
        entity.setStatus(0);
        entity.setModel(llmConfig.getModel());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        summaryMapper.insert(entity);
        log.info("威胁摘要记录已创建，fileId={}, recordId={}", fileId, entity.getId());

        try {
            // 2. 构建 Prompt 并调用 LLM
            String systemPrompt = buildSystemPrompt(fileName, fileType, tags, truncatedText, nerEntities);
            String userPrompt = buildUserPrompt(fileName, truncatedText);

            String llmResponse = llmClient.chat(systemPrompt, userPrompt);

            // 3. LLM 不可用降级
            if (llmResponse == null || llmResponse.trim().isEmpty()) {
                log.warn("LLM 不可用或返回空响应，fileId={}", fileId);
                entity.setStatus(2);
                entity.setErrorMessage("LLM 服务不可用或返回空响应");
                entity.setUpdatedAt(LocalDateTime.now());
                summaryMapper.updateById(entity);
                return entity;
            }

            // 4. 解析 JSON 响应
            parseLlmResponse(entity, llmResponse);

            // 5. 更新 status=1（成功）
            entity.setStatus(1);
            entity.setTokensUsed(estimateTokens(llmResponse));
            entity.setUpdatedAt(LocalDateTime.now());
            summaryMapper.updateById(entity);
            log.info("威胁摘要生成成功，fileId={}, recordId={}", fileId, entity.getId());
            return entity;

        } catch (Exception e) {
            // 6. 异常时 status=2
            log.error("威胁摘要生成异常，fileId={}", fileId, e);
            entity.setStatus(2);
            entity.setErrorMessage("生成异常: " + e.getMessage());
            entity.setUpdatedAt(LocalDateTime.now());
            summaryMapper.updateById(entity);
            return entity;
        }
    }

    /**
     * 获取文件的威胁摘要
     *
     * @param fileId 文件ID
     * @return 威胁摘要实体，无记录时返回 null
     */
    public ThreatSummaryEntity getByFileId(Long fileId) {
        return summaryMapper.selectByFileId(fileId);
    }

    /**
     * 截断文本到前 {@value #MAX_TEXT_LENGTH} 字符
     *
     * @param textContent 原始文本
     * @return 截断后的文本，null 返回空字符串
     */
    private String truncateText(String textContent) {
        if (textContent == null || textContent.isEmpty()) {
            return "";
        }
        if (textContent.length() <= MAX_TEXT_LENGTH) {
            return textContent;
        }
        log.info("文本过长（{} 字符），截断到 {} 字符", textContent.length(), MAX_TEXT_LENGTH);
        return textContent.substring(0, MAX_TEXT_LENGTH);
    }

    /**
     * 构建系统 Prompt
     *
     * @param fileName       文件名
     * @param fileType       文件类型
     * @param tags           标签列表
     * @param textContent    文本内容（已截断）
     * @param nerEntities    NER 实体列表
     * @return 系统 Prompt
     */
    private String buildSystemPrompt(String fileName, String fileType,
                                      List<String> tags, String textContent,
                                      List<NerEntityVO> nerEntities) {
        String tagsStr = (tags == null || tags.isEmpty()) ? "无" : String.join("、", tags);
        String nerStr = formatNerEntities(nerEntities);

        return "你是一名红方网络安全分析师。请基于以下文件信息生成威胁摘要。\n\n"
                + "## 文件信息\n"
                + "- 文件名: " + (fileName == null ? "未知" : fileName) + "\n"
                + "- 文件类型: " + (fileType == null ? "未知" : fileType) + "\n"
                + "- 标签: " + tagsStr + "\n\n"
                + "## 文件内容摘要（前4000字符）\n" + textContent + "\n\n"
                + "## NER 识别实体\n" + nerStr + "\n\n"
                + "## 输出要求\n"
                + "请以 JSON 格式输出，包含以下字段：\n"
                + "{\n"
                + "  \"summary\": \"3-5句话的威胁摘要，描述文件中的关键威胁信息\",\n"
                + "  \"keyFindings\": [\"关键发现1\", \"关键发现2\", \"关键发现3\"],\n"
                + "  \"suggestedActions\": [\"建议行动1\", \"建议行动2\"]\n"
                + "}\n\n"
                + "注意：\n"
                + "- 摘要应关注攻击线索、漏洞信息、敏感数据泄露、恶意代码等\n"
                + "- 关键发现列出 3-5 条具体的安全发现\n"
                + "- 建议行动给出 2-3 条可操作的建议\n"
                + "- 如果文件无安全威胁，明确说明\"未发现明显威胁\"";
    }

    /**
     * 构建用户 Prompt
     *
     * @param fileName    文件名
     * @param textContent 文本内容
     * @return 用户 Prompt
     */
    private String buildUserPrompt(String fileName, String textContent) {
        return "请对文件「" + (fileName == null ? "未知" : fileName) + "」进行威胁分析并生成结构化摘要。";
    }

    /**
     * 格式化 NER 实体列表为 Prompt 文本
     *
     * @param nerEntities NER 实体列表
     * @return 格式化字符串
     */
    private String formatNerEntities(List<NerEntityVO> nerEntities) {
        if (nerEntities == null || nerEntities.isEmpty()) {
            return "未识别到实体";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < nerEntities.size(); i++) {
            NerEntityVO e = nerEntities.get(i);
            String confidence = (e.getConfidence() != null) ? String.format("%.2f", e.getConfidence()) : "N/A";
            lines.add(String.format("%d. [%s] %s (置信度: %s)",
                    i + 1,
                    e.getEntityType() == null ? "UNKNOWN" : e.getEntityType(),
                    e.getEntityText() == null ? "" : e.getEntityText(),
                    confidence));
        }
        return String.join("\n", lines);
    }

    /**
     * 解析 LLM 响应为结构化字段。
     *
     * <p>JSON 解析失败时，将原始文本作为 summary，keyFindings / suggestedActions 置空数组。</p>
     *
     * @param entity      待填充的实体
     * @param llmResponse LLM 原始响应
     */
    private void parseLlmResponse(ThreatSummaryEntity entity, String llmResponse) {
        try {
            // 提取 JSON 内容（兼容 LLM 可能包裹的 ```json ... ``` 标记）
            String json = extractJson(llmResponse);
            JsonNode root = objectMapper.readTree(json);

            entity.setSummary(getTextOrDefault(root, "summary", llmResponse));
            entity.setKeyFindings(arrayToString(root, "keyFindings"));
            entity.setSuggestedActions(arrayToString(root, "suggestedActions"));
        } catch (Exception e) {
            log.warn("LLM 响应 JSON 解析失败，将原始文本作为 summary。原因: {}", e.getMessage());
            // JSON 解析失败时，将原始文本作为 summary
            entity.setSummary(llmResponse);
            entity.setKeyFindings("[]");
            entity.setSuggestedActions("[]");
        }
    }

    /**
     * 从可能包含 markdown 代码块标记的响应中提取 JSON 字符串
     *
     * @param response LLM 原始响应
     * @return 提取后的 JSON 文本
     */
    private String extractJson(String response) {
        if (response == null) {
            return "{}";
        }
        String trimmed = response.trim();
        // 去除 ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int lastFence = trimmed.lastIndexOf("```");
            if (lastFence >= 0) {
                trimmed = trimmed.substring(0, lastFence);
            }
            trimmed = trimmed.trim();
        }
        return trimmed;
    }

    /**
     * 安全读取 JSON 节点的文本字段，缺失时返回默认值
     *
     * @param root        JSON 根节点
     * @param field       字段名
     * @param defaultValue 默认值
     * @return 字段文本
     */
    private String getTextOrDefault(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        String text = node.asText();
        return (text == null || text.isEmpty()) ? defaultValue : text;
    }

    /**
     * 将 JSON 数组字段转为字符串形式存储
     *
     * @param root  JSON 根节点
     * @param field 字段名
     * @return JSON 数组字符串
     */
    private String arrayToString(JsonNode root, String field) {
        try {
            JsonNode node = root.get(field);
            if (node == null || node.isNull()) {
                return "[]";
            }
            if (node.isArray()) {
                List<String> items = new ArrayList<>();
                node.forEach(item -> items.add(item.asText()));
                return objectMapper.writeValueAsString(items);
            }
            // 非数组则原样返回
            return objectMapper.writeValueAsString(Collections.singletonList(node.asText()));
        } catch (Exception e) {
            log.warn("字段 {} 转 JSON 数组失败: {}", field, e.getMessage());
            return "[]";
        }
    }

    /**
     * 估算 LLM 响应消耗的 token 数（粗略估算：1 字符 ≈ 0.5 token 中文 / 0.25 token 英文）
     *
     * @param response LLM 响应
     * @return 估算的 token 数
     */
    private Integer estimateTokens(String response) {
        if (response == null) {
            return 0;
        }
        // 粗略估算：字符数 / 2
        return response.length() / 2;
    }

    /**
     * 用于测试注入 ObjectMapper 的可见性（可选）
     *
     * @return ObjectMapper 实例
     */
    ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
