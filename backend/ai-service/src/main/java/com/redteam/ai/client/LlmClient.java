package com.redteam.ai.client;

/**
 * LLM 大模型客户端抽象接口
 *
 * <p>封装大语言模型调用，屏蔽底层实现差异（Ollama 本地 / 远程 OpenAI 兼容 API）。</p>
 *
 * @author 红方团队
 */
public interface LlmClient {

    /**
     * 发送 chat completion 请求
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户输入
     * @return LLM 响应文本，调用失败返回 null
     */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 发送 chat completion 请求（带参数）
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt    用户输入
     * @param temperature   采样温度（0.0~2.0，值越大随机性越强）
     * @param maxTokens     最大生成 token 数
     * @return LLM 响应文本，调用失败返回 null
     */
    String chat(String systemPrompt, String userPrompt, double temperature, int maxTokens);

    /**
     * 检查 LLM 服务是否可用
     *
     * @return true 表示可用，false 表示不可用
     */
    boolean isAvailable();
}
