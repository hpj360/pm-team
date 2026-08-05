package com.redteam.ai.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 威胁情报查询工具
 *
 * <p>查询 IOC（IP/域名/URL/哈希）或 CVE 相关的威胁情报。降级：服务不可用时返回空提示。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class GetThreatIntelTool implements AgentTool {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${threat.intel.service.url:http://localhost:8086}")
    private String threatIntelServiceUrl;

    @Override
    public String getName() {
        return "get_threat_intel";
    }

    @Override
    public String getDescription() {
        return "查询 IOC（IP/域名/URL/哈希）或 CVE 编号对应的威胁情报，"
                + "返回关联的攻击组织、攻击手法、已知漏洞信息。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"indicator\":{\"type\":\"string\",\"description\":\"IOC 值或 CVE 编号，如 192.168.1.1 / CVE-2024-1234\"},"
                + "\"type\":{\"type\":\"string\",\"description\":\"指标类型 ip/domain/url/hash/cve\",\"default\":\"auto\"}"
                + "},\"required\":[\"indicator\"]}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String indicator = params == null ? null : (String) params.get("indicator");
        if (indicator == null || indicator.isBlank()) {
            return "错误：indicator 参数不能为空";
        }
        String type = params.get("type") == null ? "auto" : String.valueOf(params.get("type"));

        try {
            String url = threatIntelServiceUrl + "/api/threat-intel/query?indicator=" + indicator + "&type=" + type;
            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isBlank()) {
                return "未查询到 " + indicator + " 的威胁情报";
            }
            log.info("威胁情报查询成功, indicator={}", indicator);
            return response;
        } catch (Exception e) {
            log.warn("威胁情报查询失败（降级）, indicator={}: {}", indicator, e.getMessage());
            return "威胁情报服务暂时不可用，无法查询 " + indicator + " 的情报。";
        }
    }

    @Override
    public String getRequiredPermission() {
        return "ai:agent:tool:threat-intel";
    }
}
