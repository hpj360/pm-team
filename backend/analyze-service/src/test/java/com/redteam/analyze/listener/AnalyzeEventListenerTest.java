package com.redteam.analyze.listener;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.analyze.service.FileAnalyzeService;
import com.redteam.common.api.dto.FileAnalyzeDTO;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 分析事件监听器单元测试
 *
 * <p>覆盖文件解析事件、分析请求事件的消费与幂等控制。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnalyzeEventListenerTest {

    @Mock
    private FileAnalyzeService fileAnalyzeService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AnalyzeEventListener listener;

    /**
     * 构造 Kafka ConsumerRecord
     */
    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("redteam.parse.events", 0, 0L, "key", value);
    }

    /**
     * 构造 file.parsed 事件 JSON
     */
    private String fileParsedEvent(Long fileId, String filePath) {
        JSONObject json = new JSONObject();
        json.set("eventType", "file.parsed");
        if (fileId != null) {
            json.set("fileId", fileId);
        }
        if (filePath != null) {
            json.set("filePath", filePath);
        }
        return json.toString();
    }

    /**
     * 构造 analyze.request 事件 JSON
     */
    private String analyzeRequestEvent(Long taskId, Long fileId) {
        JSONObject json = new JSONObject();
        json.set("eventType", "analyze.request");
        if (taskId != null) {
            json.set("taskId", taskId);
        }
        if (fileId != null) {
            json.set("fileId", fileId);
        }
        return json.toString();
    }

    // ==================== listenFileParsed ====================

    @Test
    @DisplayName("listenFileParsed: 空消息跳过")
    void listenFileParsed_emptyMessage_skips() {
        listener.listenFileParsed(record(""));
        verifyNoInteractions(fileAnalyzeService);
    }

    @Test
    @DisplayName("listenFileParsed: 非 file.parsed 事件跳过")
    void listenFileParsed_nonFileParsedEvent_skips() {
        String json = "{\"eventType\":\"file.created\",\"fileId\":1}";
        listener.listenFileParsed(record(json));
        verifyNoInteractions(fileAnalyzeService);
    }

    @Test
    @DisplayName("listenFileParsed: 缺少 fileId 跳过")
    void listenFileParsed_missingFileId_skips() {
        String json = "{\"eventType\":\"file.parsed\"}";
        listener.listenFileParsed(record(json));
        verifyNoInteractions(fileAnalyzeService);
    }

    @Test
    @DisplayName("listenFileParsed: 首次处理触发异步分析")
    void listenFileParsed_firstTime_triggersAnalyze() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(fileAnalyzeService.analyzeAsync(any(FileAnalyzeDTO.class))).thenReturn(100L);

        listener.listenFileParsed(record(fileParsedEvent(1L, "/tmp/file.txt")));

        ArgumentCaptor<FileAnalyzeDTO> captor = ArgumentCaptor.forClass(FileAnalyzeDTO.class);
        verify(fileAnalyzeService).analyzeAsync(captor.capture());
        FileAnalyzeDTO dto = captor.getValue();
        assertEquals(1L, dto.getFileId());
        assertEquals("/tmp/file.txt", dto.getFilePath());
        assertEquals(5, dto.getAnalyzeType());
        assertTrue(dto.getGenerateEmbedding());
    }

    @Test
    @DisplayName("listenFileParsed: 已处理过的 fileId 跳过（幂等）")
    void listenFileParsed_alreadyProcessed_skips() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        listener.listenFileParsed(record(fileParsedEvent(1L, "/tmp/file.txt")));

        verify(fileAnalyzeService, never()).analyzeAsync(any());
    }

    @Test
    @DisplayName("listenFileParsed: filePath 为空仍触发分析")
    void listenFileParsed_nullFilePath_stillTriggers() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(fileAnalyzeService.analyzeAsync(any(FileAnalyzeDTO.class))).thenReturn(200L);

        listener.listenFileParsed(record(fileParsedEvent(1L, null)));

        verify(fileAnalyzeService).analyzeAsync(any(FileAnalyzeDTO.class));
    }

    @Test
    @DisplayName("listenFileParsed: 非法 JSON 不抛异常")
    void listenFileParsed_invalidJson_doesNotThrow() {
        assertDoesNotThrow(() -> listener.listenFileParsed(record("not a json {{{")));
        verifyNoInteractions(fileAnalyzeService);
    }

    @Test
    @DisplayName("listenFileParsed: service 抛异常不向上传播")
    void listenFileParsed_serviceThrows_notPropagated() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        doThrow(new RuntimeException("DB 不可用"))
                .when(fileAnalyzeService).analyzeAsync(any(FileAnalyzeDTO.class));

        assertDoesNotThrow(() -> listener.listenFileParsed(record(fileParsedEvent(1L, "/tmp/f.txt"))));
    }

    // ==================== listenAnalyzeEvent ====================

    @Test
    @DisplayName("listenAnalyzeEvent: 空消息跳过")
    void listenAnalyzeEvent_emptyMessage_skips() {
        listener.listenAnalyzeEvent(record(""));
        verifyNoInteractions(fileAnalyzeService);
    }

    @Test
    @DisplayName("listenAnalyzeEvent: 非 analyze.request 事件跳过")
    void listenAnalyzeEvent_nonRequestEvent_skips() {
        String json = "{\"eventType\":\"analyze.completed\",\"taskId\":1}";
        listener.listenAnalyzeEvent(record(json));
        verifyNoInteractions(fileAnalyzeService);
    }

    @Test
    @DisplayName("listenAnalyzeEvent: 缺少 taskId 跳过")
    void listenAnalyzeEvent_missingTaskId_skips() {
        String json = "{\"eventType\":\"analyze.request\"}";
        listener.listenAnalyzeEvent(record(json));
        verifyNoInteractions(fileAnalyzeService);
    }

    @Test
    @DisplayName("listenAnalyzeEvent: 首次处理触发任务执行")
    void listenAnalyzeEvent_firstTime_triggersProcess() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        doNothing().when(fileAnalyzeService).processAnalyzeTask(anyLong());

        listener.listenAnalyzeEvent(record(analyzeRequestEvent(100L, 1L)));

        verify(fileAnalyzeService).processAnalyzeTask(100L);
    }

    @Test
    @DisplayName("listenAnalyzeEvent: 已处理过的 taskId 跳过（幂等）")
    void listenAnalyzeEvent_alreadyProcessed_skips() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        listener.listenAnalyzeEvent(record(analyzeRequestEvent(100L, 1L)));

        verify(fileAnalyzeService, never()).processAnalyzeTask(anyLong());
    }

    @Test
    @DisplayName("listenAnalyzeEvent: 非法 JSON 不抛异常")
    void listenAnalyzeEvent_invalidJson_doesNotThrow() {
        assertDoesNotThrow(() -> listener.listenAnalyzeEvent(record("invalid json")));
        verifyNoInteractions(fileAnalyzeService);
    }

    @Test
    @DisplayName("listenAnalyzeEvent: service 抛异常不向上传播")
    void listenAnalyzeEvent_serviceThrows_notPropagated() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        doThrow(new RuntimeException("处理失败"))
                .when(fileAnalyzeService).processAnalyzeTask(anyLong());

        assertDoesNotThrow(() -> listener.listenAnalyzeEvent(record(analyzeRequestEvent(100L, 1L))));
    }

    // ==================== 静态方法测试 ====================

    @Test
    @DisplayName("buildFileParsedEvent: 包含必要字段")
    void buildFileParsedEvent_containsRequiredFields() {
        String json = AnalyzeEventListener.buildFileParsedEvent(1L, "/tmp/file.txt");
        JSONObject obj = JSONUtil.parseObj(json);
        assertEquals("file.parsed", obj.getStr("eventType"));
        assertEquals(1L, obj.getLong("fileId"));
        assertEquals("/tmp/file.txt", obj.getStr("filePath"));
        assertNotNull(obj.getStr("eventId"));
        assertNotNull(obj.getLong("timestamp"));
    }

    @Test
    @DisplayName("buildAnalyzeRequestEvent: 包含必要字段")
    void buildAnalyzeRequestEvent_containsRequiredFields() {
        String json = AnalyzeEventListener.buildAnalyzeRequestEvent(100L, 1L);
        JSONObject obj = JSONUtil.parseObj(json);
        assertEquals("analyze.request", obj.getStr("eventType"));
        assertEquals(100L, obj.getLong("taskId"));
        assertEquals(1L, obj.getLong("fileId"));
        assertNotNull(obj.getStr("eventId"));
        assertNotNull(obj.getLong("timestamp"));
    }
}
