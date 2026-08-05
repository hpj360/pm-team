package com.redteam.ai.controller;

import com.redteam.ai.client.LlmClient;
import com.redteam.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 服务健康检查控制器
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Tag(name = "AI 服务", description = "AI 服务健康检查接口")
public class HealthController {

    private final LlmClient llmClient;

    /**
     * AI 服务健康检查
     *
     * @return 服务状态信息
     */
    @GetMapping("/health")
    @Operation(summary = "AI 服务健康检查", description = "检查 LLM 服务可用性及当前时间戳")
    public Result<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("llmAvailable", llmClient.isAvailable());
        status.put("timestamp", System.currentTimeMillis());
        return Result.success(status);
    }
}
