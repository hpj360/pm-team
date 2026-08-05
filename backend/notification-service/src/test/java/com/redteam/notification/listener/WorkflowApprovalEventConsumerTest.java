package com.redteam.notification.listener;

import com.redteam.notification.dto.NotificationDTO;
import com.redteam.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 工作流审批事件消费者单元测试（V4.7-P0-4）
 *
 * <p>覆盖 SUBMIT/APPROVE/REJECT/COMPLETE 四类事件的通知推送、文案差异、接收人确定及异常分支。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowApprovalEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private WorkflowApprovalEventConsumer consumer;

    /**
     * 构造 Kafka 消息记录
     */
    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("workflow.approval", 0, 0L, "key", value);
    }

    /**
     * 构造审批事件 JSON
     */
    private String buildEventJson(String eventType, Long submitterId, String approverIdsJson,
                                    String comment) {
        return "{"
                + "\"eventId\":\"evt-1\","
                + "\"instanceId\":100,"
                + "\"workflowId\":1,"
                + "\"businessId\":\"5001\","
                + "\"businessType\":\"FILE_REVIEW\","
                + "\"eventType\":\"" + eventType + "\","
                + "\"currentNode\":\"初审节点\","
                + "\"operator\":\"alice\","
                + "\"comment\":\"" + comment + "\","
                + "\"timestamp\":1700000000000,"
                + "\"submitterId\":" + (submitterId == null ? "null" : submitterId) + ","
                + "\"approverIds\":" + approverIdsJson
                + "}";
    }

    @Test
    @DisplayName("SUBMIT: 通知所有当前审批人，文案为待审批")
    void onWorkflowApproval_submit_notifiesApprovers() {
        String json = buildEventJson("SUBMIT", 2001L, "[1001,1002]", "请审批");

        consumer.onWorkflowApproval(record(json));

        // 两个审批人各收到一条通知
        verify(notificationService, times(2)).sendNotification(any(NotificationDTO.class));

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService, times(2)).sendNotification(captor.capture());
        // 验证文案
        NotificationDTO first = captor.getAllValues().get(0);
        assertTrue(first.getContent().contains("您有新的评审待审批"));
        assertTrue(first.getContent().contains("FILE_REVIEW #5001"));
        assertEquals("IM", first.getChannel());
        assertEquals("SYSTEM", first.getType());
    }

    @Test
    @DisplayName("APPROVE: 通知提交人，文案为已通过")
    void onWorkflowApproval_approve_notifiesSubmitter() {
        String json = buildEventJson("APPROVE", 2001L, "[]", "同意");

        consumer.onWorkflowApproval(record(json));

        verify(notificationService, times(1)).sendNotification(any(NotificationDTO.class));
        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationDTO dto = captor.getValue();
        assertEquals(2001L, dto.getUserId());
        assertTrue(dto.getContent().contains("您的评审已通过"));
        assertEquals("INFO", dto.getLevel());
    }

    @Test
    @DisplayName("REJECT: 通知提交人，文案含驳回原因")
    void onWorkflowApproval_reject_notifiesSubmitterWithComment() {
        String json = buildEventJson("REJECT", 2001L, "[]", "材料不齐");

        consumer.onWorkflowApproval(record(json));

        verify(notificationService, times(1)).sendNotification(any(NotificationDTO.class));
        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationDTO dto = captor.getValue();
        assertEquals(2001L, dto.getUserId());
        assertTrue(dto.getContent().contains("被驳回"));
        assertTrue(dto.getContent().contains("材料不齐"));
        assertEquals("WARN", dto.getLevel());
    }

    @Test
    @DisplayName("COMPLETE: 通知提交人，文案为已完成")
    void onWorkflowApproval_complete_notifiesSubmitter() {
        String json = buildEventJson("COMPLETE", 2001L, "[]", "全部通过");

        consumer.onWorkflowApproval(record(json));

        verify(notificationService, times(1)).sendNotification(any(NotificationDTO.class));
        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationDTO dto = captor.getValue();
        assertEquals(2001L, dto.getUserId());
        assertTrue(dto.getContent().contains("评审流程已完成"));
    }

    @Test
    @DisplayName("空消息跳过，不发送通知")
    void onWorkflowApproval_nullValue_skips() {
        consumer.onWorkflowApproval(record(""));
        consumer.onWorkflowApproval(record(null));
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("未知 eventType 跳过，不发送通知")
    void onWorkflowApproval_unknownEventType_skips() {
        String json = buildEventJson("UNKNOWN_TYPE", 2001L, "[1001]", "comment");
        consumer.onWorkflowApproval(record(json));
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("SUBMIT 无审批人时不发送通知")
    void onWorkflowApproval_submitNoApprovers_skips() {
        String json = buildEventJson("SUBMIT", 2001L, "[]", "请审批");
        consumer.onWorkflowApproval(record(json));
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("APPROVE 无提交人时不发送通知")
    void onWorkflowApproval_approveNoSubmitter_skips() {
        String json = buildEventJson("APPROVE", null, "[]", "同意");
        consumer.onWorkflowApproval(record(json));
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    @DisplayName("非法 JSON 跳过，不发送通知且不抛异常")
    void onWorkflowApproval_invalidJson_skips() {
        consumer.onWorkflowApproval(record("not-a-json"));
        verify(notificationService, never()).sendNotification(any());
    }
}
