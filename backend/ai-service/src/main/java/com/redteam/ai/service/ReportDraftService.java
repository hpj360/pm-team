package com.redteam.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.client.LlmClient;
import com.redteam.ai.config.LlmConfig;
import com.redteam.ai.vo.ReportDraft;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报告草稿生成服务
 *
 * <p>基于 LLM 大模型对报告统计数据、文件列表、标签分布进行综合分析，
 * 生成报告的结论段落与建议行动。</p>
 *
 * <p>降级策略：
 * <ol>
 *   <li>LLM 不可用（isAvailable=false 或返回 null/空）→ conclusion 返回模板文本，
 *       recommendations 为空，llmUsed=false</li>
 *   <li>LLM 正常返回但 JSON 解析失败 → 原文作为 conclusion，recommendations 为空，llmUsed=true</li>
 *   <li>整个方法用 try-catch 包裹，异常时降级为模板文本并记录 errorMessage</li>
 * </ol>
 * </p>
 *
 * @author 红方团队
 */
@Service
@Slf4j
public class ReportDraftService {

    /**
     * 系统 Prompt 模板
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一名红方安全报告撰写专家。请基于以下数据生成报告的结论段落和建议行动。

            ## 报告统计数据
            {statsJson}

            ## 文件列表（最多20条）
            {fileListJson}

            ## 标签分布
            {tagDistributionJson}

            ## 输出要求
            请以 JSON 格式输出：
            {
              "conclusion": "2-3段结论文字，总结本期报告的关键发现和安全态势",
              "recommendations": ["建议1", "建议2", "建议3"]
            }

            注意：
            - 结论应涵盖文件分析概况、主要威胁发现、标签分布特征
            - 建议行动给出 3-5 条可操作的安全建议
            - 语气正式，适合用于正式安全报告""";

    /**
     * 降级模板文本（{N} 文件数，{M} 标签数）
     */
    private static final String TEMPLATE_CONCLUSION =
            "本期报告共分析 {N} 份文件，识别到 {M} 个关键标签。详细分析请参考报告正文。";

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LlmConfig llmConfig;

    /**
     * report-service 服务地址
     */
    @Value("${report.service.url:http://localhost:8092}")
    private String reportServiceUrl;

    /**
     * JSON 序列化/反序列化工具
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 内存缓存：reportId -> ReportDraft
     *
     * <p>用于 getDraft 查询已生成的草稿。进程重启后丢失。</p>
     */
    private final Map<Long, ReportDraft> draftCache = new ConcurrentHashMap<>();

    /**
     * 生成报告结论草稿
     *
     * @param reportId            报告ID
     * @param statsJson          统计数据 JSON（文件数 / 标签分布 / IOC 数等）
     * @param fileListJson       文件列表 JSON
     * @param tagDistributionJson 标签分布 JSON
     * @return 草稿结果
     */
    public ReportDraft generateDraft(Long reportId, String statsJson,
                                      String fileListJson, String tagDistributionJson) {
        log.info("开始生成报告草稿, reportId={}", reportId);

        ReportDraft draft = new ReportDraft();
        draft.setReportId(reportId);
        draft.setCreatedAt(LocalDateTime.now());

        try {
            // 1. 检查 LLM 是否可用
            if (!llmClient.isAvailable()) {
                log.warn("LLM 服务不可用，降级为模板文本, reportId={}", reportId);
                applyTemplate(draft, statsJson, tagDistributionJson);
                draft.setErrorMessage("LLM 服务不可用，已降级为模板文本");
                cacheDraft(reportId, draft);
                return draft;
            }

            // 2. 构建 Prompt
            String systemPrompt = buildSystemPrompt(statsJson, fileListJson, tagDistributionJson);
            String userPrompt = "请基于上述统计数据生成报告结论段落和建议行动，严格按 JSON 格式输出。";

            // 3. 调用 LLM
            String llmResponse = llmClient.chat(systemPrompt, userPrompt);

            // 4. LLM 不可用降级（返回 null/空）
            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("LLM 返回空响应，降级为模板文本, reportId={}", reportId);
                applyTemplate(draft, statsJson, tagDistributionJson);
                draft.setErrorMessage("LLM 返回空响应，已降级为模板文本");
                cacheDraft(reportId, draft);
                return draft;
            }

            // 5. LLM 正常返回，解析 JSON
            draft.setLlmUsed(true);
            draft.setModel(llmConfig.getModel());
            draft.setTokensUsed(estimateTokens(llmResponse));
            parseLlmResponse(draft, llmResponse);

        } catch (Exception e) {
            log.error("生成报告草稿异常, reportId={}", reportId, e);
            // 异常时降级为模板文本
            applyTemplate(draft, statsJson, tagDistributionJson);
            draft.setLlmUsed(false);
            draft.setErrorMessage("生成异常: " + e.getMessage());
        }

