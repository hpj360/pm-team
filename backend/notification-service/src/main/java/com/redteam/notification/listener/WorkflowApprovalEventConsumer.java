package com.redteam.notification.listener;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.notification.dto.NotificationDTO;
import com.redteam.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流审批事件消费者（V4.7-P0-4）
 *
 * <p>监听 {@code workflow.approval} 主题，消费 workflow-service 投递的审批事件，
 * 根据 {@code eventType} 生成差异化文案，通过 {@link NotificationService} 推送站内信 + 飞书（IM 通道）。</p>
 *
 * <p>通知文案（按事件类型）：</p>
 * <ul>
 *   <li>SUBMIT：通知当前审批人 —— "您有新的评审待审批：{businessType} #{businessId}"</li>
 *   <li>APPROVE：通知提交人 —— "您的评审已通过：{businessType} #{businessId}"</li>
 *   <li>REJECT：通知提交人 —— "您的评审被驳回：{businessType} #{businessId}，原因：{comment}"</li>
 *   <li>COMPLETE：通知提交人 —— "评审流程已完成：{businessType} #{businessId}"</li>
 * </ul>
 *
 * <p>容错策略：单条通知失败仅记录日志，不影响其他通知与消费流程；整体消费失败不抛异常，避免阻塞。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowApprovalEventConsumer {

    /**
     * 工作流审批事件主题
     */
    public static final String TOPIC_WORKFLOW_APPROVAL = "workflow.approval";

    /**
     * 消费组：notification-service 专用
     */
    public static final String GROUP_ID = "notification-service-group";

    /**
     * 通知通道：IM（站内信 + 飞书 IM 投递）
     */
    private static final String CHANNEL_IM = "IM";

    /**
     * 通知类型：系统通知
     */
    private static final String TYPE_SYSTEM = "SYSTEM";

    /**
     * 通知级别
     */
    private static final String LEVEL_INFO = "INFO";
    private static final String LEVEL_WARN = "WARN";

    /**
     * 系统发送者ID
     */
    private static final Long SYSTEM_SENDER_ID = 0L;

    private final NotificationService notificationService;

    /**
     * 消费工作流审批事件，推送通知
     *
     * @param record Kafka 消息记录
     */
    @KafkaListener(topics = TOPIC_WORKFLOW_APPROVAL, groupId = GROUP_ID)
    public void onWorkflowApproval(ConsumerRecord<String, String> record) {
        String value = record.value();
        if (StrUtil.isBlank(value)) {
            log.warn("收到空审批事件，跳过: topic={}, offset={}", record.topic(), record.offset());
            return;
        }

        log.info("收到工作流审批事件: topic={}, offset={}, key={}", record.topic(), record.offset(), record.key());

        try {
            JSONObject event = JSONUtil.parseObj(value);
            String eventType = event.getStr("eventType");
            String businessType = StrUtil.blankToDefault(event.getStr("businessType"), "UNKNOWN");
            String businessId = StrUtil.blankToDefault(event.getStr("businessId"), "");
            String comment = StrUtil.blankToDefault(event.getStr("comment"), "");
            Long submitterId = event.getLong("submitterId");
            List<Long> approverIds = parseLongList(event.getJSONArray("approverIds"));

            if (StrUtil.isBlank(eventType)) {
                log.warn("审批事件缺少 eventType，跳过: offset={}", record.offset());
                return;
            }

            // 根据事件类型生成文案与确定接收人
            List<Long> recipientIds = new ArrayList<>();
            String title;
            String content;
            String level = LEVEL_INFO;

            switch (eventType) {
                case "SUBMIT":
                    // 通知当前审批人
                    recipientIds.addAll(approverIds);
                    title = "评审待审批";
                    content = "您有新的评审待审批：" + businessType + " #" + businessId;
                    break;
                case "APPROVE":
                    // 通知提交人
                    addIfNotNull(recipientIds, submitterId);
                    title = "评审已通过";
                    content = "您的评审已通过：" + businessType + " #" + businessId;
                    break;
                case "REJECT":
                    // 通知提交人
                    addIfNotNull(recipientIds, submitterId);
                    title = "评审被驳回";
                    content = "您的评审被驳回：" + businessType + " #" + businessId + "，原因：" + comment;
                    level = LEVEL_WARN;
                    break;
                case "COMPLETE":
                    // 通知提交人
                    addIfNotNull(recipientIds, submitterId);
                    title = "评审已完成";
                    content = "评审流程已完成：" + businessType + " #" + businessId;
                    break;
                default:
                    log.warn("未知审批事件类型，跳过: eventType={}", eventType);
                    return;
            }

            if (recipientIds.isEmpty()) {
                log.warn("审批事件无有效接收人，跳过通知: eventType={}, instanceId={}",
                        eventType, event.get("instanceId"));
                return;
            }

            // 向每个接收人推送通知（站内信 + 飞书 IM 通道）
            for (Long userId : recipientIds) {
                sendOneNotification(userId, title, content, level, businessId, businessType);
            }
            log.info("审批事件通知推送完成: eventType={}, recipientCount={}", eventType, recipientIds.size());

        } catch (Exception e) {
            // 整体处理失败仅记录日志，不抛异常，避免阻塞消费
            log.error("审批事件处理失败: topic={}, offset={}", record.topic(), record.offset(), e);
        }
    }

    /**
     * 向单个用户推送通知
     *
     * @param userId       接收人ID
     * @param title        通知标题
     * @param content      通知内容
     * @param level        通知级别
     * @param relatedId    关联业务ID（如 instanceId/businessId）
     * @param relatedType  关联业务类型
     */
    private void sendOneNotification(Long userId, String title, String content,
                                       String level, String relatedId, String relatedType) {
        try {
            NotificationDTO dto = new NotificationDTO();
            dto.setUserId(userId);
            dto.setSenderId(SYSTEM_SENDER_ID);
            dto.setTitle(title);
            dto.setContent(content);
            dto.setType(TYPE_SYSTEM);
            dto.setLevel(level);
            // channel=IM：NotificationService 会同时落库站内信 + 投递 IM（飞书）
            dto.setChannel(CHANNEL_IM);
            dto.setRelatedId(relatedId);
            dto.setRelatedType(relatedType);
            notificationService.sendNotification(dto);
            log.info("审批通知已推送: userId={}, title={}", userId, title);
        } catch (Exception e) {
            // 单条通知失败不影响其他接收人
            log.error("审批通知推送失败: userId={}, title={}", userId, title, e);
        }
    }

    /**
     * 解析 JSON 数组为 Long 列表
     *
     * @param array JSON 数组
     * @return Long 列表
     */
    private List<Long> parseLongList(JSONArray array) {
        List<Long> result = new ArrayList<>();
        if (array == null || array.isEmpty()) {
            return result;
        }
        for (Object item : array) {
            if (item == null) {
                continue;
            }
            try {
                result.add(Long.valueOf(String.valueOf(item)));
            } catch (NumberFormatException e) {
                // 忽略非数字
            }
        }
        return result;
    }

    /**
     * 非 null 时添加到列表
     *
     * @param list 列表
     * @param id   ID
     */
    private void addIfNotNull(List<Long> list, Long id) {
        if (id != null) {
            list.add(id);
        }
    }
}
