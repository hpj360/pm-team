package com.redteam.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.entity.WorkflowDefinitionEntity;
import com.redteam.common.entity.WorkflowInstanceEntity;
import com.redteam.common.entity.WorkflowReviewEntity;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.WorkflowDefinitionMapper;
import com.redteam.common.mapper.WorkflowInstanceMapper;
import com.redteam.common.mapper.WorkflowReviewMapper;
import com.redteam.workflow.dto.WorkflowEdgeDTO;
import com.redteam.workflow.dto.WorkflowInstanceVO;
import com.redteam.workflow.dto.WorkflowNodeDTO;
import com.redteam.workflow.dto.WorkflowReviewVO;
import com.redteam.workflow.event.ApprovalEventType;
import com.redteam.workflow.producer.WorkflowApprovalEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审批工作流引擎
 *
 * <p>核心引擎实现，负责：</p>
 * <ul>
 *   <li>启动工作流实例（解析定义 JSON → 找到首个审批节点 → 落库实例）</li>
 *   <li>处理审批决定（按节点审批模式推进流转）</li>
 *   <li>查询实例详情（聚合审批记录）</li>
 *   <li>查询待审批列表（按审批人过滤 PENDING 实例）</li>
 * </ul>
 *
 * <p>支持的审批模式：</p>
 * <ul>
 *   <li>{@code SEQUENTIAL}（线性）：节点内审批人按 {@code approverIds} 顺序逐人通过，
 *       {@code currentApprovers} 始终只含下一个待审批人 ID</li>
 *   <li>{@code PARALLEL_ALL}（会签）：节点内所有审批人都通过后才进入下一节点，
 *       {@code currentApprovers} 含全部审批人，每人通过后从列表移除</li>
 *   <li>{@code PARALLEL_ANY}（或签）：节点内任一审批人通过即进入下一节点</li>
 * </ul>
 *
 * <p>{@code nodes_json}/{@code edges_json} 通过注入的 {@link ObjectMapper} 序列化/反序列化。</p>
 *
 * @author 红方团队
 */
@Service
@Slf4j
public class WorkflowEngine {

    /**
     * 节点类型常量
     */
    private static final String NODE_TYPE_START = "START";
    private static final String NODE_TYPE_APPROVAL = "APPROVAL";
    private static final String NODE_TYPE_END = "END";

    /**
     * 审批模式常量
     */
    private static final String MODE_SEQUENTIAL = "SEQUENTIAL";
    private static final String MODE_PARALLEL_ALL = "PARALLEL_ALL";
    private static final String MODE_PARALLEL_ANY = "PARALLEL_ANY";

    /**
     * 实例状态常量
     */
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    /**
     * 决定常量
     */
    private static final String DECISION_APPROVE = "APPROVE";
    private static final String DECISION_REJECT = "REJECT";

    @Autowired
    private WorkflowDefinitionMapper definitionMapper;

    @Autowired
    private WorkflowInstanceMapper instanceMapper;

    @Autowired
    private WorkflowReviewMapper reviewMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 审批事件生产者（V4.7-P0-4：发布审批事件到 notification-service）
     */
    @Autowired
    private WorkflowApprovalEventProducer approvalEventProducer;

    // ===================== 公开 API =====================

