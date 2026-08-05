package com.redteam.ai.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.agent.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * RAG 知识库检索工具
 *
 * <p>从知识库（ATT&CK 矩阵、CVE 漏洞库、APT 组织档案、历史分析报告）中
 * 语义检索相关知识片段。降级：RagService 内部已处理降级。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class SearchKnowledgeTool implements AgentTool {

    @Autowired
    private RagService ragService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "search_knowledge";
    }

    @Override
    public String getDescription() {
        return "从知识库（ATT&CK 矩阵、CVE 漏洞库、APT 组织档案、历史分析报告）中"
                + "语义检索相关知识片段，返回匹配的文档内容与来源。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\",\"description\":\"检索查询语句\"},"
                + "\"topK\":{\"type\":\"integer\",\"description\":\"返回条数，默认5\",\"default\":5}"
                + "},\"required\":[\"query\"]}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String query = params == null ? null : (String) params.get("query");
        if (query == null || query.isBlank()) {
            return "错误：query 参数不能为空";
        }
        int topK = 5;
        Object topKObj = params.get("topK");
        if (topKObj instanceof Number) {
            topK = ((Number) topKObj).intValue();
        }

        try {
            List<Map<String, Object>> results = ragService.search(query, topK);
            if (results.isEmpty()) {
                return "知识库中未检索到与「" + query + "」相关的内容";
            }
            String json = objectMapper.writeValueAsString(results);
            log.info("知识库检索成功, query={}, results={}", query, results.size());
            return json;
        } catch (Exception e) {
            log.warn("知识库检索失败（降级）, query={}: {}", query, e.getMessage());
            return "知识库检索暂时不可用，无法查询「" + query + "」相关内容。";
        }
    }

    @Override
    public String getRequiredPermission() {
        return "ai:agent:tool:knowledge";
    }
}
