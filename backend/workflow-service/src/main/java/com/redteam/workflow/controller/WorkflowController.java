package com.redteam.workflow.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.entity.WorkflowDefinitionEntity;
import com.redteam.common.entity.WorkflowInstanceEntity;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.WorkflowDefinitionMapper;
import com.redteam.common.mapper.WorkflowInstanceMapper;
import com.redteam.common.result.Result;
import com.redteam.common.util.UserContext;
import com.redteam.workflow.dto.ReviewDecisionDTO;
import com.redteam.workflow.dto.SubmitReviewDTO;
import com.redteam.workflow.dto.WorkflowDefinitionDTO;
import com.redteam.workflow.dto.WorkflowInstanceVO;
import com.redteam.workflow.service.WorkflowEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 审批工作流管理 REST 接口
 *
 * <p>提供工作流定义 CRUD、实例启动、实例查询、审批决定等接口，
 * 统一前缀 {@code /api/workflow}。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
@Tag(name = "工作流管理", description = "工作流定义/实例/审批意见管理接口")
public class WorkflowController {

    private final WorkflowEngine workflowEngine;
    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final ObjectMapper objectMapper;

    // ===================== 工作流定义 =====================

    /**
     * 创建工作流定义
     *
     * @param dto 工作流定义请求
     * @return 已创建的工作流定义
     */
    @PostMapping("/definitions")
    @Operation(summary = "创建工作流定义")
    public Result<WorkflowDefinitionEntity> createDefinition(@RequestBody WorkflowDefinitionDTO dto) {
        log.info("创建工作流定义: name={}, businessType={}", dto.getName(), dto.getBusinessType());

        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setBusinessType(dto.getBusinessType());
        entity.setNodesJson(serializeJson(dto.getNodes(), "nodes"));
        entity.setEdgesJson(serializeJson(dto.getEdges(), "edges"));
        entity.setEnabled(1);
        entity.setVersion(1);

        Long userId = UserContext.getUserId();
        if (userId != null) {
            entity.setCreatedBy(userId);
            entity.setCreatedByName(UserContext.getUsername());
        }

        definitionMapper.insert(entity);
        return Result.success(entity);
    }

    /**
     * 工作流定义列表
     *
     * @param businessType 业务类型（可选）
     * @return 工作流定义列表
     */
    @GetMapping("/definitions")
    @Operation(summary = "工作流定义列表")
    public Result<List<WorkflowDefinitionEntity>> listDefinitions(
            @Parameter(description = "业务类型")
            @RequestParam(required = false) String businessType) {
        List<WorkflowDefinitionEntity> list;
        if (businessType != null && !businessType.isEmpty()) {
            list = definitionMapper.selectByBusinessType(businessType);
        } else {
            list = definitionMapper.selectList(null);
        }
        return Result.success(list);
    }

    /**
     * 更新工作流定义
     *
     * @param id  工作流定义ID
     * @param dto 工作流定义请求
     * @return 更新后的工作流定义
     */
    @PutMapping("/definitions/{id}")
    @Operation(summary = "更新工作流定义")
    public Result<WorkflowDefinitionEntity> updateDefinition(
            @Parameter(description = "工作流定义ID", required = true) @PathVariable Long id,
            @RequestBody WorkflowDefinitionDTO dto) {
        log.info("更新工作流定义: id={}", id);

        WorkflowDefinitionEntity entity = definitionMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of("工作流定义不存在: " + id);
        }

        entity.setName(dto.getName());
        entity.setDescription(dto.getDescription());
        entity.setBusinessType(dto.getBusinessType());
        entity.setNodesJson(serializeJson(dto.getNodes(), "nodes"));
        entity.setEdgesJson(serializeJson(dto.getEdges(), "edges"));
        // 版本号递增
        Integer currentVersion = entity.getVersion();
        entity.setVersion(currentVersion == null ? 1 : currentVersion + 1);