    /**
     * 启动工作流实例
     *
     * <p>根据 {@code workflowId} 加载工作流定义，解析节点/边 JSON，
     * 找到首个审批节点（从 START 节点出发按边追溯），落库一条 PENDING 实例。</p>
     *
     * @param workflowId    工作流定义ID
     * @param businessId    业务ID（如文件ID/任务ID）
     * @param businessType  业务类型
     * @param submitterId   提交人ID
     * @param submitterName 提交人姓名
     * @return 已创建的实例实体（含自增主键 ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowInstanceEntity startInstance(Long workflowId, String businessId,
                                                  String businessType, Long submitterId, String submitterName) {
        log.info("启动工作流实例: workflowId={}, businessId={}, businessType={}, submitterId={}",
                workflowId, businessId, businessType, submitterId);

        WorkflowDefinitionEntity definition = definitionMapper.selectById(workflowId);
        if (definition == null) {
            throw BusinessException.of("工作流定义不存在: " + workflowId);
        }
        if (definition.getEnabled() == null || definition.getEnabled() != 1) {
            throw BusinessException.of("工作流定义未启用: " + workflowId);
        }

        List<WorkflowNodeDTO> nodes = parseNodes(definition.getNodesJson());
        if (nodes.isEmpty()) {
            throw BusinessException.of("工作流定义节点为空: " + workflowId);
        }

        // 找到首个审批节点
        WorkflowNodeDTO startNode = findFirstNode(nodes, definition.getEdgesJson());
        if (startNode == null) {
            throw BusinessException.of("工作流定义缺少审批节点: " + workflowId);
        }

        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setWorkflowId(workflowId);
        instance.setWorkflowName(definition.getName());
        instance.setBusinessId(businessId);
        instance.setBusinessType(businessType);
        instance.setSubmitterId(submitterId);
        instance.setSubmitterName(submitterName);
        instance.setStatus(STATUS_PENDING);
        instance.setCurrentNodeId(startNode.getNodeId());
        instance.setCurrentNodeName(startNode.getNodeName());
        instance.setCurrentApprovers(initCurrentApprovers(startNode));

        instanceMapper.insert(instance);
        log.info("工作流实例已创建: instanceId={}, currentNodeId={}", instance.getId(), instance.getCurrentNodeId());
        return instance;
    }

    /**
     * 处理审批决定
     *
     * <p>校验审批人是否在当前节点的审批人列表中，落库审批意见，
     * 然后按节点审批模式推进实例流转。</p>
     *
     * @param instanceId    实例ID
     * @param reviewerId    审批人ID
     * @param reviewerName  审批人姓名
     * @param decision      决定：APPROVE/REJECT
     * @param comment       审批意见
     * @return 更新后的实例实体
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowInstanceEntity processDecision(Long instanceId, Long reviewerId,
                                                    String reviewerName, String decision, String comment) {
        log.info("处理审批决定: instanceId={}, reviewerId={}, decision={}", instanceId, reviewerId, decision);

        WorkflowInstanceEntity instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw BusinessException.of("审批实例不存在: " + instanceId);
        }
        if (!STATUS_PENDING.equals(instance.getStatus())) {
            throw BusinessException.of("审批实例状态非 PENDING，无法继续审批: " + instance.getStatus());
        }

        // 校验审批人
        List<Long> currentApprovers = parseApproverIds(instance.getCurrentApprovers());
        if (!currentApprovers.contains(reviewerId)) {
            throw BusinessException.of("当前用户不在审批人列表中: " + reviewerId);
        }

        // 落库审批意见
        WorkflowReviewEntity review = new WorkflowReviewEntity();
        review.setInstanceId(instanceId);
        review.setNodeId(instance.getCurrentNodeId());
        review.setReviewerId(reviewerId);
        review.setReviewerName(reviewerName);
        review.setDecision(decision);
        review.setComment(comment);
        reviewMapper.insert(review);

        // 处理决定
        if (DECISION_REJECT.equals(decision)) {
            rejectInstance(instance, comment);
        } else if (DECISION_APPROVE.equals(decision)) {
            WorkflowDefinitionEntity definition = definitionMapper.selectById(instance.getWorkflowId());
            WorkflowNodeDTO currentNode = findNodeById(parseNodes(definition.getNodesJson()),
                    instance.getCurrentNodeId());
            handleApprove(instance, definition, reviewerId, currentNode);
        } else {
            throw BusinessException.of("不支持的审批决定: " + decision);
        }

        instanceMapper.updateById(instance);

        // V4.7-P0-4：发布审批事件到 notification-service（发送失败仅记日志，不阻塞主流程）
        publishApprovalEvent(instance, decision, reviewerName, comment);

        return instance;
    }

    /**
     * 查询实例详情（含审批记录）
     *
     * @param instanceId 实例ID
     * @return 实例详情 VO
     */
    public WorkflowInstanceVO getInstanceDetail(Long instanceId) {
        WorkflowInstanceEntity instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw BusinessException.of("审批实例不存在: " + instanceId);
        }

        List<WorkflowReviewEntity> reviews = reviewMapper.selectByInstanceId(instanceId);

