package com.redteam.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.agent.tool.AgentTool;
import com.redteam.ai.client.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 执行器（ReAct 模式）
 *
 * <p>实现 Plan → Act → Observe → Reflect 的 ReAct 推理循环：
 * <ol>
 *   <li>构造 prompt（含系统指令 + 可用工具 + 历史轨迹）</li>
 *   <li>LLM 返回 Thought + Action/Final Answer</li>
 *   <li>若为 Action：执行工具 → 获取 Observation → 加入轨迹 → 回到步骤 1</li>
 *   <li>若为 Final Answer：结束循环，返回结论</li>
 * </ol>
 * </p>
 *
 * <p>限制与降级：
 * <ul>
 *   <li>最大步数限制（默认 10 步），超出后强制总结</li>
 *   <li>总 token 预算控制</li>
 *   <li>LLM 不可用时返回降级结论</li>
 *   <li>每步工具执行异常不阻塞循环</li>
 * </ul>
 * </p>
 *
 * @author 红方团队
 */
@Component
@Slf4j
public class AgentExecutor {

    /**
     * 默认最大步数
     */
    private static final int DEFAULT_MAX_STEPS = 10;

    /**
     * 默认 token 预算
     */
    private static final int DEFAULT_TOKEN_BUDGET = 8000;

    /**
     * Thought 正则
     */
    private static final Pattern THOUGHT_PATTERN =
            Pattern.compile("(?i)Thought\\s*[:：]\\s*(.+?)(?=\\n\\s*(?:Action|Final Answer|最终答案|动作)|$)",
                    Pattern.DOTALL);

    /**
     * Action 正则
     */
    private static final Pattern ACTION_PATTERN =
            Pattern.compile("(?i)Action\\s*[:：]\\s*(.+?)(?=\\n\\s*(?:Action Input|动作输入)|$)",
                    Pattern.DOTALL);

    /**
     * Action Input 正则
     */
    private static final Pattern ACTION_INPUT_PATTERN =
            Pattern.compile("(?i)Action Input\\s*[:：]\\s*(\\{.*?\\}|.+?)(?=\\n\\s*(?:Thought|Observation|思考|观察)|$)",
                    Pattern.DOTALL);

    /**
     * Final Answer 正则
     */
    private static final Pattern FINAL_ANSWER_PATTERN =
            Pattern.compile("(?i)(?:Final Answer|最终答案)\\s*[:：]\\s*(.+)$", Pattern.DOTALL);

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private ToolRegistry toolRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行 Agent 推理循环
     *
     * @param query     用户分析请求
     * @param userPerms 用户权限集合（用于工具权限校验）
     * @return Agent 执行结果
     */
    public AgentResult execute(String query, Set<String> userPerms) {
        return execute(query, userPerms, DEFAULT_MAX_STEPS, DEFAULT_TOKEN_BUDGET);
    }

