package com.redteam.workflow.event;

/**
 * 审批事件类型枚举（V4.7-P0-4）
 *
 * <p>定义工作流审批流程中各关键节点的事件类型，
 * 由 {@code WorkflowApprovalEventProducer} 投递到 {@code workflow.approval} 主题，
 * 供 {@code notification-service} 消费并推送站内信/飞书通知。</p>
 *
 * @author 红方团队
 */
public enum ApprovalEventType {

    /**
     * 提交评审：业务方提交评审申请，进入首个审批节点
     */
    SUBMIT,

    /**
     * 审批通过：当前节点审批人同意，流转至下一节点
     */
    APPROVE,

    /**
     * 审批驳回：当前节点审批人驳回，流程终止
     */
    REJECT,

    /**
     * 评审完成：所有节点通过，流程完结
     */
    COMPLETE
}
