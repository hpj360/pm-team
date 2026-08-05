package com.redteam.workflow.service;

import com.redteam.common.entity.WorkflowDefinitionEntity;
import com.redteam.common.entity.WorkflowInstanceEntity;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.WorkflowDefinitionMapper;
import com.redteam.common.mapper.WorkflowInstanceMapper;
import com.redteam.workflow.dto.WorkflowInstanceVO;
import com.redteam.workflow.event.ApprovalEventType;
import com.redteam.workflow.producer.WorkflowApprovalEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文件评审服务
 *
 * <p>基于 {@link WorkflowEngine} 封装文件评审业务能力：</p>
 * <ul>
 *   <li>提交文件评审：定位 FILE_REVIEW 工作流定义并启动实例</li>
 *   <li>处理评审决定：透传给引擎推进流转</li>
 *   <li>查询文件评审状态：按 fileId 查询最新实例详情</li>
 *   <li>查询待评审列表：按审批人查询待办</li>
 * </ul>
 *
 * <p>业务约定：</p>
 * <ul>
 *   <li>{@code businessType} 固定为 {@code FILE_REVIEW}</li>
 *   <li>{@code businessId} 使用文件 ID 字符串</li>
 * </ul>
 *
 * @author 红方团队
 */
@Service
@Slf4j
public class FileReviewService {

    /** 文件评审业务类型 */
    private static final String BUSINESS_TYPE = "FILE_REVIEW";

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WorkflowDefinitionMapper definitionMapper;

    @Autowired
    private WorkflowInstanceMapper instanceMapper;

    /**
     * 审批事件生产者（V4.7-P0-4：提交评审时发布 SUBMIT 事件）
     */
    @Autowired
    private WorkflowApprovalEventProducer approvalEventProducer;

    /**
     * 提交文件评审
     *
     * <p>定位 FILE_REVIEW 类型的启用工作流定义（取版本号最高的一条），
     * 调用 {@link WorkflowEngine#startInstance} 启动评审实例。
     * {@code businessId} 使用 {@code fileId} 字符串，{@code businessType} 固定为 {@code FILE_REVIEW}。</p>
     *
     * @param fileId        文件ID
     * @param submitterId   提交人ID
     * @param submitterName 提交人姓名
     * @param comment       提交说明
     * @return 已创建的工作流实例实体
     */
    public WorkflowInstanceEntity submitReview(Long fileId, Long submitterId,
                                                 String submitterName, String comment) {
        log.info("提交文件评审: fileId={}, submitterId={}, comment={}", fileId, submitterId, comment);

        WorkflowDefinitionEntity definition = findEnabledDefinition();
        String businessId = String.valueOf(fileId);

        WorkflowInstanceEntity instance = workflowEngine.startInstance(
                definition.getId(), businessId, BUSINESS_TYPE, submitterId, submitterName);
        log.info("文件评审实例已创建: fileId={}, instanceId={}", fileId, instance.getId());

        // V4.7-P0-4：发布 SUBMIT 事件到 notification-service（发送失败仅记日志，不阻塞主流程）
        approvalEventProducer.sendApprovalEvent(instance, ApprovalEventType.SUBMIT, submitterName, comment);

        return instance;
    }

    /**
     * 处理评审决定
     *
     * <p>透传给 {@link WorkflowEngine#processDecision} 处理，支持 APPROVE / REJECT。</p>
     *
     * @param instanceId    实例ID
     * @param reviewerId    审批人ID
     * @param reviewerName  审批人姓名
     * @param decision      决定：APPROVE / REJECT
     * @param comment       评审意见
     * @return 更新后的工作流实例实体
     */
    public WorkflowInstanceEntity processReview(Long instanceId, Long reviewerId,
                                                  String reviewerName, String decision, String comment) {
        log.info("处理文件评审决定: instanceId={}, reviewerId={}, decision={}",
                instanceId, reviewerId, decision);
        return workflowEngine.processDecision(instanceId, reviewerId, reviewerName, decision, comment);
    }

    /**
     * 获取文件评审状态
     *
     * <p>按 {@code fileId} + {@code FILE_REVIEW} 查询实例列表，取最新一条返回详情（含审批记录）。
     * 文件未提交过评审时返回 {@code null}。</p>
     *
     * @param fileId 文件ID
     * @return 评审实例详情 VO；不存在返回 {@code null}
     */
    public WorkflowInstanceVO getFileReviewStatus(Long fileId) {
        log.info("查询文件评审状态: fileId={}", fileId);
        String businessId = String.valueOf(fileId);
        List<WorkflowInstanceEntity> instances = instanceMapper.selectByBusinessId(businessId, BUSINESS_TYPE);
        if (instances == null || instances.isEmpty()) {
            log.info("文件评审实例不存在: fileId={}", fileId);
            return null;
        }
        // 取最新一条（selectByBusinessId 已按 created_at DESC 排序）
        WorkflowInstanceEntity latest = instances.get(0);
        return workflowEngine.getInstanceDetail(latest.getId());
    }

    /**
     * 获取待评审列表（按审批人）
     *
     * <p>透传给 {@link WorkflowEngine#getPendingInstances} 查询当前审批人待办列表。</p>
     *
     * @param reviewerId 审批人ID
     * @return 待评审实例列表
     */
    public List<WorkflowInstanceVO> getPendingReviews(Long reviewerId) {
        log.info("查询待评审列表: reviewerId={}", reviewerId);
        return workflowEngine.getPendingInstances(reviewerId);
    }

    // ===================== 内部方法 =====================

    /**
     * 查找启用的 FILE_REVIEW 工作流定义
     *
     * <p>按业务类型查询（已按版本号倒序），取第一条启用记录。
     * 不存在时抛出业务异常。</p>
     *
     * @return 工作流定义实体
     */
    private WorkflowDefinitionEntity findEnabledDefinition() {
        List<WorkflowDefinitionEntity> definitions = definitionMapper.selectByBusinessType(BUSINESS_TYPE);
        if (definitions == null || definitions.isEmpty()) {
            throw BusinessException.of("文件评审工作流模板未初始化，请检查 FileReviewTemplateInitializer");
        }
        // selectByBusinessType 已按 version DESC 排序，取最新版本
        WorkflowDefinitionEntity definition = definitions.get(0);
        if (definition.getEnabled() == null || definition.getEnabled() != 1) {
            throw BusinessException.of("文件评审工作流模板未启用: " + definition.getName());
        }
        return definition;
    }
}