    /**
     * 执行 Agent 推理循环（自定义参数）
     *
     * @param query       用户分析请求
     * @param userPerms   用户权限集合
     * @param maxSteps    最大步数
     * @param tokenBudget 总 token 预算
     * @return Agent 执行结果
     */
    public AgentResult execute(String query, Set<String> userPerms, int maxSteps, int tokenBudget) {
        log.info("Agent 开始执行, query={}, maxSteps={}, tokenBudget={}", query, maxSteps, tokenBudget);

        List<AgentTrace> traces = new ArrayList<>();
        List<String> evidenceChain = new ArrayList<>();
        List<String> referencedFiles = new ArrayList<>();

        // LLM 不可用降级
        if (!llmClient.isAvailable()) {
            log.warn("LLM 不可用，Agent 降级返回模板结论");
            return AgentResult.builder()
                    .conclusion(buildDegradedConclusion(query, traces))
                    .evidenceChain(evidenceChain)
                    .referencedFiles(referencedFiles)
                    .confidence(0.1)
                    .traces(traces)
                    .degraded(true)
                    .errorMessage("LLM 服务不可用，已降级为模板结论")
                    .build();
        }

        int usedTokens = 0;

        for (int step = 1; step <= maxSteps; step++) {
            // token 预算控制
            if (usedTokens >= tokenBudget) {
                log.warn("达到 token 预算上限，强制结束, step={}, usedTokens={}", step, usedTokens);
                String conclusion = forceSummarize(query, traces);
                return AgentResult.builder()
                        .conclusion(conclusion)
                        .evidenceChain(evidenceChain)
                        .referencedFiles(referencedFiles)
                        .confidence(0.3)
                        .traces(traces)
                        .degraded(false)
                        .errorMessage("达到 token 预算上限，强制总结")
                        .build();
            }

            // 1. 构造 prompt
            String prompt = buildPrompt(query, traces);
            String systemPrompt = buildSystemPrompt();

            // 2. 调用 LLM
            String llmResponse;
            try {
                llmResponse = llmClient.chat(systemPrompt, prompt);
            } catch (Exception e) {
                log.error("LLM 调用异常, step={}: {}", step, e.getMessage());
                return AgentResult.builder()
                        .conclusion(buildDegradedConclusion(query, traces))
                        .evidenceChain(evidenceChain)
                        .referencedFiles(referencedFiles)
                        .confidence(0.2)
                        .traces(traces)
                        .degraded(true)
                        .errorMessage("LLM 调用异常: " + e.getMessage())
                        .build();
            }

            usedTokens += estimateTokens(llmResponse);

            if (llmResponse == null || llmResponse.isBlank()) {
                log.warn("LLM 返回空响应, step={}", step);
                AgentTrace trace = new AgentTrace(step, "LLM 返回空响应", "EMPTY", "", "无观察结果");
                traces.add(trace);
                continue;
            }

            // 3. 解析 LLM 响应
            String thought = extractField(llmResponse, THOUGHT_PATTERN, "无法解析思考过程");
            String finalAnswer = extractField(llmResponse, FINAL_ANSWER_PATTERN, null);

            // 4. 若为 Final Answer，结束循环
            if (finalAnswer != null && !finalAnswer.isBlank()) {
                AgentTrace trace = new AgentTrace(step, thought, "FINAL_ANSWER", "", finalAnswer);
                traces.add(trace);
                log.info("Agent 完成, step={}, finalAnswerLen={}", step, finalAnswer.length());
                return AgentResult.builder()
                        .conclusion(finalAnswer.trim())
                        .evidenceChain(evidenceChain)
                        .referencedFiles(referencedFiles)
                        .confidence(calculateConfidence(traces))
                        .traces(traces)
                        .degraded(false)
                        .build();
            }

            // 5. 若为 Action，执行工具
            String actionName = extractField(llmResponse, ACTION_PATTERN, null);
            String actionInput = extractField(llmResponse, ACTION_INPUT_PATTERN, "{}");

            if (actionName == null || actionName.isBlank()) {
                // 既非 Final Answer 也非 Action，记录并继续
                AgentTrace trace = new AgentTrace(step, thought, "UNKNOWN", "",
                        "无法解析动作，原文: " + truncate(llmResponse, 200));
                traces.add(trace);
                log.warn("无法解析 LLM 响应为动作或最终答案, step={}", step);
                continue;
            }

            actionName = actionName.trim();
            Map<String, Object> params = parseActionInput(actionInput);

            // 执行工具
            String observation;
            AgentTool tool = toolRegistry.getTool(actionName);
            if (tool == null) {
                observation = "错误：工具 " + actionName + " 不存在，请从可用工具列表中选择。";
            } else if (!toolRegistry.checkPermission(actionName, userPerms)) {
                observation = "错误：权限不足，无法调用工具 " + actionName;
            } else {
                try {
                    observation = tool.execute(params);
                    // 收集证据
                    evidenceChain.add("步骤" + step + " 调用 " + actionName + ": " + truncate(observation, 300));
                    // 提取引用文件
                    extractReferencedFiles(observation, referencedFiles);
                } catch (Exception e) {
                    observation = "工具执行异常: " + e.getMessage();
                    log.error("工具执行异常, tool={}: {}", actionName, e.getMessage());
                }
            }

            AgentTrace trace = new AgentTrace(step, thought, actionName, actionInput, truncate(observation, 1000));
            traces.add(trace);
            log.info("Agent 步骤 {}/{}, action={}, observationLen={}", step, maxSteps, actionName,
                    observation.length());
        }

        // 达到最大步数，强制总结
        log.warn("达到最大步数 {}，强制总结", maxSteps);
        String conclusion = forceSummarize(query, traces);
        return AgentResult.builder()
                .conclusion(conclusion)
                .evidenceChain(evidenceChain)
                .referencedFiles(referencedFiles)
                .confidence(calculateConfidence(traces))
                .traces(traces)
                .degraded(false)
                .errorMessage("达到最大步数限制，强制总结")
                .build();
    }

