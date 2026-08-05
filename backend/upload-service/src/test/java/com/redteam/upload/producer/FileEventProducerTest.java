package com.redteam.upload.producer;

import cn.hutool.json.JSONUtil;
import com.redteam.upload.dto.FileEvent;
import com.redteam.upload.entity.FileEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 文件事件生产者单元测试
 *
 * <p>覆盖文件上传/删除事件投递，验证：</p>
 * <ul>
 *   <li>事件 JSON 结构符合规范（eventId/eventType/fileId/fileSm3 等）</li>
 *   <li>Kafka 发送失败不影响主流程（仅记录日志）</li>
 *   <li>非法参数（null / fileId 缺失）跳过发送</li>
 * </ul>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private FileEventProducer producer;

    /**
     * 测试用主题
     */
    private static final String TOPIC = "redteam.file.events";

    @BeforeEach
    void setUp() {
        // 由于 FileEventProducer 通过构造器注入 @Qualifier("fileEventKafkaTemplate")，
        // 这里直接 new 实例，避免 @InjectMocks 受 qualifier 影响
        producer = new FileEventProducer(kafkaTemplate);
        ReflectionTestUtils.setField(producer, "fileEventsTopic", TOPIC);

        // kafkaTemplate.send 默认返回非空 CompletableFuture，避免 NPE
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    /**
     * 构造测试用 FileEntity
     */
    private FileEntity buildEntity() {
        FileEntity entity = new FileEntity();
        entity.setId(1001L);
        entity.setFilename("uuid.txt");
        entity.setOriginalFilename("test.txt");
        entity.setStoragePath("files/2026/07/27/uuid.txt");
        entity.setFileSize(12345L);
        entity.setFileType("txt");
        entity.setMimeType("text/plain");
        entity.setFileMd5("md5-fake");
        entity.setFileSm3("sm3-fake");
        entity.setTargetId(200L);
        return entity;
    }

    // ==================== sendFileUploadedEvent ====================

    @Nested
    @DisplayName("sendFileUploadedEvent: 文件上传事件")
    class SendFileUploadedEventTests {

        @Test
        @DisplayName("成功：投递 FILE_UPLOADED 事件到 redteam.file.events")
        void sendFileUploadedEvent_success() {
            FileEntity entity = buildEntity();

            producer.sendFileUploadedEvent(entity, 1001L);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq("1001"), payloadCaptor.capture());

            FileEvent event = JSONUtil.toBean(payloadCaptor.getValue(), FileEvent.class);
            assertEquals(FileEventProducer.EVENT_TYPE_FILE_UPLOADED, event.getEventType());
            assertEquals(1001L, event.getFileId());
            assertEquals("sm3-fake", event.getFileSm3());
            assertEquals("test.txt", event.getFileName());
            assertEquals("txt", event.getFileType());
            assertEquals(12345L, event.getFileSize());
            assertEquals("files/2026/07/27/uuid.txt", event.getStoragePath());
            assertEquals(200L, event.getTargetId());
            assertEquals(1001L, event.getUserId());
            assertNotNull(event.getEventId());
            assertEquals(32, event.getEventId().length()); // UUID
            assertNotNull(event.getTimestamp());
        }

        @Test
        @DisplayName("fileEntity 为 null：跳过发送")
        void sendFileUploadedEvent_nullEntity_skips() {
            producer.sendFileUploadedEvent(null, 1001L);
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("fileId 为 null：跳过发送")
        void sendFileUploadedEvent_nullFileId_skips() {
            FileEntity entity = new FileEntity();
            entity.setId(null);
            producer.sendFileUploadedEvent(entity, 1001L);
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("Kafka 发送异常：不传播，仅记录日志")
        void sendFileUploadedEvent_kafkaException_notPropagated() {
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Kafka 不可用"));
            FileEntity entity = buildEntity();

            assertDoesNotThrow(() -> producer.sendFileUploadedEvent(entity, 1001L));
        }

        @Test
        @DisplayName("userId 为 null：仍发送事件，事件 userId 字段为 null")
        void sendFileUploadedEvent_nullUserId_stillSent() {
            FileEntity entity = buildEntity();

            producer.sendFileUploadedEvent(entity, null);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), anyString(), payloadCaptor.capture());
            FileEvent event = JSONUtil.toBean(payloadCaptor.getValue(), FileEvent.class);
            assertEquals(FileEventProducer.EVENT_TYPE_FILE_UPLOADED, event.getEventType());
            assertNull(event.getUserId());
        }
    }

    // ==================== sendFileDeletedEvent ====================

    @Nested
    @DisplayName("sendFileDeletedEvent: 文件删除事件")
    class SendFileDeletedEventTests {

        @Test
        @DisplayName("成功（基于 FileEntity）：投递 FILE_DELETED 事件")
        void sendFileDeletedEvent_byEntity_success() {
            FileEntity entity = buildEntity();

            producer.sendFileDeletedEvent(entity, 1001L);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq("1001"), payloadCaptor.capture());

            FileEvent event = JSONUtil.toBean(payloadCaptor.getValue(), FileEvent.class);
            assertEquals(FileEventProducer.EVENT_TYPE_FILE_DELETED, event.getEventType());
            assertEquals(1001L, event.getFileId());
            assertEquals("sm3-fake", event.getFileSm3());
            assertEquals(1001L, event.getUserId());
        }

        @Test
        @DisplayName("成功（基于 fileId）：投递 FILE_DELETED 事件，仅含 fileId")
        void sendFileDeletedEvent_byFileId_success() {
            producer.sendFileDeletedEvent(999L, 1001L);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(eq(TOPIC), eq("999"), payloadCaptor.capture());

            FileEvent event = JSONUtil.toBean(payloadCaptor.getValue(), FileEvent.class);
            assertEquals(FileEventProducer.EVENT_TYPE_FILE_DELETED, event.getEventType());
            assertEquals(999L, event.getFileId());
            assertEquals(1001L, event.getUserId());
            assertNull(event.getFileSm3());
            assertNull(event.getFileName());
        }

        @Test
        @DisplayName("fileEntity 为 null：跳过发送")
        void sendFileDeletedEvent_nullEntity_skips() {
            producer.sendFileDeletedEvent((FileEntity) null, 1001L);
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("fileId 为 null：跳过发送")
        void sendFileDeletedEvent_nullFileId_skips() {
            producer.sendFileDeletedEvent((Long) null, 1001L);
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("fileEntity fileId 为 null：跳过发送")
        void sendFileDeletedEntity_nullFileId_skips() {
            FileEntity entity = new FileEntity();
            entity.setId(null);
            producer.sendFileDeletedEvent(entity, 1001L);
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("Kafka 发送异常：不传播，仅记录日志")
        void sendFileDeletedEvent_kafkaException_notPropagated() {
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Kafka 不可用"));
            FileEntity entity = buildEntity();

            assertDoesNotThrow(() -> producer.sendFileDeletedEvent(entity, 1001L));
            assertDoesNotThrow(() -> producer.sendFileDeletedEvent(999L, 1001L));
        }
    }
}