        WorkflowInstanceVO vo = new WorkflowInstanceVO();
        vo.setId(instance.getId());
        vo.setWorkflowName(instance.getWorkflowName());
        vo.setBusinessId(instance.getBusinessId());
        vo.setBusinessType(instance.getBusinessType());
        vo.setSubmitterName(instance.getSubmitterName());
        vo.setStatus(instance.getStatus());
        vo.setCurrentNodeName(instance.getCurrentNodeName());
        vo.setCreatedAt(instance.getCreatedAt());

        List<WorkflowReviewVO> reviewVOs = reviews.stream().map(this::toReviewVO).collect(Collectors.toList());
        vo.setReviews(reviewVOs);
        return vo;
    }

    /**
     * 查询待审批列表（按审批人）
     *
     * <p>查询所有 PENDING 实例，过滤出 {@code currentApprovers} 包含 {@code reviewerId} 的实例。</p>
     *
     * @param reviewerId 审批人ID
     * @return 待审批实例列表
     */
    public List<WorkflowInstanceVO> getPendingInstances(Long reviewerId) {
        List<WorkflowInstanceEntity> pending = instanceMapper.selectByStatus(STATUS_PENDING);
        if (pending == null || pending.isEmpty()) {
            return Collections.emptyList();
        }

        List<WorkflowInstanceVO> result = new ArrayList<>();
        for (WorkflowInstanceEntity instance : pending) {
            List<Long> approvers = parseApproverIds(instance.getCurrentApprovers());
            if (approvers.contains(reviewerId)) {
                WorkflowInstanceVO vo = new WorkflowInstanceVO();
                vo.setId(instance.getId());
                vo.setWorkflowName(instance.getWorkflowName());
                vo.setBusinessId(instance.getBusinessId());
                vo.setBusinessType(instance.getBusinessType());
                vo.setSubmitterName(instance.getSubmitterName());
                vo.setStatus(instance.getStatus());
                vo.setCurrentNodeName(instance.getCurrentNodeName());
                vo.setCreatedAt(instance.getCreatedAt());
                vo.setReviews(Collections.emptyList());
                result.add(vo);
            }
        }
        return result;
    }

    // ===================== 内部方法 =====================

    /**
     * 发布审批事件（V4.7-P0-4）
     *
     * <p>根据审批决定与实例最终状态，向 {@code workflow.approval} 主题投递对应类型事件：</p>
     * <ul>
     *   <li>REJECT 决定 → {@link ApprovalEventType#REJECT}</li>
     *   <li>APPROVE 决定且实例状态为 APPROVED → {@link ApprovalEventType#COMPLETE}（全部通过）</li>
     *   <li>APPROVE 决定且实例状态仍为 PENDING → {@link ApprovalEventType#APPROVE}（节点流转）</li>
     * </ul>
     *
     * <p>事件发布失败仅记录日志，不影响审批主流程。</p>
     *
     * @param instance 审批实例
     * @param decision 审批决定（APPROVE/REJECT）
     * @param operator 操作人姓名
     * @param comment  审批意见
     */
    private void publishApprovalEvent(WorkflowInstanceEntity instance, String decision,
                                        String operator, String comment) {
        try {
            if (DECISION_REJECT.equals(decision)) {
                approvalEventProducer.sendApprovalEvent(instance, ApprovalEventType.REJECT, operator, comment);
            } else if (DECISION_APPROVE.equals(decision)) {
                if (STATUS_APPROVED.equals(instance.getStatus())) {
                    // 所有节点通过，流程完成
                    approvalEventProducer.sendApprovalEvent(instance, ApprovalEventType.COMPLETE, operator, comment);
                } else {
                    // 当前节点通过，流转至下一节点
                    approvalEventProducer.sendApprovalEvent(instance, ApprovalEventType.APPROVE, operator, comment);
                }
            }
        } catch (Exception e) {
            // 事件发布失败不影响审批主流程
            log.warn("审批事件发布失败: instanceId={}, decision={}", instance.getId(), decision, e);
        }
    }

    /**
     * 推进到下一节点
     *
     * <p>通过解析边定义找到下一审批节点；找不到时调用 {@link #completeInstance}。</p>
     *
     * @param instance    实例
     * @param definition  工作流定义
     */
    private void advanceToNextNode(WorkflowInstanceEntity instance, WorkflowDefinitionEntity definition) {
        List<WorkflowNodeDTO> nodes = parseNodes(definition.getNodesJson());
        WorkflowNodeDTO nextNode = findNextApprovalNode(nodes, definition.getEdgesJson(),
                instance.getCurrentNodeId());

        if (nextNode == null) {
            completeInstance(instance);
        } else {
            instance.setCurrentNodeId(nextNode.getNodeId());
            instance.setCurrentNodeName(nextNode.getNodeName());
            instance.setCurrentApprovers(initCurrentApprovers(nextNode));
        }
    }

    /**
     * 完成实例（全部通过）
     *
     * @param instance 实例
     */
    private void completeInstance(WorkflowInstanceEntity instance) {
        instance.setStatus(STATUS_APPROVED);
        instance.setCurrentNodeId(null);
        instance.setCurrentNodeName(null);
        instance.setCurrentApprovers(null);
        log.info("审批实例已通过: instanceId={}", instance.getId());
    }

    /**
     * 驳回实例
     *
     * @param instance 实例
     * @param comment  驳回意见
     */
    private void rejectInstance(WorkflowInstanceEntity instance, String comment) {
        instance.setStatus(STATUS_REJECTED);
        instance.setCurrentNodeId(null);
        instance.setCurrentNodeName(null);
        instance.setCurrentApprovers(null);
        log.info("审批实例已驳回: instanceId={}, comment={}", instance.getId(), comment);
    }

    /**
     * 处理 APPROVE 决定：按节点审批模式推进
     *
     * @param instance    实例
     * @param definition  工作流定义
     * @param reviewerId  审批人ID
     * @param currentNode 当前节点
     */
    private void handleApprove(WorkflowInstanceEntity instance, WorkflowDefinitionEntity definition,
                                Long reviewerId, WorkflowNodeDTO currentNode) {
        if (currentNode == null) {
            log.warn("当前节点为空，直接完成: instanceId={}", instance.getId());
            completeInstance(instance);
            return;
        }

        String mode = currentNode.getApprovalMode();
        List<Long> approverIds = currentNode.getApproverIds();
        if (approverIds == null || approverIds.isEmpty()) {
            // 节点无审批人，直接推进
            advanceToNextNode(instance, definition);
            return;
        }

        if (MODE_PARALLEL_ANY.equals(mode)) {
            // 或签：任一通过即进入下一节点
            advanceToNextNode(instance, definition);
        } else if (MODE_PARALLEL_ALL.equals(mode)) {
            // 会签：移除当前审批人，全部通过后才进入下一节点
            List<Long> remaining = parseApproverIds(instance.getCurrentApprovers());
            remaining.remove(reviewerId);
            if (remaining.isEmpty()) {
                advanceToNextNode(instance, definition);
            } else {
                instance.setCurrentApprovers(approverIdsToString(remaining));
            }
        } else {
            // SEQUENTIAL（默认）：按顺序逐人审批
            int idx = approverIds.indexOf(reviewerId);
            if (idx >= 0 && idx + 1 < approverIds.size()) {
                // 还有下一个审批人
                instance.setCurrentApprovers(String.valueOf(approverIds.get(idx + 1)));
            } else {
                // 当前是最后一个审批人 → 进入下一节点
                advanceToNextNode(instance, definition);
            }
        }
    }

    /**
     * 初始化节点的 currentApprovers
     *
     * <p>SEQUENTIAL 模式仅放首个审批人；PARALLEL_ALL/PARALLEL_ANY 放全部审批人。</p>
     *
     * @param node 节点
     * @return 审批人 ID 字符串（逗号分隔或单个）
     */
    private String initCurrentApprovers(WorkflowNodeDTO node) {
        List<Long> approverIds = node.getApproverIds();
        if (approverIds == null || approverIds.isEmpty()) {
            return null;
        }
        if (MODE_SEQUENTIAL.equals(node.getApprovalMode())) {
            return String.valueOf(approverIds.get(0));
        }
        return approverIdsToString(approverIds);
    }

    /**
     * 找到首个审批节点
     *
     * <p>优先使用 START 节点 + 边定义追溯；找不到 START 时回退为节点列表中首个 APPROVAL 节点。</p>
     *
     * @param nodes     节点列表
     * @param edgesJson 边 JSON
     * @return 首个审批节点；不存在返回 null
     */
    private WorkflowNodeDTO findFirstNode(List<WorkflowNodeDTO> nodes, String edgesJson) {
        WorkflowNodeDTO startNode = nodes.stream()
                .filter(n -> NODE_TYPE_START.equals(n.getNodeType()))
                .findFirst()
                .orElse(null);

        if (startNode != null) {
            WorkflowNodeDTO next = findNextApprovalNode(nodes, edgesJson, startNode.getNodeId());
            if (next != null) {
                return next;
            }
        }

        // 回退：节点列表中首个 APPROVAL 节点
        return nodes.stream()
                .filter(n -> NODE_TYPE_APPROVAL.equals(n.getNodeType()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 通过边定义追溯下一审批节点
     *
     * <p>找不到匹配边或目标非 APPROVAL 时回退为节点列表中当前节点之后的下一个 APPROVAL 节点。</p>
     *
     * @param nodes         节点列表
     * @param edgesJson     边 JSON
     * @param currentNodeId 当前节点ID
     * @return 下一审批节点；不存在返回 null
     */
    private WorkflowNodeDTO findNextApprovalNode(List<WorkflowNodeDTO> nodes, String edgesJson,
                                                   String currentNodeId) {
        List<WorkflowEdgeDTO> edges = parseEdges(edgesJson);
        for (WorkflowEdgeDTO edge : edges) {
            if (currentNodeId.equals(edge.getSourceNodeId())) {
                WorkflowNodeDTO target = findNodeById(nodes, edge.getTargetNodeId());
                if (target != null && NODE_TYPE_APPROVAL.equals(target.getNodeType())) {
                    return target;
                }
            }
        }

        // 回退：节点列表中当前节点之后的下一个 APPROVAL 节点
        int currentIdx = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (currentNodeId.equals(nodes.get(i).getNodeId())) {
                currentIdx = i;
                break;
            }
        }
        if (currentIdx >= 0) {
            for (int i = currentIdx + 1; i < nodes.size(); i++) {
                if (NODE_TYPE_APPROVAL.equals(nodes.get(i).getNodeType())) {
                    return nodes.get(i);
                }
            }
        }
        return null;
    }

    /**
     * 通过节点ID查找节点
     *
     * @param nodes  节点列表
     * @param nodeId 节点ID
     * @return 节点；不存在返回 null
     */
    private WorkflowNodeDTO findNodeById(List<WorkflowNodeDTO> nodes, String nodeId) {
        return nodes.stream()
                .filter(n -> nodeId.equals(n.getNodeId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析节点 JSON
     *
     * @param json 节点 JSON 字符串
     * @return 节点列表；解析失败返回空列表
     */
    private List<WorkflowNodeDTO> parseNodes(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkflowNodeDTO>>() {});
        } catch (Exception e) {
            log.error("解析节点 JSON 失败: {}", json, e);
            throw BusinessException.of("工作流节点 JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析边 JSON
     *
     * @param json 边 JSON 字符串
     * @return 边列表；解析失败返回空列表
     */
    private List<WorkflowEdgeDTO> parseEdges(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<WorkflowEdgeDTO>>() {});
        } catch (Exception e) {
            log.error("解析边 JSON 失败: {}", json, e);
            throw BusinessException.of("工作流边 JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析审批人 ID 字符串（逗号分隔）
     *
     * @param str 审批人 ID 字符串
     * @return 审批人 ID 列表
     */
    private List<Long> parseApproverIds(String str) {
        if (str == null || str.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * 审批人 ID 列表转字符串（逗号分隔）
     *
     * @param ids 审批人 ID 列表
     * @return 审批人 ID 字符串
     */
    private String approverIdsToString(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * 实体转 VO
     *
     * @param review 审批记录实体
     * @return 审批记录 VO
     */
    private WorkflowReviewVO toReviewVO(WorkflowReviewEntity review) {
        WorkflowReviewVO vo = new WorkflowReviewVO();
        vo.setNodeId(review.getNodeId());
        vo.setReviewerId(review.getReviewerId());
        vo.setReviewerName(review.getReviewerName());
        vo.setDecision(review.getDecision());
        vo.setComment(review.getComment());
        vo.setCreatedAt(review.getCreatedAt());
        return vo;
    }
}