        // 缓存草稿
        cacheDraft(reportId, draft);
        log.info("报告草稿生成完成, reportId={}, llmUsed={}", reportId, draft.isLlmUsed());
        return draft;
    }

    /**
     * 获取报告草稿
     *
     * @param reportId 报告ID
     * @return 草稿结果，无记录时返回 null
     */
    public ReportDraft getDraft(Long reportId) {
        return draftCache.get(reportId);
    }

    /**
     * 构建系统 Prompt
     *
     * @param statsJson          统计数据 JSON
     * @param fileListJson       文件列表 JSON
     * @param tagDistributionJson 标签分布 JSON
     * @return 系统 Prompt
     */
    private String buildSystemPrompt(String statsJson, String fileListJson, String tagDistributionJson) {
        return SYSTEM_PROMPT_TEMPLATE
                .replace("{statsJson}", nullSafeJson(statsJson))
                .replace("{fileListJson}", nullSafeJson(fileListJson))
                .replace("{tagDistributionJson}", nullSafeJson(tagDistributionJson));
    }

    /**
     * 解析 LLM 响应为草稿字段
     *
     * <p>JSON 解析失败时，将原始文本作为 conclusion，recommendations 为空列表。</p>
     *
     * @param draft       待填充的草稿
     * @param llmResponse LLM 原始响应
     */
    private void parseLlmResponse(ReportDraft draft, String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            JsonNode root = objectMapper.readTree(json);

            // 解析 conclusion
            JsonNode conclusionNode = root.path("conclusion");
            if (!conclusionNode.isMissingNode() && !conclusionNode.isNull()
                    && !conclusionNode.asText().isBlank()) {
                draft.setConclusion(conclusionNode.asText());
            } else {
                // conclusion 字段缺失时，使用原文
                draft.setConclusion(llmResponse);
            }

            // 解析 recommendations
            draft.setRecommendations(parseStringArray(root, "recommendations"));
        } catch (Exception e) {
            log.warn("LLM 响应 JSON 解析失败，将原始文本作为 conclusion。原因: {}", e.getMessage());
            // JSON 解析失败时，原文作为 conclusion
            draft.setConclusion(llmResponse);
            draft.setRecommendations(Collections.emptyList());
        }
    }

    /**
     * 降级为模板文本
     *
     * <p>模板：本期报告共分析 {N} 份文件，识别到 {M} 个关键标签。详细分析请参考报告正文。</p>
     *
     * @param draft              待填充的草稿
     * @param statsJson          统计数据 JSON（用于提取文件数）
     * @param tagDistributionJson 标签分布 JSON（用于提取标签数）
     */
    private void applyTemplate(ReportDraft draft, String statsJson, String tagDistributionJson) {
        int fileCount = extractFileCount(statsJson);
        int tagCount = extractTagCount(tagDistributionJson);
        String conclusion = TEMPLATE_CONCLUSION
                .replace("{N}", String.valueOf(fileCount))
                .replace("{M}", String.valueOf(tagCount));
        draft.setConclusion(conclusion);
        draft.setRecommendations(Collections.emptyList());
        draft.setLlmUsed(false);
    }

    /**
     * 从统计数据 JSON 中提取文件数
     *
     * <p>兼容字段名：fileCount / totalFiles / fileTotal / files / file_count</p>
     *
     * @param statsJson 统计数据 JSON
     * @return 文件数，解析失败返回 0
     */
    private int extractFileCount(String statsJson) {
        if (statsJson == null || statsJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(statsJson);
            String[] fields = {"fileCount", "totalFiles", "fileTotal", "files", "file_count"};
            for (String field : fields) {
                JsonNode node = root.path(field);
                if (!node.isMissingNode() && !node.isNull()) {
                    if (node.isInt() || node.isLong()) {
                        return node.asInt();
                    }
                    if (node.isTextual()) {
                        try {
                            return Integer.parseInt(node.asText().trim());
                        } catch (NumberFormatException ignored) {
                            // 忽略，继续尝试其他字段
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("提取文件数失败，使用默认值 0: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 从标签分布 JSON 中提取标签数
     *
     * <p>若为 JSON 对象，返回其字段数；若包含 tagCount 字段则使用该字段；否则返回 0。</p>
     *
     * @param tagDistributionJson 标签分布 JSON
     * @return 标签数，解析失败返回 0
     */
    private int extractTagCount(String tagDistributionJson) {
        if (tagDistributionJson == null || tagDistributionJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(tagDistributionJson);
            if (root.isObject()) {
                return root.size();
            }
            if (root.isArray()) {
                return root.size();
            }
            if (root.isInt() || root.isLong()) {
                return root.asInt();
            }
        } catch (Exception e) {
            log.warn("提取标签数失败，使用默认值 0: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * 解析 JSON 数组字段为字符串列表
     *
     * @param root  JSON 根节点
     * @param field 字段名
     * @return 字符串列表，缺失或非数组返回空列表
     */
    private List<String> parseStringArray(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull() || !node.isArray()) {
            return Collections.emptyList();
        }
        List<String> items = new ArrayList<>();
        node.forEach(item -> {
            if (item != null && !item.isNull()) {
                items.add(item.asText());
            }
        });
        return items;
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
     * JSON 字符串安全处理，null/空返回 "无"
     *
     * @param json JSON 字符串
     * @return 非空字符串
     */
    private String nullSafeJson(String json) {
        return (json == null || json.isBlank()) ? "无" : json;
    }

    /**
     * 缓存草稿
     *
     * @param reportId 报告ID
     * @param draft    草稿
     */
    private void cacheDraft(Long reportId, ReportDraft draft) {
        draftCache.put(reportId, draft);
    }
}
