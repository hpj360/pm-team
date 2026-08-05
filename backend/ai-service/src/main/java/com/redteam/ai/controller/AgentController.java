package com.redteam.ai.controller;

import com.redteam.ai.agent.AgentTrace;
import com.redteam.ai.agent.AutonomousAnalysisService;
import com.redteam.ai.agent.RagService;
import com.redteam.ai.dto.AgentAnalysisRequest;
import com.redteam.ai.dto.KnowledgeIndexRequest;
import com.redteam.ai.entity.AgentTaskEntity;
import com.redteam.ai.entity.KnowledgeEntity;
import com.redteam.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI Agent 控制器
 *
 * <p>提供 Agent 自主分析任务管理与知识库索引/检索接口。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Agent 自主分析", description = "V5.1 Agent 化模块：自主分析、推理轨迹与知识库管理")
public class AgentController {

    @Autowired
    private AutonomousAnalysisService autonomousAnalysisService;

    @Autowired
    private RagService ragService;

    /**
     * 提交自主分析任务
     *
     * @param request 分析请求（含 query 与 userId）
     * @return 任务ID
     */
    @PostMapping("/agent/analyze")
    @Operation(summary = "提交自主分析任务", description = "用户提交自然语言分析请求，Agent 异步执行检索与推理")
    public Result<String> submitAnalysis(@RequestBody AgentAnalysisRequest request) {
        log.info("提交 Agent 分析任务, userId={}, query={}", request.getUserId(), request.getQuery());
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            return Result.fail(400, "分析请求不能为空");
        }
        String taskId = autonomousAnalysisService.submitAnalysis(request.getQuery(), request.getUserId());
        return Result.success(taskId);
    }

    /**
     * 查询任务状态与结果
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    @GetMapping("/agent/tasks/{taskId}")
    @Operation(summary = "查询任务状态与结果", description = "按任务ID查询 Agent 分析任务的状态、结论与证据链")
    public Result<AgentTaskEntity> getTask(
            @Parameter(description = "任务ID", required = true) @PathVariable String taskId) {
        AgentTaskEntity entity = autonomousAnalysisService.getTask(taskId);
        if (entity == null) {
            return Result.fail(404, "任务不存在");
        }
        return Result.success(entity);
    }

    /**
     * 查询任务列表
     *
     * @param userId 用户ID（可选）
     * @param limit  返回条数上限（默认 20）
     * @return 任务列表
     */
    @GetMapping("/agent/tasks")
    @Operation(summary = "查询任务列表", description = "按用户ID查询 Agent 分析任务列表")
    public Result<List<AgentTaskEntity>> listTasks(
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "返回条数上限") @RequestParam(defaultValue = "20") int limit) {
        List<AgentTaskEntity> tasks = autonomousAnalysisService.listTasks(userId, limit);
        return Result.success(tasks);
    }

    /**
     * 查询推理轨迹
     *
     * @param taskId 任务ID
     * @return 推理轨迹列表
     */
    @GetMapping("/agent/traces/{taskId}")
    @Operation(summary = "查询推理轨迹", description = "按任务ID查询 Agent 的 ReAct 推理轨迹")
    public Result<List<AgentTrace>> getTraces(
            @Parameter(description = "任务ID", required = true) @PathVariable String taskId) {
        List<AgentTrace> traces = autonomousAnalysisService.getTraces(taskId);
        return Result.success(traces);
    }

    /**
     * 索引知识库文档
     *
     * @param request 索引请求
     * @return 知识ID
     */
    @PostMapping("/knowledge")
    @Operation(summary = "索引知识库文档", description = "将文档写入知识库并进行向量索引")
    public Result<String> indexKnowledge(@RequestBody KnowledgeIndexRequest request) {
        log.info("索引知识库文档, title={}, source={}", request.getTitle(), request.getSource());
        if (request.getContent() == null || request.getContent().isBlank()) {
            return Result.fail(400, "文档内容不能为空");
        }
        String knowledgeId = ragService.indexDocument(null, request.getContent(), request.getMetadata());
        return Result.success(knowledgeId);
    }

    /**
     * 知识库检索测试
     *
     * @param query 检索查询
     * @param topK  返回条数
     * @return 匹配的知识片段列表
     */
    @GetMapping("/knowledge/search")
    @Operation(summary = "知识库检索测试", description = "输入查询语句测试知识库语义检索")
    public Result<List<Map<String, Object>>> searchKnowledge(
            @Parameter(description = "检索查询") @RequestParam String query,
            @Parameter(description = "返回条数") @RequestParam(defaultValue = "5") int topK) {
        List<Map<String, Object>> results = ragService.search(query, topK);
        return Result.success(results);
    }

    /**
     * 查询全部知识库文档
     *
     * @return 知识库文档列表
     */
    @GetMapping("/knowledge")
    @Operation(summary = "查询知识库文档列表", description = "返回全部知识库文档元信息")
    public Result<List<KnowledgeEntity>> listKnowledge() {
        List<KnowledgeEntity> list = ragService.listAll();
        return Result.success(list);
    }
}
