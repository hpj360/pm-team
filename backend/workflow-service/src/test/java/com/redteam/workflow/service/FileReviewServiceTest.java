package com.redteam.workflow.service;

import com.redteam.common.entity.WorkflowDefinitionEntity;
import com.redteam.common.entity.WorkflowInstanceEntity;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.WorkflowDefinitionMapper;
import com.redteam.common.mapper.WorkflowInstanceMapper;
import com.redteam.workflow.dto.WorkflowInstanceVO;
import com.redteam.workflow.dto.WorkflowReviewVO;
import com.redteam.workflow.producer.WorkflowApprovalEventProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FileReviewService} 单元测试
 *
 * <p>使用 Mockito 隔离 {@link WorkflowEngine} 与 Mapper，验证文件评审服务
 * 对引擎的封装逻辑、businessId/businessType 传参约定及异常分支。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileReviewServiceTest {

    @Mock
    private WorkflowEngine workflowEngine;

    @Mock
    private WorkflowDefinitionMapper definitionMapper;

    @Mock
    private WorkflowInstanceMapper instanceMapper;

    @Mock
    private WorkflowApprovalEventProducer approvalEventProducer;

    @InjectMocks
    private FileReviewService fileReviewService;

    // ===================== testSubmitReview =====================

    /**
     * 测试提交文件评审 - 应定位 FILE_REVIEW 定义并启动工作流实例
     */
    @Test
    @DisplayName("提交文件评审 - 应启动工作流实例且 businessId=fileId、businessType=FILE_REVIEW")
    void testSubmitReview() {
        // 准备：FILE_REVIEW 工作流定义已存在且启用
        WorkflowDefinitionEntity definition = buildDefinition(1L, "文件评审工作流", 1, 1);
        when(definitionMapper.selectByBusinessType("FILE_REVIEW"))
                .thenReturn(Collections.singletonList(definition));

        // 引擎返回已创建实例
        WorkflowInstanceEntity engineInstance = buildInstance(100L, "PENDING",
                "node-approval-1", "评审人1", "1001");
        when(workflowEngine.startInstance(eq(1L), eq("5001"), eq("FILE_REVIEW"),
                eq(2001L), eq("alice")))
                .thenReturn(engineInstance);

        // 执行
        WorkflowInstanceEntity result = fileReviewService.submitReview(
                5001L, 2001L, "alice", "请评审此文件");

        // 验证返回值
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("PENDING", result.getStatus());

        // 验证引擎被以正确的 businessId(fileId字符串) 与 businessType 调用
        verify(workflowEngine, times(1)).startInstance(
                eq(1L), eq("5001"), eq("FILE_REVIEW"), eq(2001L), eq("alice"));
    }

    /**
     * 测试提交文件评审 - 工作流模板未初始化时应抛出异常
     */
    @Test
    @DisplayName("提交文件评审 - FILE_REVIEW 模板不存在时应抛出业务异常")
    void testSubmitReviewTemplateNotFound() {
        when(definitionMapper.selectByBusinessType("FILE_REVIEW"))
                .thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileReviewService.submitReview(5001L, 2001L, "alice", "comment"));
        assertTrue(ex.getMessage().contains("文件评审工作流模板未初始化"));
        // 引擎不应被调用
        verify(workflowEngine, never()).startInstance(any(), any(), any(), any(), any());
    }

    // ===================== testProcessReview_Approve =====================

    /**
     * 测试评审通过 - 应透传给引擎处理
     */
    @Test
    @DisplayName("评审决定 - APPROVE 应透传给引擎并返回更新后的实例")
    void testProcessReview_Approve() {
        WorkflowInstanceEntity approved = buildInstance(100L, "APPROVED",
                null, null, null);
        when(workflowEngine.processDecision(eq(100L), eq(1001L), eq("alice"),
                eq("APPROVE"), eq("同意")))
                .thenReturn(approved);

        WorkflowInstanceEntity result = fileReviewService.processReview(
                100L, 1001L, "alice", "APPROVE", "同意");

        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
        assertNull(result.getCurrentNodeId());
        verify(workflowEngine, times(1)).processDecision(
                eq(100L), eq(1001L), eq("alice"), eq("APPROVE"), eq("同意"));
    }

    // ===================== testProcessReview_Reject =====================

    /**
     * 测试评审驳回 - 应透传给引擎处理
     */
    @Test
    @DisplayName("评审决定 - REJECT 应透传给引擎并返回 REJECTED 实例")
    void testProcessReview_Reject() {
        WorkflowInstanceEntity rejected = buildInstance(100L, "REJECTED",
                null, null, null);
        when(workflowEngine.processDecision(eq(100L), eq(1001L), eq("alice"),
                eq("REJECT"), eq("不通过")))
                .thenReturn(rejected);

        WorkflowInstanceEntity result = fileReviewService.processReview(
                100L, 1001L, "alice", "REJECT", "不通过");

        assertNotNull(result);
        assertEquals("REJECTED", result.getStatus());
        assertNull(result.getCurrentApprovers());
        verify(workflowEngine, times(1)).processDecision(
                eq(100L), eq(1001L), eq("alice"), eq("REJECT"), eq("不通过"));
    }

    // ===================== testGetFileReviewStatus =====================

    /**
     * 测试查询文件评审状态 - 应返回最新实例详情
     */
    @Test
    @DisplayName("获取文件评审状态 - 应返回最新实例详情 VO（含审批记录）")
    void testGetFileReviewStatus() {
        // 准备：fileId=5001 对应两条历史实例，最新一条 id=200
        WorkflowInstanceEntity inst1 = buildInstance(199L, "REJECTED",
                null, null, null);
        inst1.setBusinessId("5001");
        WorkflowInstanceEntity inst2 = buildInstance(200L, "PENDING",
                "node-approval-1", "评审人1", "1001");
        inst2.setBusinessId("5001");
        // selectByBusinessId 已按 created_at DESC 排序，第一条即最新
        when(instanceMapper.selectByBusinessId("5001", "FILE_REVIEW"))
                .thenReturn(Arrays.asList(inst2, inst1));

        // 引擎返回详情 VO
        WorkflowInstanceVO vo = new WorkflowInstanceVO();
        vo.setId(200L);
        vo.setBusinessId("5001");
        vo.setBusinessType("FILE_REVIEW");
        vo.setStatus("PENDING");
        vo.setCurrentNodeName("评审人1");
        WorkflowReviewVO review = new WorkflowReviewVO();
        review.setReviewerId(1001L);
        review.setReviewerName("alice");
        review.setDecision("APPROVE");
        review.setComment("通过");
        vo.setReviews(Collections.singletonList(review));
        when(workflowEngine.getInstanceDetail(200L)).thenReturn(vo);

        // 执行
        WorkflowInstanceVO result = fileReviewService.getFileReviewStatus(5001L);

        // 验证：返回最新实例的详情
        assertNotNull(result);
        assertEquals(200L, result.getId());
        assertEquals("PENDING", result.getStatus());
        assertEquals("评审人1", result.getCurrentNodeName());
        assertNotNull(result.getReviews());
        assertEquals(1, result.getReviews().size());
        assertEquals("alice", result.getReviews().get(0).getReviewerName());
        // 验证使用最新实例 id 调用引擎
        verify(workflowEngine, times(1)).getInstanceDetail(200L);
    }

    /**
     * 测试查询文件评审状态 - 文件未提交评审时返回 null
     */
    @Test
    @DisplayName("获取文件评审状态 - 文件未提交评审时返回 null")
    void testGetFileReviewStatusNotFound() {
        when(instanceMapper.selectByBusinessId("9999", "FILE_REVIEW"))
                .thenReturn(Collections.emptyList());

        WorkflowInstanceVO result = fileReviewService.getFileReviewStatus(9999L);

        assertNull(result);
        verify(workflowEngine, never()).getInstanceDetail(any());
    }

    // ===================== testGetPendingReviews =====================

    /**
     * 测试待评审列表 - 应返回当前审批人待办
     */
    @Test
    @DisplayName("获取待评审列表 - 应返回当前审批人待办实例列表")
    void testGetPendingReviews() {
        WorkflowInstanceVO vo1 = new WorkflowInstanceVO();
        vo1.setId(100L);
        vo1.setBusinessId("5001");
        vo1.setBusinessType("FILE_REVIEW");
        vo1.setStatus("PENDING");
        vo1.setCurrentNodeName("评审人1");

        WorkflowInstanceVO vo2 = new WorkflowInstanceVO();
        vo2.setId(101L);
        vo2.setBusinessId("5002");
        vo2.setBusinessType("FILE_REVIEW");
        vo2.setStatus("PENDING");
        vo2.setCurrentNodeName("评审人1");

        when(workflowEngine.getPendingInstances(1001L))
                .thenReturn(Arrays.asList(vo1, vo2));

        // 执行
        List<WorkflowInstanceVO> result = fileReviewService.getPendingReviews(1001L);

        // 验证
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(v -> v.getId().equals(100L)));
        assertTrue(result.stream().anyMatch(v -> v.getId().equals(101L)));
        // 验证全部为 FILE_REVIEW 待办
        assertTrue(result.stream().allMatch(v -> "FILE_REVIEW".equals(v.getBusinessType())));
        assertTrue(result.stream().allMatch(v -> "PENDING".equals(v.getStatus())));
        verify(workflowEngine, times(1)).getPendingInstances(1001L);
    }

    // ===================== 辅助方法 =====================

    /**
     * 构造测试用工作流定义实体
     *
     * @param id      定义ID
     * @param name    工作流名称
     * @param version 版本号
     * @param enabled 启用状态
     * @return 工作流定义实体
     */
    private WorkflowDefinitionEntity buildDefinition(Long id, String name, Integer version, Integer enabled) {
        WorkflowDefinitionEntity definition = new WorkflowDefinitionEntity();
        definition.setId(id);
        definition.setName(name);
        definition.setBusinessType("FILE_REVIEW");
        definition.setEnabled(enabled);
        definition.setVersion(version);
        return definition;
    }

    /**
     * 构造测试用工作流实例实体
     *
     * @param id                实例ID
     * @param status            状态
     * @param currentNodeId     当前节点ID
     * @param currentNodeName   当前节点名称
     * @param currentApprovers  当前审批人字符串
     * @return 实例实体
     */
    private WorkflowInstanceEntity buildInstance(Long id, String status,
                                                   String currentNodeId,
                                                   String currentNodeName,
                                                   String currentApprovers) {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(id);
        instance.setWorkflowId(1L);
        instance.setWorkflowName("文件评审工作流");
        instance.setBusinessId("5001");
        instance.setBusinessType("FILE_REVIEW");
        instance.setSubmitterId(2001L);
        instance.setSubmitterName("alice");
        instance.setStatus(status);
        instance.setCurrentNodeId(currentNodeId);
        instance.setCurrentNodeName(currentNodeName);
        instance.setCurrentApprovers(currentApprovers);
        instance.setCreatedAt(LocalDateTime.now());
        return instance;
    }
}
