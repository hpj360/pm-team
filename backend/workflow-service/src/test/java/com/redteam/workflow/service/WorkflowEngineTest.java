package com.redteam.workflow.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import com.redteam.workflow.producer.WorkflowApprovalEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowEngine} 单元测试
 *
 * <p>覆盖 SEQUENTIAL/PARALLEL_ALL/PARALLEL_ANY 三种审批模式的核心路径，
 * 使用 Mockito 隔离 Mapper，使用真实 Jackson {@link ObjectMapper} 序列化节点/边 JSON。</p>
 *
 * <p>测试覆盖率目标 ≥ 80%。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowEngineTest {

    @Mock
    private WorkflowDefinitionMapper definitionMapper;

    @Mock
    private WorkflowInstanceMapper instanceMapper;

    @Mock
    private WorkflowReviewMapper reviewMapper;

    @Mock
    private WorkflowApprovalEventProducer approvalEventProducer;

    @InjectMocks
    private WorkflowEngine workflowEngine;

    /**
     * 真实 ObjectMapper（用于在测试中序列化节点/边 JSON 与引擎内部解析保持一致）
     */
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.registerModule(new JavaTimeModule());
        // 引擎依赖注入的 ObjectMapper（@Autowired 字段不会被 @InjectMocks 注入）
        ReflectionTestUtils.setField(workflowEngine, "objectMapper", objectMapper);
    }

    // ===================== testStartInstance =====================

    /**
     * 测试启动工作流实例：应入库 PENDING 实例，且当前节点为首个审批节点
     */
    @Test
    @DisplayName("启动工作流实例 - 应入库 PENDING 实例并定位首个审批节点")
    void testStartInstance() {
        WorkflowDefinitionEntity definition = buildDefinition(
                "start", "approval1", "end", "SEQUENTIAL", Arrays.asList(1001L));
        when(definitionMapper.selectById(1L)).thenReturn(definition);
        when(instanceMapper.insert(any(WorkflowInstanceEntity.class))).thenAnswer(invocation -> {
            WorkflowInstanceEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        });

        WorkflowInstanceEntity instance = workflowEngine.startInstance(
                1L, "biz-001", "FILE_REVIEW", 2001L, "alice");

        assertNotNull(instance);
        assertEquals(100L, instance.getId());
        assertEquals("PENDING", instance.getStatus());
        assertEquals("biz-001", instance.getBusinessId());
        assertEquals("FILE_REVIEW", instance.getBusinessType());
        assertEquals("alice", instance.getSubmitterName());
        assertEquals("approval1", instance.getCurrentNodeId());
        assertEquals("审批节点1", instance.getCurrentNodeName());
        // SEQUENTIAL 模式初始 currentApprovers 为首个审批人
        assertEquals("1001", instance.getCurrentApprovers());
    }

    /**
     * 测试启动工作流实例 - 定义不存在时应抛出异常
     */
    @Test
    @DisplayName("启动工作流实例 - 工作流定义不存在时应抛出异常")
    void testStartInstanceDefinitionNotFound() {
        when(definitionMapper.selectById(999L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowEngine.startInstance(999L, "biz-001", "FILE_REVIEW", 2001L, "alice"));
        assertTrue(ex.getMessage().contains("工作流定义不存在"));
    }

    // ===================== testSequentialApproval_AllApprove =====================

    /**
     * 测试 SEQUENTIAL（线性）审批：两个审批人按顺序全部通过 → 实例 APPROVED
     */
    @Test
    @DisplayName("SEQUENTIAL 线性审批 - 全部通过后实例状态变为 APPROVED")
    void testSequentialApproval_AllApprove() {
        WorkflowDefinitionEntity definition = buildDefinition(
                "start", "approval1", "end", "SEQUENTIAL", Arrays.asList(1001L, 1002L));
        when(definitionMapper.selectById(1L)).thenReturn(definition);
        when(reviewMapper.insert(any())).thenReturn(1);
        when(instanceMapper.updateById(any())).thenReturn(1);

        // 共享同一个实例引用，模拟数据库读写一致
        WorkflowInstanceEntity instance = buildInstance(1L, "approval1", "审批节点1", "1001");
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        // 第一审批人 1001 通过 → currentApprovers 推进到 1002
        WorkflowInstanceEntity after1 = workflowEngine.processDecision(1L, 1001L, "alice", "APPROVE", "ok");
        assertEquals("PENDING", after1.getStatus());
        assertEquals("1002", after1.getCurrentApprovers());
        assertEquals("approval1", after1.getCurrentNodeId());

        // 第二审批人 1002 通过 → 实例完成
        WorkflowInstanceEntity after2 = workflowEngine.processDecision(1L, 1002L, "bob", "APPROVE", "ok");
        assertEquals("APPROVED", after2.getStatus());
        assertNull(after2.getCurrentNodeId());
        assertNull(after2.getCurrentApprovers());
    }

    // ===================== testSequentialApproval_Reject =====================

    /**
     * 测试 SEQUENTIAL 审批驳回：第一审批人驳回 → 实例 REJECTED
     */
    @Test
    @DisplayName("SEQUENTIAL 线性审批 - 任一审批人驳回则实例状态变为 REJECTED")
    void testSequentialApproval_Reject() {
        WorkflowDefinitionEntity definition = buildDefinition(
                "start", "approval1", "end", "SEQUENTIAL", Arrays.asList(1001L, 1002L));
        when(definitionMapper.selectById(1L)).thenReturn(definition);
        when(reviewMapper.insert(any())).thenReturn(1);
        when(instanceMapper.updateById(any())).thenReturn(1);

        WorkflowInstanceEntity instance = buildInstance(1L, "approval1", "审批节点1", "1001");
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        WorkflowInstanceEntity after = workflowEngine.processDecision(1L, 1001L, "alice", "REJECT", "不通过");
        assertEquals("REJECTED", after.getStatus());
        assertNull(after.getCurrentNodeId());
        assertNull(after.getCurrentApprovers());
    }

    // ===================== testParallelAll_Approve =====================

    /**
     * 测试 PARALLEL_ALL（会签）审批：两个审批人都通过后才进入下一节点 → 实例 APPROVED
     */
    @Test
    @DisplayName("PARALLEL_ALL 会签 - 全部审批人通过后实例状态变为 APPROVED")
    void testParallelAll_Approve() {
        WorkflowDefinitionEntity definition = buildDefinition(
                "start", "approval1", "end", "PARALLEL_ALL", Arrays.asList(1001L, 1002L));
        when(definitionMapper.selectById(1L)).thenReturn(definition);
        when(reviewMapper.insert(any())).thenReturn(1);
        when(instanceMapper.updateById(any())).thenReturn(1);

        // 会签初始 currentApprovers 含全部审批人
        WorkflowInstanceEntity instance = buildInstance(1L, "approval1", "审批节点1", "1001,1002");
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        // 1001 通过 → 仍 PENDING，剩余审批人 1002
        WorkflowInstanceEntity after1 = workflowEngine.processDecision(1L, 1001L, "alice", "APPROVE", "ok");
        assertEquals("PENDING", after1.getStatus());
        assertEquals("1002", after1.getCurrentApprovers());

        // 1002 通过 → 全部通过，实例完成
        WorkflowInstanceEntity after2 = workflowEngine.processDecision(1L, 1002L, "bob", "APPROVE", "ok");
        assertEquals("APPROVED", after2.getStatus());
        assertNull(after2.getCurrentApprovers());
    }

    // ===================== testParallelAny_Approve =====================

    /**
     * 测试 PARALLEL_ANY（或签）审批：任一审批人通过即进入下一节点 → 实例 APPROVED
     */
    @Test
    @DisplayName("PARALLEL_ANY 或签 - 任一审批人通过即完成实例")
    void testParallelAny_Approve() {
        WorkflowDefinitionEntity definition = buildDefinition(
                "start", "approval1", "end", "PARALLEL_ANY", Arrays.asList(1001L, 1002L));
        when(definitionMapper.selectById(1L)).thenReturn(definition);
        when(reviewMapper.insert(any())).thenReturn(1);
        when(instanceMapper.updateById(any())).thenReturn(1);

        WorkflowInstanceEntity instance = buildInstance(1L, "approval1", "审批节点1", "1001,1002");
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        // 任一审批人通过即完成
        WorkflowInstanceEntity after = workflowEngine.processDecision(1L, 1001L, "alice", "APPROVE", "ok");
        assertEquals("APPROVED", after.getStatus());
        assertNull(after.getCurrentNodeId());
        assertNull(after.getCurrentApprovers());
    }

    /**
     * 测试审批决定 - 非审批人调用应抛出异常
     */
    @Test
    @DisplayName("审批决定 - 非当前审批人调用应抛出异常")
    void testProcessDecisionNotInApprovers() {
        WorkflowDefinitionEntity definition = buildDefinition(
                "start", "approval1", "end", "SEQUENTIAL", Arrays.asList(1001L));
        when(definitionMapper.selectById(1L)).thenReturn(definition);
        WorkflowInstanceEntity instance = buildInstance(1L, "approval1", "审批节点1", "1001");
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowEngine.processDecision(1L, 9999L, "eve", "APPROVE", "ok"));
        assertTrue(ex.getMessage().contains("不在审批人列表中"));
    }

    /**
     * 测试审批决定 - 非 PENDING 状态实例不允许审批
     */
    @Test
    @DisplayName("审批决定 - 非 PENDING 状态应抛出异常")
    void testProcessDecisionNotPending() {
        WorkflowInstanceEntity instance = buildInstance(1L, "approval1", "审批节点1", "1001");
        instance.setStatus("APPROVED");
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        assertThrows(BusinessException.class,
                () -> workflowEngine.processDecision(1L, 1001L, "alice", "APPROVE", "ok"));
    }

    // ===================== testGetPendingInstances =====================

    /**
     * 测试查询待审批列表 - 应返回当前审批人待办实例
     */
    @Test
    @DisplayName("待审批列表 - 应返回当前审批人待办实例")
    void testGetPendingInstances() {
        WorkflowInstanceEntity inst1 = buildInstance(101L, "approval1", "审批节点1", "1001,1002");
        WorkflowInstanceEntity inst2 = buildInstance(102L, "approval2", "审批节点2", "1003");
        WorkflowInstanceEntity inst3 = buildInstance(103L, "approval1", "审批节点1", "1001");

        when(instanceMapper.selectByStatus("PENDING"))
                .thenReturn(Arrays.asList(inst1, inst2, inst3));

        // 审批人 1001 待办：inst1 + inst3
        List<WorkflowInstanceVO> result = workflowEngine.getPendingInstances(1001L);

        assertNotNull(result);
        assertEquals(2, result.size());
        // 验证返回结果包含 inst1 与 inst3
        assertTrue(result.stream().anyMatch(vo -> vo.getId().equals(101L)));
        assertTrue(result.stream().anyMatch(vo -> vo.getId().equals(103L)));
        assertFalse(result.stream().anyMatch(vo -> vo.getId().equals(102L)));
        // 验证字段映射
        WorkflowInstanceVO vo0 = result.stream().filter(v -> v.getId().equals(101L)).findFirst().get();
        assertEquals("PENDING", vo0.getStatus());
        assertEquals("审批节点1", vo0.getCurrentNodeName());
        assertEquals("biz-001", vo0.getBusinessId());
        assertEquals("FILE_REVIEW", vo0.getBusinessType());
    }

    /**
     * 测试查询待审批列表 - 无待办时返回空列表
     */
    @Test
    @DisplayName("待审批列表 - 无待办时返回空列表")
    void testGetPendingInstancesEmpty() {
        when(instanceMapper.selectByStatus("PENDING")).thenReturn(Collections.emptyList());

        List<WorkflowInstanceVO> result = workflowEngine.getPendingInstances(1001L);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ===================== testGetInstanceDetail =====================

    /**
     * 测试实例详情查询 - 应返回实例信息及审批记录列表
     */
    @Test
    @DisplayName("实例详情 - 应包含审批记录列表")
    void testGetInstanceDetail() {
        WorkflowInstanceEntity instance = buildInstance(1L, "approval1", "审批节点1", "1001");
        instance.setStatus("APPROVED");
        when(instanceMapper.selectById(1L)).thenReturn(instance);

        WorkflowReviewEntity r1 = new WorkflowReviewEntity();
        r1.setId(10L);
        r1.setInstanceId(1L);
        r1.setNodeId("approval1");
        r1.setReviewerId(1001L);
        r1.setReviewerName("alice");
        r1.setDecision("APPROVE");
        r1.setComment("通过");

        WorkflowReviewEntity r2 = new WorkflowReviewEntity();
        r2.setId(11L);
        r2.setInstanceId(1L);
        r2.setNodeId("approval2");
        r2.setReviewerId(1002L);
        r2.setReviewerName("bob");
        r2.setDecision("APPROVE");
        r2.setComment("同意");

        when(reviewMapper.selectByInstanceId(1L)).thenReturn(Arrays.asList(r1, r2));

        WorkflowInstanceVO vo = workflowEngine.getInstanceDetail(1L);

        assertNotNull(vo);
        assertEquals(1L, vo.getId());
        assertEquals("APPROVED", vo.getStatus());
        assertEquals("审批节点1", vo.getCurrentNodeName());
        assertEquals("biz-001", vo.getBusinessId());
        assertEquals("alice", vo.getSubmitterName());
        assertNotNull(vo.getReviews());
        assertEquals(2, vo.getReviews().size());
        // 验证审批记录字段映射
        assertEquals("approval1", vo.getReviews().get(0).getNodeId());
        assertEquals("alice", vo.getReviews().get(0).getReviewerName());
        assertEquals("APPROVE", vo.getReviews().get(0).getDecision());
        assertEquals("通过", vo.getReviews().get(0).getComment());
    }

    /**
     * 测试实例详情查询 - 实例不存在时抛出异常
     */
    @Test
    @DisplayName("实例详情 - 实例不存在时应抛出异常")
    void testGetInstanceDetailNotFound() {
        when(instanceMapper.selectById(999L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> workflowEngine.getInstanceDetail(999L));
    }

    // ===================== 辅助方法 =====================

    /**
     * 构造测试用工作流定义实体
     *
     * <p>构造一个三节点工作流：start → approval1 → end，单审批节点。
     * 节点/边序列化为 JSON 写入 {@code nodesJson}/{@code edgesJson}。</p>
     *
     * @param startId     START 节点ID
     * @param approvalId  APPROVAL 节点ID
     * @param endId       END 节点ID
     * @param mode        审批模式：SEQUENTIAL/PARALLEL_ALL/PARALLEL_ANY
     * @param approverIds 审批人ID列表
     * @return 工作流定义实体
     */
    private WorkflowDefinitionEntity buildDefinition(String startId, String approvalId, String endId,
                                                       String mode, List<Long> approverIds) {
        WorkflowNodeDTO start = new WorkflowNodeDTO();
        start.setNodeId(startId);
        start.setNodeName("开始");
        start.setNodeType("START");

        WorkflowNodeDTO approval = new WorkflowNodeDTO();
        approval.setNodeId(approvalId);
        approval.setNodeName("审批节点1");
        approval.setNodeType("APPROVAL");
        approval.setApprovalMode(mode);
        approval.setApproverIds(approverIds);

        WorkflowNodeDTO end = new WorkflowNodeDTO();
        end.setNodeId(endId);
        end.setNodeName("结束");
        end.setNodeType("END");

        List<WorkflowNodeDTO> nodes = Arrays.asList(start, approval, end);

        WorkflowEdgeDTO e1 = new WorkflowEdgeDTO();
        e1.setSourceNodeId(startId);
        e1.setTargetNodeId(approvalId);

        WorkflowEdgeDTO e2 = new WorkflowEdgeDTO();
        e2.setSourceNodeId(approvalId);
        e2.setTargetNodeId(endId);

        List<WorkflowEdgeDTO> edges = Arrays.asList(e1, e2);

        WorkflowDefinitionEntity definition = new WorkflowDefinitionEntity();
        definition.setId(1L);
        definition.setName("测试工作流");
        definition.setBusinessType("FILE_REVIEW");
        definition.setEnabled(1);
        definition.setVersion(1);
        try {
            definition.setNodesJson(objectMapper.writeValueAsString(nodes));
            definition.setEdgesJson(objectMapper.writeValueAsString(edges));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return definition;
    }

    /**
     * 构造测试用审批实例实体
     *
     * @param id              实例ID
     * @param currentNodeId   当前节点ID
     * @param currentNodeName  当前节点名称
     * @param currentApprovers 当前审批人字符串
     * @return 实例实体
     */
    private WorkflowInstanceEntity buildInstance(Long id, String currentNodeId,
                                                   String currentNodeName, String currentApprovers) {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(id);
        instance.setWorkflowId(1L);
        instance.setWorkflowName("测试工作流");
        instance.setBusinessId("biz-001");
        instance.setBusinessType("FILE_REVIEW");
        instance.setSubmitterId(2001L);
        instance.setSubmitterName("alice");
        instance.setStatus("PENDING");
        instance.setCurrentNodeId(currentNodeId);
        instance.setCurrentNodeName(currentNodeName);
        instance.setCurrentApprovers(currentApprovers);
        return instance;
    }
}
