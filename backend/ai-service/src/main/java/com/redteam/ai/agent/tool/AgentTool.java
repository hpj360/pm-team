package com.redteam.ai.agent.tool;

import java.util.Map;

/**
 * Agent 工具接口
 *
 * <p>V5.1 AI Agent 化模块的统一工具抽象。每个工具代表一种 Agent 可调用的能力，
 * 如文件检索、威胁情报查询、NER 识别、关系图谱查询、报告生成、知识库检索等。</p>
 *
 * <p>工具实现需保证幂等与降级：外部服务不可用时返回降级文本，不抛出异常。</p>
 *
 * @author 红方团队
 */
public interface AgentTool {

    /**
     * 工具名称（唯一标识，用于 LLM 选择工具）
     *
     * @return 工具名称
     */
    String getName();

    /**
     * 工具描述（供 LLM 理解工具用途）
     *
     * @return 工具描述
     */
    String getDescription();

    /**
     * 工具参数 JSON Schema（供 LLM 构造调用参数）
     *
     * @return 参数 schema JSON 字符串
     */
    String getParametersSchema();

    /**
     * 执行工具
     *
     * @param params 工具参数（来自 LLM 解析的 JSON 对象）
     * @return 执行结果字符串（供 Agent 观察）
     */
    String execute(Map<String, Object> params);

    /**
     * 工具所需权限标识（空字符串表示无需权限校验）
     *
     * @return 权限标识
     */
    default String getRequiredPermission() {
        return "";
    }
}