        definitionMapper.updateById(entity);
        return Result.success(entity);
    }

    /**
     * 删除工作流定义
     *
     * @param id 工作流定义ID
     * @return 操作结果
     */
    @DeleteMapping("/definitions/{id}")
    @Operation(summary = "删除工作流定义")
    public Result<Void> deleteDefinition(
            @Parameter(description = "工作流定义ID", required = true) @PathVariable Long id) {
        log.info("删除工作流定义: id={}", id);
        definitionMapper.deleteById(id);
        return Result.success();
    }

    // ===================== 工作流实例 =====================

    /**
     * 启动工作流实例
     *
     * @param dto 启动请求
     * @return 已创建的实例实体
     */
    @PostMapping("/instances")
    @Operation(summary = "启动工作流实例")
    public Result<WorkflowInstanceEntity> startInstance(@RequestBody SubmitReviewDTO dto) {
        log.info("启动工作流实例: workflowId={}, businessId={}", dto.getWorkflowId(), dto.getBusinessId());

        Long submitterId = UserContext.getUserId();
        String submitterName = UserContext.getUsername();

        WorkflowInstanceEntity instance = workflowEngine.startInstance(
                dto.getWorkflowId(), dto.getBusinessId(), dto.getBusinessType(),
                submitterId, submitterName);
        return Result.success(instance);
    }

    /**
     * 实例详情
     *
     * @param id 实例ID
     * @return 实例详情 VO（含审批记录）
     */
    @GetMapping("/instances/{id}")
    @Operation(summary = "实例详情")
    public Result<WorkflowInstanceVO> getInstance(
            @Parameter(description = "实例ID", required = true) @PathVariable Long id) {
        log.info("获取实例详情: id={}", id);
        WorkflowInstanceVO vo = workflowEngine.getInstanceDetail(id);
        return Result.success(vo);
    }

    /**
     * 待审批列表
     *
     * @param reviewerId 审批人ID
     * @return 待审批实例列表
     */
    @GetMapping("/instances/pending")
    @Operation(summary = "待审批列表")
    public Result<List<WorkflowInstanceVO>> getPendingInstances(
            @Parameter(description = "审批人ID", required = true) @RequestParam Long reviewerId) {
        log.info("查询待审批列表: reviewerId={}", reviewerId);
        List<WorkflowInstanceVO> list = workflowEngine.getPendingInstances(reviewerId);
        return Result.success(list);
    }

    /**
     * 审批决定
     *
     * @param id  实例ID
     * @param dto 审批决定请求
     * @return 更新后的实例实体
     */
    @PostMapping("/instances/{id}/decide")
    @Operation(summary = "审批决定")
    public Result<WorkflowInstanceEntity> decide(
            @Parameter(description = "实例ID", required = true) @PathVariable Long id,
            @RequestBody ReviewDecisionDTO dto) {
        log.info("审批决定: instanceId={}, decision={}", id, dto.getDecision());

        Long reviewerId = UserContext.getUserId();
        String reviewerName = UserContext.getUsername();

        // 路径参数优先：用 path 的 id 覆盖 dto 中的 instanceId
        dto.setInstanceId(id);
        WorkflowInstanceEntity instance = workflowEngine.processDecision(
                id, reviewerId, reviewerName, dto.getDecision(), dto.getComment());
        return Result.success(instance);
    }

    /**
     * 实例列表
     *
     * @param businessType 业务类型（可选）
     * @param status       状态（可选）
     * @param submitterId 提交人ID（可选）
     * @return 实例列表
     */
    @GetMapping("/instances")
    @Operation(summary = "实例列表")
    public Result<List<WorkflowInstanceVO>> listInstances(
            @Parameter(description = "业务类型") @RequestParam(required = false) String businessType,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "提交人ID") @RequestParam(required = false) Long submitterId) {
        log.info("查询实例列表: businessType={}, status={}, submitterId={}", businessType, status, submitterId);

        List<WorkflowInstanceEntity> entities;
        if (submitterId != null) {
            entities = instanceMapper.selectBySubmitter(submitterId);
        } else if (status != null && !status.isEmpty()) {
            entities = instanceMapper.selectByStatus(status);
        } else {
            entities = instanceMapper.selectList(null);
        }

        List<WorkflowInstanceVO> result = new ArrayList<>();
        for (WorkflowInstanceEntity entity : entities) {
            if (businessType != null && !businessType.isEmpty()
                    && !businessType.equals(entity.getBusinessType())) {
                continue;
            }
            WorkflowInstanceVO vo = new WorkflowInstanceVO();
            vo.setId(entity.getId());
            vo.setWorkflowName(entity.getWorkflowName());
            vo.setBusinessId(entity.getBusinessId());
            vo.setBusinessType(entity.getBusinessType());
            vo.setSubmitterName(entity.getSubmitterName());
            vo.setStatus(entity.getStatus());
            vo.setCurrentNodeName(entity.getCurrentNodeName());
            vo.setCreatedAt(entity.getCreatedAt());
            vo.setReviews(new ArrayList<>());
            result.add(vo);
        }
        return Result.success(result);
    }

    // ===================== 私有方法 =====================

    /**
     * 序列化对象为 JSON 字符串
     *
     * @param obj       对象
     * @param fieldName 字段名（用于异常提示）
     * @return JSON 字符串
     */
    private String serializeJson(Object obj, String fieldName) {
        if (obj == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("序列化 {} 失败", fieldName, e);
            throw BusinessException.of(fieldName + " 序列化失败: " + e.getMessage());
        }
    }
}
