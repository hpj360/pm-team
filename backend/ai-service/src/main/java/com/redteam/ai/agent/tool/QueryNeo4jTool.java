package com.redteam.ai.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Neo4j 关系图谱查询工具
 *
 * <p>查询关系图谱中实体间的关系（如 IP→域名→组织→攻击链）。
 * 降级：profile-service 不可用时返回空关系提示。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class QueryNeo4jTool implements AgentTool {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${profile.service.url:http://localhost:8085}")
    private String profileServiceUrl;

    @Override
    public String getName() {
        return "query_neo4j";
    }

    @Override
    public String getDescription() {
        return "查询 Neo4j 关系图谱，根据实体值查询其关联关系（如 IP 关联的域名、组织、"
                + "攻击手法等），返回关系路径与节点信息。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"entity\":{\"type\":\"string\",\"description\":\"查询的实体值，如 IP 地址、域名、组织名\"},"
                + "\"depth\":{\"type\":\"integer\",\"description\":\"查询深度，默认2\",\"default\":2}"
                + "},\"required\":[\"entity\"]}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String entity = params == null ? null : (String) params.get("entity");
        if (entity == null || entity.isBlank()) {
            return "错误：entity 参数不能为空";
        }
        int depth = 2;
        Object depthObj = params.get("depth");
        if (depthObj instanceof Number) {
            depth = ((Number) depthObj).intValue();
        }

        try {
            String url = profileServiceUrl + "/api/profile/relations/query";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of("entities", java.util.Collections.singletonList(entity),
                    "depth", depth);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String response = restTemplate.postForObject(url, request, String.class);
            if (response == null || response.isBlank()) {
                return "未查询到 " + entity + " 的关联关系";
            }
            log.info("关系图谱查询成功, entity={}", entity);
            return response;
        } catch (Exception e) {
            log.warn("关系图谱查询失败（降级）, entity={}: {}", entity, e.getMessage());
            return "关系图谱服务暂时不可用，无法查询 " + entity + " 的关联关系。";
        }
    }

    @Override
    public String getRequiredPermission() {
        return "ai:agent:tool:neo4j";
    }
}
