package com.redteam.notification.listener;

import cn.hutool.json.JSONUtil;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 通知事件监听器单元测试
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener listener;

    /**
     * 构造 Kafka ConsumerRecord
     */
    private ConsumerRecord<String, String> record(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, "key", value);
    }

    /**
     * 构造事件 JSON
     */
    private String eventJson(Long userId, String eventType, String title, String content,
                             String level, String relatedId, String relatedType) {
        Map<String, Object> map = new HashMap<>();
        if (userId != null) {
            map.put("userId", userId);
        }
        if (eventType != null) {
            map.put("eventType", eventType);
        }
        if (title != null) {
            map.put("title", title);
        }
        if (content != null) {
            map.put("content", content);
        }
        if (level != null) {
            map.put("level", level);
        }
        if (relatedId != null) {
            map.put("relatedId", relatedId);
        }
        if (relatedType != null) {
            map.put("relatedType", relatedType);
        }
        return JSONUtil.toJsonStr(map);
    }

    // ==================== 任务事件 ====================

    @Test
    @DisplayName("监听任务事件：成功创建通知，默认类型 TASK")
    void listenTaskEvents_success() {
        String json = eventJson(1001L, "TASK_COMPLETED", "任务完成", "任务已结束",
                "INFO", "task-123", "TASK");
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_TASK_EVENTS, json);

        listener.listenTaskEvents(record);

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationDTO dto = captor.getValue();
        assertEquals(1001L, dto.getUserId());
        assertEquals("TASK_COMPLETED", dto.getType());
        assertEquals("任务完成", dto.getTitle());
        assertEquals("任务已结束", dto.getContent());
        assertEquals("INFO", dto.getLevel());
        assertEquals("task-123", dto.getRelatedId());
        assertEquals("TASK", dto.getRelatedType());
        assertEquals("ALL", dto.getChannel());
    }

    // ==================== 文件事件 ====================

    @Test
    @DisplayName("监听文件事件：成功创建通知，默认类型 FILE")
    void listenFileEvents_success() {
        String json = eventJson(1002L, null, null, null, "WARN", null, null);
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_FILE_EVENTS, json);

        listener.listenFileEvents(record);

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationDTO dto = captor.getValue();
        assertEquals(1002L, dto.getUserId());
        // eventType 为空 -> 默认 FILE
        assertEquals("FILE", dto.getType());
        // title 为空 -> 默认标题 "文件通知"
        assertEquals("文件通知", dto.getTitle());
        // content 为空 -> 默认 "您有一条新通知"
        assertEquals("您有一条新通知", dto.getContent());
        assertEquals("WARN", dto.getLevel());
    }

    // ==================== 系统事件 ====================

    @Test
    @DisplayName("监听系统事件：成功创建通知，默认类型 SYSTEM")
    void listenSystemEvents_success() {
        String json = eventJson(1003L, "SYSTEM_MAINTENANCE", "系统维护", "将于今晚维护",
                "WARN", null, null);
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_SYSTEM_EVENTS, json);

        listener.listenSystemEvents(record);

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        NotificationDTO dto = captor.getValue();
        assertEquals(1003L, dto.getUserId());
        assertEquals("SYSTEM_MAINTENANCE", dto.getType());
        assertEquals("系统维护", dto.getTitle());
        assertEquals("将于今晚维护", dto.getContent());
    }

    // ==================== 异常与边界场景 ====================

    @Test
    @DisplayName("空消息：跳过不处理")
    void listenTaskEvents_emptyMessage_skips() {
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_TASK_EVENTS, "");

        listener.listenTaskEvents(record);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("缺少 userId：跳过不处理")
    void listenTaskEvents_missingUserId_skips() {
        String json = eventJson(null, "TASK_COMPLETED", "标题", "内容", null, null, null);
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_TASK_EVENTS, json);

        listener.listenTaskEvents(record);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("非法 JSON：捕获异常不抛出，不调用 service")
    void listenTaskEvents_invalidJson_handlesException() {
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_TASK_EVENTS, "not a json {{{");

        assertDoesNotThrow(() -> listener.listenTaskEvents(record));
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("service 抛异常：监听器不向上抛出")
    void listenTaskEvents_serviceThrowsException_notPropagated() {
        doThrow(new RuntimeException("DB 不可用"))
                .when(notificationService).sendNotification(any(NotificationDTO.class));
        String json = eventJson(1001L, "TASK_COMPLETED", "标题", "内容", "INFO", null, null);
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_TASK_EVENTS, json);

        assertDoesNotThrow(() -> listener.listenTaskEvents(record));
        verify(notificationService).sendNotification(any(NotificationDTO.class));
    }

    @Test
    @DisplayName("level 为空：默认 INFO")
    void listenTaskEvents_missingLevel_defaultsInfo() {
        String json = eventJson(1001L, "TASK_COMPLETED", "标题", "内容", null, null, null);
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_TASK_EVENTS, json);

        listener.listenTaskEvents(record);

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        assertEquals("INFO", captor.getValue().getLevel());
    }

    @Test
    @DisplayName("默认标题生成：覆盖各事件类型")
    void defaultTitle_allTypes() {
        // 通过文件事件验证 FILE 默认标题已在 listenFileEvents_success 验证
        // 这里验证 SYSTEM 默认标题
        String json = eventJson(1003L, null, null, null, null, null, null);
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_SYSTEM_EVENTS, json);

        listener.listenSystemEvents(record);

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        assertEquals("系统通知", captor.getValue().getTitle());
    }

    @Test
    @DisplayName("任务事件默认标题：TASK -> 任务通知")
    void taskEvent_defaultTitle() {
        String json = eventJson(1001L, null, null, null, null, null, null);
        ConsumerRecord<String, String> record = record(NotificationEventListener.TOPIC_TASK_EVENTS, json);

        listener.listenTaskEvents(record);

        ArgumentCaptor<NotificationDTO> captor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(notificationService).sendNotification(captor.capture());
        assertEquals("任务通知", captor.getValue().getTitle());
    }
}
