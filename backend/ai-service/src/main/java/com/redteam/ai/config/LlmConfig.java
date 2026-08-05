package com.redteam.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM 大模型配置
 *
 * <p>通过 {@code llm.*} 配置项注入，支持 Ollama 本地模式与远程 OpenAI 兼容 API 模式切换。</p>
 *
 * @author 红方团队
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    /**
     * LLM 提供方：ollama / remote
     */
    private String provider = "ollama";

    /**
     * LLM 服务端点
     */
    private String endpoint = "http://localhost:11434";

    /**
     * 模型名称
     */
    private String model = "qwen2.5:7b";

    /**
     * API Key（remote 模式使用）
     */
    private String apiKey = "";

    /**
     * 请求超时时间（毫秒）
     */
    private int timeout = 60000;

    /**
     * 最大生成 token 数
     */
    private int maxTokens = 2048;

    /**
     * 采样温度（0.0~2.0）
     */
    private double temperature = 0.7;
}
