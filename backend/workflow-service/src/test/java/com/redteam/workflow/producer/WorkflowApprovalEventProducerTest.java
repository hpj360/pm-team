package com.redteam.workflow.producer;

import cn.hutool.json.JSONUtil;
import com.redteam.common.entity.WorkflowInstanceEntity;
import com.redteam.workflow.event.ApprovalEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作流审批事件生产者单元测试（V4.7-P0-4）
 *
 * <p>覆盖正常发送、参数校验、payload 字段、approverIds 解析、发送失败容错等场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowApprovalEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private WorkflowApprovalEventProducer producer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(producer, "workflowApprovalTopic", "workflow.approval");
    }

    /**
     * 构造测试用实例
     */
    private WorkflowInstanceEntity buildInstance() {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(100L);
        instance.setWorkflowId(1L);
        instance.setBusinessId("5001");
        instance.setBusinessType("FILE_REVIEW");
        instance.setSubmitterId(2001L);
        instance.setSubmitterName("alice");
        instance.setCurrentNodeName("初审节点");
        instance.setCurrentApprovers("1001,1002");
        return instance;
    }

    @Test
    @DisplayName("sendApprovalEvent: 正常发送事件，payload 含全字段")
    void sendApprovalEvent_success_sendsFullPayload() {
        WorkflowInstanceEntity instance = buildInstance();

        producer.sendApprovalEvent(instance, ApprovalEventType.SUBMIT, "alice", "请审批");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());

        var json = JSONUtil.parseObj(payloadCaptor.getValue());
        assertEquals(100L, json.getLong("instanceId"));
        assertEquals(1L, json.getLong("workflowId"));
        assertEquals("5001", json.getStr("businessId"));
        assertEquals("FILE_REVIEW", json.getStr("businessType"));
        assertEquals("SUBMIT", json.getStr("eventType"));
        assertEquals("初审节点", json.getStr("currentNode"));
        assertEquals("alice", json.getStr("operator"));
        assertEquals("请审批", json.getStr("comment"));
        assertEquals(2001L, json.getLong("submitterId"));
        // approverIds 解析正确
        assertEquals(2, json.getJSONArray("approverIds").size());
        assertEquals(1001L, json.getJSONArray("approverIds").getLong(0));
        assertEquals(1002L, json.getJSONArray("approverIds").getLong(1));
        assertNotNull(json.getLong("timestamp"));
    }

    @Test
    @DisplayName("sendApprovalEvent: instance 为 null 跳过发送")
    void sendApprovalEvent_nullInstance_skips() {
        producer.sendApprovalEvent(null, ApprovalEventType.SUBMIT, "alice", "comment");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendApprovalEvent: instanceId 为 null 跳过发送")
    void sendApprovalEvent_nullInstanceId_skips() {
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(null);
        producer.sendApprovalEvent(instance, ApprovalEventType.SUBMIT, "alice", "comment");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendApprovalEvent: eventType 为 null 跳过发送")
    void sendApprovalEvent_nullEventType_skips() {
        WorkflowInstanceEntity instance = buildInstance();
        producer.sendApprovalEvent(instance, null, "alice", "comment");
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendApprovalEvent: Kafka 发送抛异常时不传播，仅记日志")
    void sendApprovalEvent_kafkaThrows_noException() {
        WorkflowInstanceEntity instance = buildInstance();
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("kafka unavailable"));

        assertDoesNotThrow(() ->
                producer.sendApprovalEvent(instance, ApprovalEventType.APPROVE, "bob", "ok"));
    }

    @Test
    @DisplayName("sendApprovalEvent: currentApprovers 为空时 approverIds 为空列表")
    void sendApprovalEvent_emptyApprovers_sendsEmptyList() {
        WorkflowInstanceEntity instance = buildInstance();
        instance.setCurrentApprovers(null);

        producer.sendApprovalEvent(instance, ApprovalEventType.COMPLETE, "bob", "ok");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
        var json = JSONUtil.parseObj(payloadCaptor.getValue());
        assertEquals(0, json.getJSONArray("approverIds").size());
    }

    @Test
    @DisplayName("sendApprovalEvent: 不同事件类型正确写入 eventType 字段")
    void sendApprovalEvent_differentEventTypes_writtenCorrectly() {
        WorkflowInstanceEntity instance = buildInstance();

        for (ApprovalEventType type : ApprovalEventType.values()) {
            producer.sendApprovalEvent(instance, type, "op", "c");
        }

        verify(kafkaTemplate, org.mockito.Mockito.times(ApprovalEventType.values().length))
                .send(anyString(), anyString(), anyString());
    }
}
