package com.redteam.ai.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * NER 实体识别工具
 *
 * <p>调用 NER 服务对输入文本进行命名实体识别，提取 IP、域名、CVE、组织名等实体。
 * 降级：NER 服务不可用时返回空实体提示。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class RunNerTool implements AgentTool {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${ner.service.url:http://localhost:8087}")
    private String nerServiceUrl;

    @Override
    public String getName() {
        return "run_ner";
    }

    @Override
    public String getDescription() {
        return "对输入文本进行命名实体识别（NER），提取 IP、域名、URL、CVE、哈希、"
                + "组织名、人员、地点等实体，返回实体列表与置信度。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"text\":{\"type\":\"string\",\"description\":\"待识别的文本内容\"}"
                + "},\"required\":[\"text\"]}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String text = params == null ? null : (String) params.get("text");
        if (text == null || text.isBlank()) {
            return "错误：text 参数不能为空";
        }

        try {
            String url = nerServiceUrl + "/api/ner/recognize";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("text", text);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            String response = restTemplate.postForObject(url, request, String.class);
            if (response == null || response.isBlank()) {
                return "NER 识别未返回结果";
            }
            log.info("NER 识别成功, textLen={}", text.length());
            return response;
        } catch (Exception e) {
            log.warn("NER 识别失败（降级）, textLen={}: {}", text.length(), e.getMessage());
            return "NER 服务暂时不可用，无法识别文本中的实体。";
        }
    }

    @Override
    public String getRequiredPermission() {
        return "ai:agent:tool:ner";
    }
}
