package com.redteam.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.config.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程 LLM 客户端
 *
 * <p>调用兼容 OpenAI API 格式的远程服务（{@code POST {endpoint}/v1/chat/completions}），
 * 当 {@code llm.provider=remote} 时生效。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "remote")
public class RemoteLlmClient implements LlmClient {

    private final RestTemplate restTemplate;
    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper;

    public RemoteLlmClient(RestTemplate restTemplate, LlmConfig llmConfig) {
        this.restTemplate = restTemplate;
        this.llmConfig = llmConfig;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, llmConfig.getTemperature(), llmConfig.getMaxTokens());
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
        try {
            String url = llmConfig.getEndpoint() + "/v1/chat/completions";

            Map<String, Object> requestBody = buildRequestBody(systemPrompt, userPrompt, temperature, maxTokens);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(llmConfig.getApiKey());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null) {
                log.error("远程 LLM 返回空响应");
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            log.debug("远程 LLM 调用成功, 响应长度: {}", content.length());
            return content;
        } catch (Exception e) {
            log.error("调用远程 LLM 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String result = chat("You are a health checker. Reply with 'ok'.", "ping");
            boolean available = result != null;
            if (available) {
                log.debug("远程 LLM 服务可用");
            }
            return available;
        } catch (Exception e) {
            log.error("远程 LLM 服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 构建 OpenAI 兼容 chat 请求体
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @param temperature  采样温度
     * @param maxTokens     最大生成 token 数
     * @return 请求体 Map
     */
    private Map<String, Object> buildRequestBody(String systemPrompt, String userPrompt,
                                                   double temperature, int maxTokens) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmConfig.getModel());
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        return requestBody;
    }
}
