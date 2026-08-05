package com.redteam.ai.client;

import com.redteam.ai.config.LlmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OllamaLlmClient} 单元测试
 *
 * <p>使用 Mockito 模拟 RestTemplate，覆盖 chat 成功、超时、isAvailable 可用/不可用四类场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
class OllamaLlmClientTest {

    @Mock
    private RestTemplate restTemplate;

    private OllamaLlmClient ollamaLlmClient;

    @BeforeEach
    void setUp() {
        LlmConfig llmConfig = new LlmConfig();
        // 默认端点 http://localhost:11434，模型 qwen2.5:7b
        ollamaLlmClient = new OllamaLlmClient(restTemplate, llmConfig);
    }

    /**
     * chat - 正常响应应返回 message.content 内容
     */
    @Test
    @DisplayName("chat - 正常响应应返回内容")
    void testChat_Success() {
        String responseJson = "{\"message\":{\"role\":\"assistant\",\"content\":\"你好，我是AI助手\"}}";
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(responseJson);

        String result = ollamaLlmClient.chat("你是助手", "你好");

        assertEquals("你好，我是AI助手", result);
        verify(restTemplate).postForObject(anyString(), any(), eq(String.class));
    }

    /**
     * chat - 超时异常应返回 null 且不抛出异常
     */
    @Test
    @DisplayName("chat - 超时异常应返回 null")
    void testChat_Timeout() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("connect timed out"));

        String result = ollamaLlmClient.chat("你是助手", "你好");

        assertNull(result);
    }

    /**
     * isAvailable - LLM 服务可用应返回 true
     */
    @Test
    @DisplayName("isAvailable - 服务可用应返回 true")
    void testIsAvailable_True() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn("{\"models\":[]}");

        boolean available = ollamaLlmClient.isAvailable();

        assertTrue(available);
    }

    /**
     * isAvailable - LLM 服务不可用应返回 false
     */
    @Test
    @DisplayName("isAvailable - 服务不可用应返回 false")
    void testIsAvailable_False() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        boolean available = ollamaLlmClient.isAvailable();

        assertFalse(available);
    }
}
