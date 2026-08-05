package com.redteam.ai.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行结果
 *
 * <p>封装 AgentExecutor 的完整输出，包含最终结论、证据链、引用文件、置信度与推理轨迹。</p>
 *
 * @author 红方团队
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 最终结论
     */
    private String conclusion;

    /**
     * 证据链（每条证据来自工具观察结果）
     */
    @Builder.Default
    private List<String> evidenceChain = new ArrayList<>();

    /**
     * 引用文件列表（工具检索到的文件 ID）
     */
    @Builder.Default
    private List<String> referencedFiles = new ArrayList<>();

    /**
     * 置信度（0.0 ~ 1.0）
     */
    private double confidence;

    /**
     * 推理轨迹列表
     */
    @Builder.Default
    private List<AgentTrace> traces = new ArrayList<>();

    /**
     * 是否降级（LLM 不可用时为 true）
     */
    private boolean degraded;

    /**
     * 降级/错误信息
     */
    private String errorMessage;
}