    /**
     * 构造系统 Prompt
     *
     * @return 系统 Prompt
     */
    private String buildSystemPrompt() {
        StringBuilder toolsDesc = new StringBuilder();
        for (AgentTool tool : toolRegistry.listTools()) {
            toolsDesc.append("- ").append(tool.getName())
                    .append(": ").append(tool.getDescription()).append("\n")
                    .append("  参数: ").append(tool.getParametersSchema()).append("\n");
        }

        return "你是一名红方网络安全分析 Agent。请使用 ReAct（Reasoning + Acting）模式完成用户的分析请求。\n\n"
                + "## 可用工具\n"
                + toolsDesc
                + "\n## 输出格式\n"
                + "每一步请严格按以下格式输出（不要添加额外标记）：\n\n"
                + "Thought: <你的思考过程，分析当前状态与下一步行动>\n"
                + "Action: <工具名称，从可用工具列表中选择>\n"
                + "Action Input: <工具参数 JSON 对象>\n\n"
                + "当你已收集足够信息可以回答用户问题时，使用以下格式输出最终答案：\n\n"
                + "Thought: <你的思考过程>\n"
                + "Final Answer: <最终结论，需基于工具观察结果>\n\n"
                + "## 注意事项\n"
                + "- 每次只调用一个工具\n"
                + "- Action Input 必须是合法的 JSON 对象\n"
                + "- 基于工具返回的观察结果进行推理，不要臆造信息\n"
                + "- 最多执行 " + DEFAULT_MAX_STEPS + " 步\n";
    }

    /**
     * 构造每步的用户 Prompt（含历史轨迹）
     *
     * @param query  用户请求
     * @param traces 历史轨迹
     * @return 用户 Prompt
     */
    private String buildPrompt(String query, List<AgentTrace> traces) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户分析请求\n").append(query).append("\n\n");

        if (!traces.isEmpty()) {
            sb.append("## 已执行的步骤\n");
            for (AgentTrace trace : traces) {
                sb.append("### 步骤 ").append(trace.getStep()).append("\n");
                sb.append("Thought: ").append(trace.getThought()).append("\n");
                sb.append("Action: ").append(trace.getAction()).append("\n");
                sb.append("Action Input: ").append(trace.getActionInput()).append("\n");
                sb.append("Observation: ").append(trace.getObservation()).append("\n\n");
            }
        }

