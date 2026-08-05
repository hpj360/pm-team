package com.redteam.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.config.LlmConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ollama 本地大模型客户端
 *
 * <p>通过 RestTemplate 调用 Ollama HTTP API（{@code POST {endpoint}/api/chat}），
 * 默认 Provider，当 {@code llm.provider} 未配置或为 {@code ollama} 时生效。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaLlmClient implements LlmClient {

    private final RestTemplate restTemplate;
    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper;

    public OllamaLlmClient(RestTemplate restTemplate, LlmConfig llmConfig) {
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
            String url = llmConfig.getEndpoint() + "/api/chat";

            Map<String, Object> requestBody = buildRequestBody(systemPrompt, userPrompt, temperature, maxTokens);

            String response = restTemplate.postForObject(url, requestBody, String.class);
            if (response == null) {
                log.error("Ollama 返回空响应");
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            String content = root.path("message").path("content").asText();
            log.debug("Ollama 调用成功, 响应长度: {}", content.length());
            return content;
        } catch (Exception e) {
            log.error("调用 Ollama 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            String url = llmConfig.getEndpoint() + "/api/tags";
            restTemplate.getForObject(url, String.class);
            log.debug("Ollama 服务可用");
            return true;
        } catch (Exception e) {
            log.error("Ollama 服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 构建 Ollama chat 请求体
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @param temperature  采样温度
     * @param maxTokens     最大生成 token 数（Ollama 字段为 num_predict）
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

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", temperature);
        options.put("num_predict", maxTokens);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", llmConfig.getModel());
        requestBody.put("messages", messages);
        requestBody.put("stream", false);
        requestBody.put("options", options);
        return requestBody;
    }
}
