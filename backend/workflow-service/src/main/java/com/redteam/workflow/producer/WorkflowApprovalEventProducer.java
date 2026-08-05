package com.redteam.workflow.producer;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.redteam.common.entity.WorkflowInstanceEntity;
import com.redteam.workflow.event.ApprovalEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流审批事件生产者（V4.7-P0-4）
 *
 * <p>在审批流程的关键节点（提交/通过/驳回/完成）向 {@code workflow.approval} 主题投递事件，
 * 由 {@code notification-service} 消费并推送站内信与飞书通知。</p>
 *
 * <p>事件 JSON 结构：</p>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "instanceId": 123,
 *   "workflowId": 1,
 *   "businessId": "1001",
 *   "businessType": "FILE_REVIEW",
 *   "eventType": "SUBMIT",
 *   "currentNode": "初审节点",
 *   "operator": "张三",
 *   "comment": "请审批",
 *   "timestamp": 1700000000000
 * }
 * }</pre>
 *
 * <p>容错策略：消息发送失败仅记录日志，不抛异常，避免影响审批主流程。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class WorkflowApprovalEventProducer {

    /**
     * 审批事件主题
     */
    public static final String TOPIC_WORKFLOW_APPROVAL = "workflow.approval";

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 审批事件主题（可通过配置覆盖）
     */
    @Value("${redteam.kafka.topic.workflow-approval:workflow.approval}")
    private String workflowApprovalTopic;

    /**
     * 构造方法：注入 Spring Boot 自动配置的 KafkaTemplate
     *
     * @param kafkaTemplate KafkaTemplate
     */
    public WorkflowApprovalEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 发送审批事件
     *
     * @param instance  工作流实例（提供 instanceId/workflowId/businessId/businessType/currentNode）
     * @param eventType 事件类型（SUBMIT/APPROVE/REJECT/COMPLETE）
     * @param operator  操作人姓名（提交人/审批人）
     * @param comment   审批意见/提交说明
     */
    public void sendApprovalEvent(WorkflowInstanceEntity instance, ApprovalEventType eventType,
                                    String operator, String comment) {
        if (instance == null || instance.getId() == null) {
            log.warn("审批事件参数非法（instance 或 instanceId 为空），跳过发送: eventType={}", eventType);
            return;
        }
        if (eventType == null) {
            log.warn("审批事件 eventType 为空，跳过发送: instanceId={}", instance.getId());
            return;
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", IdUtil.fastSimpleUUID());
        event.put("instanceId", instance.getId());
        event.put("workflowId", instance.getWorkflowId());
        event.put("businessId", instance.getBusinessId());
        event.put("businessType", instance.getBusinessType());
        event.put("eventType", eventType.name());
        event.put("currentNode", StrUtil.blankToDefault(instance.getCurrentNodeName(), ""));
        event.put("operator", StrUtil.blankToDefault(operator, ""));
        event.put("comment", comment == null ? "" : comment);
        event.put("timestamp", System.currentTimeMillis());
        // 通知接收人信息（供 notification-service 决定通知对象）
        event.put("submitterId", instance.getSubmitterId());
        event.put("approverIds", parseApproverIdList(instance.getCurrentApprovers()));

        send(event, instance.getId());
    }

    /**
     * 解析审批人 ID 字符串（逗号分隔）为列表
     *
     * @param str 审批人 ID 字符串
     * @return 审批人 ID 列表
     */
    private List<Long> parseApproverIdList(String str) {
        List<Long> result = new ArrayList<>();
        if (StrUtil.isBlank(str)) {
            return result;
        }
        for (String part : str.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                result.add(Long.valueOf(trimmed));
            } catch (NumberFormatException e) {
                // 忽略非数字 token
            }
        }
        return result;
    }

    /**
     * 投递事件到 Kafka
     *
     * <p>失败仅记录日志，不抛异常，避免阻塞审批主流程。</p>
     *
     * @param event      事件体
     * @param instanceId 实例ID（作为消息 key）
     */
    private void send(Map<String, Object> event, Long instanceId) {
        try {
            String payload = JSONUtil.toJsonStr(event);
            String key = String.valueOf(instanceId);
            kafkaTemplate.send(workflowApprovalTopic, key, payload);
            log.info("审批事件已投递: topic={}, instanceId={}, eventType={}, eventId={}",
                    workflowApprovalTopic, instanceId, event.get("eventType"), event.get("eventId"));
        } catch (Exception e) {
            // 发送失败仅记录日志，不阻塞主流程
            log.error("审批事件投递失败: topic={}, instanceId={}, eventType={}, eventId={}",
                    workflowApprovalTopic, instanceId, event.get("eventType"), event.get("eventId"), e);
        }
    }
}
