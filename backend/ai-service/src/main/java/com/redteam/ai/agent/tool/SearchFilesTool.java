package com.redteam.ai.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 文件检索工具
 *
 * <p>调用 search-service 检索与查询相关的文件。降级：search-service 不可用时返回空结果提示。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class SearchFilesTool implements AgentTool {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${search.service.url:http://localhost:8081}")
    private String searchServiceUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "search_files";
    }

    @Override
    public String getDescription() {
        return "检索文件库中与查询关键词相关的文件，返回文件ID、文件名、匹配分数与摘要。"
                + "适用于查找样本、报告、日志等文件资料。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\",\"description\":\"检索关键词或自然语言查询\"},"
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
            String url = searchServiceUrl + "/api/search/files?query=" + query + "&topK=" + topK;
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isBlank()) {
                return "未检索到相关文件（query=" + query + "）";
            }
            log.info("文件检索成功, query={}, responseLen={}", query, response.length());
            return response;
        } catch (Exception e) {
            log.warn("文件检索失败（降级）, query={}: {}", query, e.getMessage());
            return "文件检索服务暂时不可用，请基于已有信息继续分析。（query=" + query + "）";
        }
    }

    @Override
    public String getRequiredPermission() {
        return "ai:agent:tool:search";
    }
}
