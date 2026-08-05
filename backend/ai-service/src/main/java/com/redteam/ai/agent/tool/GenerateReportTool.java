package com.redteam.ai.agent.tool;

import com.redteam.ai.client.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 报告草稿生成工具
 *
 * <p>基于已有证据与上下文生成分析报告草稿。降级：LLM 不可用时返回模板文本。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class GenerateReportTool implements AgentTool {

    private static final String DEGRADED_TEMPLATE =
            "## 分析报告草稿（降级模板）\n\n"
                    + "基于当前收集的证据，生成本阶段分析报告草稿：\n\n"
                    + "### 关键发现\n%s\n\n"
                    + "### 建议\n- 进一步验证关联性\n- 收集补充证据\n- 评估影响范围\n";

    @Autowired
    private LlmClient llmClient;

    @Override
    public String getName() {
        return "generate_report";
    }

    @Override
    public String getDescription() {
        return "基于收集到的证据与分析上下文，生成结构化的分析报告草稿，"
                + "包含关键发现、结论与建议行动。";
    }

    @Override
    public String getParametersSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"context\":{\"type\":\"string\",\"description\":\"已收集的证据与分析上下文\"},"
                + "\"title\":{\"type\":\"string\",\"description\":\"报告标题\",\"default\":\"分析报告\"}"
                + "},\"required\":[\"context\"]}";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String context = params == null ? null : (String) params.get("context");
        if (context == null || context.isBlank()) {
            return "错误：context 参数不能为空";
        }
        String title = params.get("title") == null ? "分析报告" : String.valueOf(params.get("title"));

        // LLM 不可用降级
        if (!llmClient.isAvailable()) {
            log.warn("LLM 不可用，报告草稿降级为模板文本");
            return String.format(DEGRADED_TEMPLATE, context);
        }

        try {
            String systemPrompt = "你是一名红方安全报告撰写专家。请基于以下证据与上下文生成结构化的分析报告草稿，"
                    + "包含关键发现、结论与建议行动。使用 Markdown 格式输出。";
            String userPrompt = "报告标题：" + title + "\n\n证据与上下文：\n" + context;
            String response = llmClient.chat(systemPrompt, userPrompt);
            if (response == null || response.isBlank()) {
                log.warn("LLM 返回空，报告草稿降级为模板文本");
                return String.format(DEGRADED_TEMPLATE, context);
            }
            log.info("报告草稿生成成功, title={}", title);
            return response;
        } catch (Exception e) {
            log.warn("报告草稿生成异常（降级）: {}", e.getMessage());
            return String.format(DEGRADED_TEMPLATE, context);
        }
    }

    @Override
    public String getRequiredPermission() {
        return "ai:agent:tool:report";
    }
}
