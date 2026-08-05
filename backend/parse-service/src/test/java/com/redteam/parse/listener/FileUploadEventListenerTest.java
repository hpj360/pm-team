package com.redteam.parse.listener;

import cn.hutool.json.JSONUtil;
import com.redteam.parse.service.FileParseService;
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
import org.springframework.kafka.support.Acknowledgment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 文件上传事件监听器单元测试
 *
 * <p>覆盖 FILE_UPLOADED 事件触发异步解析、其他事件跳过、空消息、
 * 缺字段、异常处理与 ack 行为。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileUploadEventListenerTest {

    @Mock
    private FileParseService fileParseService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private FileUploadEventListener listener;

    /**
     * 构造 Kafka 消息记录
     *
     * @param value 消息体
     * @return ConsumerRecord
     */
    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("redteam.file.events", 0, 0L, "key", value);
    }

    /**
     * 构造 FILE_UPLOADED 事件 JSON
     */
    private String buildEventJson(String eventType, Long fileId, String storagePath,
                                  String fileName, String fileType, Long fileSize) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "uuid-1234");
        event.put("eventType", eventType);
        event.put("fileId", fileId);
        event.put("fileSm3", "abc");
        event.put("fileName", fileName);
        event.put("fileType", fileType);
        event.put("fileSize", fileSize);
        event.put("storagePath", storagePath);
        event.put("targetId", 456);
        event.put("userId", 789);
        event.put("timestamp", System.currentTimeMillis());
        return JSONUtil.toJsonStr(event);
    }

    @Test
    @DisplayName("onFileEvent: FILE_UPLOADED 触发异步解析")
    void onFileEvent_fileUploaded_triggersAsyncParse() {
        String json = buildEventJson("FILE_UPLOADED", 1L, "path/file.pdf", "file.pdf", "pdf", 1000L);

        listener.onFileEvent(record(json), acknowledgment);

        verify(fileParseService).parseFileAsync(eq(1L), eq("path/file.pdf"),
                eq("file.pdf"), eq("pdf"), eq(1000L));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileEvent: 非 FILE_UPLOADED 事件跳过")
    void onFileEvent_otherEventType_skips() {
        String json = buildEventJson("FILE_DELETED", 1L, "path/file.pdf", "file.pdf", "pdf", 1000L);

        listener.onFileEvent(record(json), acknowledgment);

        verify(fileParseService, never()).parseFileAsync(anyLong(), anyString(),
                anyString(), anyString(), anyLong());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileEvent: 空消息跳过")
    void onFileEvent_blankMessage_skips() {
        listener.onFileEvent(record(""), acknowledgment);
        verify(fileParseService, never()).parseFileAsync(anyLong(), anyString(),
                anyString(), anyString(), anyLong());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileEvent: null 消息跳过")
    void onFileEvent_nullMessage_skips() {
        listener.onFileEvent(record(null), acknowledgment);
        verify(fileParseService, never()).parseFileAsync(anyLong(), anyString(),
                anyString(), anyString(), anyLong());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileEvent: fileId 缺失跳过")
    void onFileEvent_missingFileId_skips() {
        String json = buildEventJson("FILE_UPLOADED", null, "path/file.pdf", "file.pdf", "pdf", 1000L);

        listener.onFileEvent(record(json), acknowledgment);

        verify(fileParseService, never()).parseFileAsync(anyLong(), anyString(),
                anyString(), anyString(), anyLong());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileEvent: storagePath 缺失跳过")
    void onFileEvent_missingStoragePath_skips() {
        String json = buildEventJson("FILE_UPLOADED", 1L, null, "file.pdf", "pdf", 1000L);

        listener.onFileEvent(record(json), acknowledgment);

        verify(fileParseService, never()).parseFileAsync(anyLong(), anyString(),
                anyString(), anyString(), anyLong());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileEvent: 异步解析抛异常仍 ack 不阻塞分区")
    void onFileEvent_parseThrowsException_stillAcks() {
        String json = buildEventJson("FILE_UPLOADED", 1L, "path/file.pdf", "file.pdf", "pdf", 1000L);
        doThrow(new RuntimeException("async parse fail"))
                .when(fileParseService).parseFileAsync(anyLong(), anyString(), anyString(), anyString(), anyLong());

        assertDoesNotThrow(() -> listener.onFileEvent(record(json), acknowledgment));

        verify(fileParseService).parseFileAsync(anyLong(), anyString(), anyString(), anyString(), anyLong());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileEvent: 异常 JSON 仍 ack 不阻塞")
    void onFileEvent_malformedJson_stillAcks() {
        assertDoesNotThrow(() -> listener.onFileEvent(record("not a json"), acknowledgment));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileEvent: 事件参数透传完整")
    void onFileEvent_paramsPassedCorrectly() {
        String json = buildEventJson("FILE_UPLOADED", 999L, "2026/07/27/x.pdf", "x.pdf", "pdf", 2048L);

        listener.onFileEvent(record(json), acknowledgment);

        ArgumentCaptor<Long> fileIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> sizeCaptor = ArgumentCaptor.forClass(Long.class);
        verify(fileParseService).parseFileAsync(fileIdCaptor.capture(), pathCaptor.capture(),
                nameCaptor.capture(), typeCaptor.capture(), sizeCaptor.capture());

        assertEquals(999L, fileIdCaptor.getValue());
        assertEquals("2026/07/27/x.pdf", pathCaptor.getValue());
        assertEquals("x.pdf", nameCaptor.getValue());
        assertEquals("pdf", typeCaptor.getValue());
        assertEquals(2048L, sizeCaptor.getValue());
    }
}
