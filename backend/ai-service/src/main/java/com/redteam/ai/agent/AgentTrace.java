package com.redteam.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Agent 推理轨迹单步记录
 *
 * <p>对应 ReAct 模式的 Thought → Action → Observation 一个完整循环。</p>
 *
 * @author 红方团队
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentTrace implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 步骤序号（从 1 开始）
     */
    private int step;

    /**
     * 思考过程（LLM 的 Thought）
     */
    private String thought;

    /**
     * 动作（工具名称，如 search_files；若为最终结论则为 FINAL_ANSWER）
     */
    private String action;

    /**
     * 动作输入（工具参数 JSON 字符串）
     */
    private String actionInput;

    /**
     * 观察结果（工具执行返回的内容）
     */
    private String observation;
}