        sb.append("## 请继续\n");
        sb.append("请基于当前状态决定下一步（Thought + Action/Final Answer）。\n");
        return sb.toString();
    }

    /**
     * 从 LLM 响应中提取字段
     *
     * @param response   LLM 响应
     * @param pattern    正则
     * @param defaultValue 默认值
     * @return 提取结果
     */
    private String extractField(String response, Pattern pattern, String defaultValue) {
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return defaultValue;
    }

    /**
     * 解析 Action Input JSON
     *
     * @param actionInput JSON 字符串
     * @return 参数 Map
     */
    private Map<String, Object> parseActionInput(String actionInput) {
        if (actionInput == null || actionInput.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(actionInput.trim(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Action Input 解析失败: {}", actionInput);
            // 降级：将整个字符串作为 query 参数
            return Map.of("query", actionInput.trim());
        }
    }

    /**
     * 强制总结（达到步数/token 限制时）
     *
     * @param query  原始请求
     * @param traces 历史轨迹
     * @return 总结结论
     */
    private String forceSummarize(String query, List<AgentTrace> traces) {
        if (traces.isEmpty()) {
            return buildDegradedConclusion(query, traces);
        }

        // 尝试调用 LLM 进行总结
        if (llmClient.isAvailable()) {
            try {
                StringBuilder evidence = new StringBuilder();
                for (AgentTrace trace : traces) {
                    evidence.append("步骤").append(trace.getStep())
                            .append(": ").append(trace.getAction())
                            .append(" → ").append(trace.getObservation()).append("\n");
                }
                String systemPrompt = "你是一名红方安全分析专家。请基于以下 Agent 执行轨迹，"
                        + "生成一份简明的分析结论。";
                String userPrompt = "原始请求：" + query + "\n\n执行轨迹：\n" + evidence;
                String response = llmClient.chat(systemPrompt, userPrompt);
                if (response != null && !response.isBlank()) {
                    return response;
                }
            } catch (Exception e) {
                log.warn("强制总结 LLM 调用失败: {}", e.getMessage());
            }
        }

        // 降级：基于轨迹拼接
        StringBuilder sb = new StringBuilder();
        sb.append("基于 ").append(traces.size()).append(" 步分析，初步结论如下：\n\n");
        for (AgentTrace trace : traces) {
            if (!"FINAL_ANSWER".equals(trace.getAction()) && trace.getObservation() != null) {
                sb.append("- ").append(truncate(trace.getObservation(), 150)).append("\n");
            }
        }
        sb.append("\n（注：因达到执行上限，结论基于已有轨迹总结，建议进一步验证。）");
        return sb.toString();
    }

    /**
     * 构造降级结论（LLM 不可用时）
     *
     * @param query  用户请求
     * @param traces 历史轨迹
     * @return 降级结论
     */
    private String buildDegradedConclusion(String query, List<AgentTrace> traces) {
        return "【降级结论】LLM 服务当前不可用，无法对「" + query + "」进行 AI 推理分析。"
                + "已执行步骤数：" + traces.size() + "。"
                + "请稍后重试或联系管理员检查 LLM 服务状态。";
    }

    /**
     * 计算置信度（基于执行步数与是否正常完成）
     *
     * @param traces 历史轨迹
     * @return 置信度 0.0~1.0
     */
    private double calculateConfidence(List<AgentTrace> traces) {
        if (traces.isEmpty()) {
            return 0.1;
        }
        // 检查最后一步是否为 FINAL_ANSWER
        AgentTrace last = traces.get(traces.size() - 1);
        if ("FINAL_ANSWER".equals(last.getAction())) {
            // 步数越少置信度越高
            return Math.max(0.5, 0.95 - traces.size() * 0.05);
        }
        // 非正常结束
        return Math.max(0.2, 0.6 - traces.size() * 0.05);
    }

    /**
     * 从工具观察结果中提取引用文件 ID
     *
     * @param observation   观察结果
     * @param referencedFiles 引用文件列表（输出）
     */
    private void extractReferencedFiles(String observation, List<String> referencedFiles) {
        if (observation == null) {
            return;
        }
        // 简单提取 fileId 字段值
        Pattern fileIdPattern = Pattern.compile("\"fileId\"\\s*[:：]\\s*\"([^\"]+)\"");
        Matcher matcher = fileIdPattern.matcher(observation);
        while (matcher.find()) {
            String fileId = matcher.group(1);
            if (!referencedFiles.contains(fileId)) {
                referencedFiles.add(fileId);
            }
        }
    }

    /**
     * 粗略估算 token 数（4 字符 ≈ 1 token）
     *
     * @param text 文本
     * @return 估算 token 数
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 4;
    }

    /**
     * 截断字符串
     *
     * @param text   原始文本
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
