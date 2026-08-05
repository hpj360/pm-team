package com.redteam.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.client.LlmClient;
import com.redteam.ai.vo.NlSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自然语言搜索服务
 *
 * <p>利用 LLM 将用户的自然语言搜索请求解析为结构化搜索条件（keyword / searchMode / fileType /
 * tagIds / booleanConditions），然后转发给 search-service 执行实际检索。</p>
 *
 * <p>降级策略：</p>
 * <ul>
 *   <li>LLM 不可用或返回 null —— 降级为简单关键词搜索（将自然语言直接作为 keyword）</li>
 *   <li>LLM 响应 JSON 解析失败 —— 同样降级为简单关键词搜索</li>
 *   <li>search-service 不可用 —— 返回空结果 + 错误信息，不抛出异常</li>
 * </ul>
 *
 * @author 红方团队
 */
@Service
@Slf4j
public class NaturalLanguageSearchService {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * search-service 基础地址
     */
    @Value("${search.service.url:http://localhost:8083}")
    private String searchServiceUrl;

    /**
     * search-service 检索接口路径
     */
    @Value("${search.service.path:/search}")
    private String searchServicePath;

    /**
     * JSON 序列化/反序列化工具
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 系统提示词模板（{naturalLanguageQuery} 占位符会被替换为用户实际输入）
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            你是一个搜索条件解析器。将用户的自然语言搜索请求转换为结构化搜索条件 JSON。

            ## 用户输入
            {naturalLanguageQuery}

            ## 可用标签列表
            - L1.FILE.TYPE.PDF / L1.FILE.TYPE.EXE / L1.FILE.TYPE.PCAP 等（文件类型）
            - L3.ENTITY.IP / L3.ENTITY.DOMAIN / L3.ENTITY.VULN.CVE 等（实体类型）
            - L5.INTEL.APT.APT28 等（APT 组织）
            - L6.SECURITY.CLASSIFICATION.SECRET 等（密级）

            ## 输出要求
            请输出 JSON 格式的搜索条件：
            {
              "keyword": "提取的关键词，如果没有则为空字符串",
              "searchMode": "keyword 或 fulltext，默认 keyword",
              "fileType": "文件类型如 pdf/exe/pcap，如果用户提到了则为对应类型，否则为空",
              "tagIds": [标签ID列表，如果用户提到了标签相关概念],
              "booleanConditions": [
                {"logic": "AND", "field": "fileName", "value": "值"},
                {"logic": "OR", "field": "textContent", "value": "值"}
              ]
            }

            ## 示例
            输入: "查找所有包含 APT28 相关 IP 的 PDF 文件"
            输出: {"keyword": "APT28", "searchMode": "keyword", "fileType": "pdf", "tagIds": [], "booleanConditions": []}

            输入: "搜索包含 CVE-2024 漏洞或恶意IP的文件"
            输出: {"keyword": "", "searchMode": "keyword", "fileType": "", "tagIds": [], "booleanConditions": [{"logic":"OR","field":"textContent","value":"CVE-2024"},{"logic":"OR","field":"textContent","value":"IP"}]}

            注意：只输出 JSON，不要有其他文字。""";

    /**
     * 自然语言搜索
     *
     * <p>流程：
     * <ol>
     *   <li>调用 LLM 解析自然语言为结构化搜索条件</li>
     *   <li>LLM 不可用或解析失败时降级为简单关键词搜索</li>
     *   <li>将结构化条件转换为 search-service 的 SearchRequestDTO 格式</li>
     *   <li>调用 search-service POST /search 执行检索</li>
     *   <li>search-service 不可用时返回空结果 + 错误信息</li>
     * </ol>
     *
     * @param naturalLanguageQuery 用户自然语言输入
     * @return 搜索结果（转发给 search-service 执行）
     */
    public NlSearchResult search(String naturalLanguageQuery) {
        NlSearchResult result = new NlSearchResult();
        result.setNaturalLanguageQuery(naturalLanguageQuery);

        // 1. 解析自然语言为结构化搜索条件
        ParseOutcome outcome = parseInternal(naturalLanguageQuery);
        result.setParsedConditions(outcome.conditions);
        result.setLlmUsed(outcome.llmUsed);
        if (outcome.errorMessage != null) {
            result.setErrorMessage(outcome.errorMessage);
        }

        // 2. 转换为 search-service 的请求格式并调用
        try {
            Map<String, Object> searchRequest = convertToSearchRequest(outcome.conditions);
            String url = searchServiceUrl + searchServicePath;
            log.info("调用 search-service 检索: url={}, conditions={}", url, outcome.conditions);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, searchRequest, Map.class);
            Map<String, Object> responseMap = response.getBody();

            if (responseMap != null) {
                Object data = responseMap.get("data");
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) data;
                    result.setTotal(extractTotal(dataMap.get("total")));
                    result.setResults(extractHits(dataMap.get("hits")));
                } else {
                    log.warn("search-service 返回数据为空或格式异常: {}", responseMap);
                    result.setTotal(0L);
                    result.setResults(Collections.emptyList());
                }
            } else {
                result.setTotal(0L);
                result.setResults(Collections.emptyList());
            }
        } catch (Exception e) {
            log.error("调用 search-service 失败: {}", e.getMessage(), e);
            result.setResults(Collections.emptyList());
            result.setTotal(0L);
            // 拼接已有的错误信息（如 LLM 降级信息）
            String searchError = "search-service 不可用: " + e.getMessage();
            result.setErrorMessage(result.getErrorMessage() == null
                    ? searchError
                    : result.getErrorMessage() + "; " + searchError);
        }

        return result;
    }

    /**
     * 仅将自然语言转换为结构化搜索条件（不执行搜索）
     *
     * @param naturalLanguageQuery 用户自然语言输入
     * @return 结构化搜索条件 Map，包含 keyword / searchMode / fileType / tagIds / booleanConditions
     */
    public Map<String, Object> parseToSearchConditions(String naturalLanguageQuery) {
        return parseInternal(naturalLanguageQuery).conditions;
    }

    // ==================== 内部方法 ====================

    /**
     * 解析自然语言为结构化搜索条件（内部方法，带回解析状态）
     *
     * @param naturalLanguageQuery 自然语言输入
     * @return 解析结果（包含条件、是否使用 LLM、错误信息）
     */
    private ParseOutcome parseInternal(String naturalLanguageQuery) {
        if (naturalLanguageQuery == null || naturalLanguageQuery.isBlank()) {
            return new ParseOutcome(buildFallbackConditions(""), false, "自然语言输入为空");
        }

        // 构建系统提示词（替换占位符）
        String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace("{naturalLanguageQuery}", naturalLanguageQuery);

        // 调用 LLM 解析
        String llmResponse = null;
        try {
            llmResponse = llmClient.chat(systemPrompt, naturalLanguageQuery);
        } catch (Exception e) {
            log.warn("LLM 调用异常: {}", e.getMessage());
            return new ParseOutcome(
                    buildFallbackConditions(naturalLanguageQuery),
                    false,
                    "LLM 调用异常，降级为关键词搜索: " + e.getMessage());
        }

        // LLM 返回 null —— 降级为关键词搜索
        if (llmResponse == null) {
            log.warn("LLM 返回空响应，降级为关键词搜索");
            return new ParseOutcome(
                    buildFallbackConditions(naturalLanguageQuery),
                    false,
                    "LLM 不可用，降级为关键词搜索");
        }

        // 解析 LLM 返回的 JSON
        try {
            String json = extractJson(llmResponse);
            Map<String, Object> conditions = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            // 补全缺失字段
            normalizeConditions(conditions);
            log.info("LLM 解析成功: {}", conditions);
            return new ParseOutcome(conditions, true, null);
        } catch (Exception e) {
            log.error("解析 LLM 响应 JSON 失败: {}, 原始响应: {}", e.getMessage(), llmResponse);
            return new ParseOutcome(
                    buildFallbackConditions(naturalLanguageQuery),
                    false,
                    "LLM 响应 JSON 解析失败，降级为关键词搜索");
        }
    }

    /**
     * 构建降级搜索条件（将自然语言直接作为 keyword）
     *
     * @param naturalLanguageQuery 自然语言输入
     * @return 降级搜索条件 Map
     */
    private Map<String, Object> buildFallbackConditions(String naturalLanguageQuery) {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("keyword", naturalLanguageQuery == null ? "" : naturalLanguageQuery);
        conditions.put("searchMode", "keyword");
        conditions.put("fileType", "");
        conditions.put("tagIds", Collections.emptyList());
        conditions.put("booleanConditions", Collections.emptyList());
        return conditions;
    }

    /**
     * 从 LLM 响应中提取 JSON 字符串
     *
     * <p>LLM 可能将 JSON 包裹在 markdown 代码块中（如 ```json ... ```），
     * 此方法负责剥离代码块标记并提取纯 JSON。</p>
     *
     * @param llmResponse LLM 响应文本
     * @return 纯 JSON 字符串
     */
    private String extractJson(String llmResponse) {
        String text = llmResponse.trim();
        // 剥离 markdown 代码块
        if (text.startsWith("```")) {
            // 移除开头的 ```json 或 ```
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                text = text.substring(firstNewline + 1);
            }
            // 移除结尾的 ```
            int lastFence = text.lastIndexOf("```");
            if (lastFence >= 0) {
                text = text.substring(0, lastFence);
            }
            text = text.trim();
        }
        // 提取第一个 { 到最后一个 } 之间的内容
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    /**
     * 规范化搜索条件，补全缺失字段
     *
     * @param conditions 搜索条件 Map
     */
    @SuppressWarnings("unchecked")
    private void normalizeConditions(Map<String, Object> conditions) {
        if (!conditions.containsKey("keyword") || conditions.get("keyword") == null) {
            conditions.put("keyword", "");
        }
        if (!conditions.containsKey("searchMode") || conditions.get("searchMode") == null) {
            conditions.put("searchMode", "keyword");
        }
        if (!conditions.containsKey("fileType") || conditions.get("fileType") == null) {
            conditions.put("fileType", "");
        }
        if (!conditions.containsKey("tagIds") || !(conditions.get("tagIds") instanceof List)) {
            conditions.put("tagIds", Collections.emptyList());
        }
        if (!conditions.containsKey("booleanConditions") || !(conditions.get("booleanConditions") instanceof List)) {
            conditions.put("booleanConditions", Collections.emptyList());
        }
    }

    /**
     * 将 LLM 解析的搜索条件转换为 search-service 的 SearchRequestDTO 格式
     *
     * <p>字段映射：
     * <ul>
     *   <li>keyword → query</li>
     *   <li>searchMode → searchType（KEYWORD / VECTOR / HYBRID）</li>
     *   <li>fileType → fileType</li>
     *   <li>tagIds → tags</li>
     *   <li>booleanConditions → booleanConditions</li>
     * </ul>
     *
     * @param conditions LLM 解析的搜索条件
     * @return search-service 请求体 Map
     */
    private Map<String, Object> convertToSearchRequest(Map<String, Object> conditions) {
        Map<String, Object> request = new HashMap<>();
        // keyword → query
        Object keyword = conditions.get("keyword");
        request.put("query", keyword == null ? "" : keyword.toString());
        // searchMode → searchType（统一转为大写）
        Object searchMode = conditions.get("searchMode");
        String searchType = (searchMode == null || searchMode.toString().isBlank())
                ? "KEYWORD"
                : searchMode.toString().toUpperCase();
        request.put("searchType", searchType);
        // fileType
        Object fileType = conditions.get("fileType");
        request.put("fileType", fileType == null ? "" : fileType.toString());
        // tagIds → tags
        Object tagIds = conditions.get("tagIds");
        if (tagIds instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> tags = (List<Object>) tagIds;
            request.put("tags", tags);
        } else {
            request.put("tags", Collections.emptyList());
        }
        // booleanConditions
        Object booleanConditions = conditions.get("booleanConditions");
        if (booleanConditions instanceof List) {
            request.put("booleanConditions", booleanConditions);
        } else {
            request.put("booleanConditions", Collections.emptyList());
        }
        // 分页参数
        request.put("pageNum", 1);
        request.put("pageSize", 10);
        return request;
    }

    /**
     * 从 search-service 响应中提取命中总数
     *
     * @param totalObj total 字段值
     * @return 命中总数
     */
    private Long extractTotal(Object totalObj) {
        if (totalObj == null) {
            return 0L;
        }
        if (totalObj instanceof Number) {
            return ((Number) totalObj).longValue();
        }
        try {
            return Long.parseLong(totalObj.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 从 search-service 响应中提取命中列表
     *
     * @param hitsObj hits 字段值
     * @return 命中列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractHits(Object hitsObj) {
        if (hitsObj instanceof List) {
            List<Map<String, Object>> hits = new ArrayList<>();
            for (Object item : (List<Object>) hitsObj) {
                if (item instanceof Map) {
                    hits.add((Map<String, Object>) item);
                }
            }
            return hits;
        }
        return Collections.emptyList();
    }

    /**
     * 解析结果内部封装
     */
    private static class ParseOutcome {
        /** 解析出的搜索条件 */
        final Map<String, Object> conditions;
        /** 是否成功使用 LLM 解析 */
        final boolean llmUsed;
        /** 错误信息（降级时非空） */
        final String errorMessage;

        ParseOutcome(Map<String, Object> conditions, boolean llmUsed, String errorMessage) {
            this.conditions = conditions;
            this.llmUsed = llmUsed;
            this.errorMessage = errorMessage;
        }
    }
}
