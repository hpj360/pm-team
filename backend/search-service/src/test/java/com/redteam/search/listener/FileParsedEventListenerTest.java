package com.redteam.search.listener;

import cn.hutool.json.JSONUtil;
import com.redteam.search.dto.FileIndexDTO;
import com.redteam.search.entity.SearchIndexTaskEntity;
import com.redteam.search.mapper.SearchIndexTaskMapper;
import com.redteam.search.service.FileSearchService;
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
 * 文件解析事件监听器单元测试
 *
 * <p>覆盖 file.parsed 事件触发索引、幂等跳过、空消息、缺字段、重试、ack 行为。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileParsedEventListenerTest {

    @Mock
    private FileSearchService fileSearchService;

    @Mock
    private SearchIndexTaskMapper searchIndexTaskMapper;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private FileParsedEventListener listener;

    /**
     * 构造 Kafka 消息记录
     *
     * @param value 消息体
     * @return ConsumerRecord
     */
    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("redteam.parse.events", 0, 0L, "key", value);
    }

    /**
     * 构造 file.parsed 事件 JSON
     */
    private String buildEventJson(String eventType, Long fileId, String fileName, String fileType) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "uuid-1234");
        event.put("eventType", eventType);
        event.put("fileId", fileId);
        event.put("fileName", fileName);
        event.put("fileType", fileType);
        event.put("parseStatus", "SUCCESS");
        event.put("timestamp", System.currentTimeMillis());
        return JSONUtil.toJsonStr(event);
    }

    @Test
    @DisplayName("onFileParsed: file.parsed 触发索引创建")
    void onFileParsed_fileParsedTriggersIndex() {
        String json = buildEventJson("file.parsed", 1L, "file.pdf", "pdf");
        when(searchIndexTaskMapper.selectOne(any())).thenReturn(null);

        listener.onFileParsed(record(json), acknowledgment);

        ArgumentCaptor<FileIndexDTO> captor = ArgumentCaptor.forClass(FileIndexDTO.class);
        verify(fileSearchService).indexFile(captor.capture());
        assertEquals(1L, captor.getValue().getFileId());
        assertEquals("file.pdf", captor.getValue().getFileName());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: 非 file.parsed 事件跳过")
    void onFileParsed_otherEventTypeSkips() {
        String json = buildEventJson("file.parse_failed", 1L, "file.pdf", "pdf");

        listener.onFileParsed(record(json), acknowledgment);

        verify(fileSearchService, never()).indexFile(any(FileIndexDTO.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: 空消息跳过")
    void onFileParsed_blankMessageSkips() {
        listener.onFileParsed(record(""), acknowledgment);
        verify(fileSearchService, never()).indexFile(any(FileIndexDTO.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: null 消息跳过")
    void onFileParsed_nullMessageSkips() {
        listener.onFileParsed(record(null), acknowledgment);
        verify(fileSearchService, never()).indexFile(any(FileIndexDTO.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: fileId 缺失跳过")
    void onFileParsed_missingFileIdSkips() {
        String json = buildEventJson("file.parsed", null, "file.pdf", "pdf");
        listener.onFileParsed(record(json), acknowledgment);
        verify(fileSearchService, never()).indexFile(any(FileIndexDTO.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: 已索引成功的文件跳过（幂等）")
    void onFileParsed_alreadyIndexedSkips() {
        String json = buildEventJson("file.parsed", 1L, "file.pdf", "pdf");
        SearchIndexTaskEntity task = new SearchIndexTaskEntity();
        task.setFileId(1L);
        task.setIndexStatus(SearchIndexTaskEntity.STATUS_SUCCESS);
        task.setEsIndexed(true);
        task.setMilvusIndexed(true);
        when(searchIndexTaskMapper.selectOne(any())).thenReturn(task);

        listener.onFileParsed(record(json), acknowledgment);

        verify(fileSearchService, never()).indexFile(any(FileIndexDTO.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: 部分索引未完成时仍触发重新索引")
    void onFileParsed_partialIndexedRetriggers() {
        String json = buildEventJson("file.parsed", 1L, "file.pdf", "pdf");
        SearchIndexTaskEntity task = new SearchIndexTaskEntity();
        task.setFileId(1L);
        task.setIndexStatus(SearchIndexTaskEntity.STATUS_SUCCESS);
        task.setEsIndexed(true);
        task.setMilvusIndexed(false);
        when(searchIndexTaskMapper.selectOne(any())).thenReturn(task);

        listener.onFileParsed(record(json), acknowledgment);

        verify(fileSearchService).indexFile(any(FileIndexDTO.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: 索引失败时重试最多 3 次")
    void onFileParsed_retryOnFailure() {
        String json = buildEventJson("file.parsed", 1L, "file.pdf", "pdf");
        when(searchIndexTaskMapper.selectOne(any())).thenReturn(null);
        doThrow(new RuntimeException("索引失败"))
                .when(fileSearchService).indexFile(any(FileIndexDTO.class));

        listener.onFileParsed(record(json), acknowledgment);

        // 应重试 3 次
        verify(fileSearchService, times(3)).indexFile(any(FileIndexDTO.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: 第一次成功不重试")
    void onFileParsed_successNoRetry() {
        String json = buildEventJson("file.parsed", 1L, "file.pdf", "pdf");
        when(searchIndexTaskMapper.selectOne(any())).thenReturn(null);

        listener.onFileParsed(record(json), acknowledgment);

        verify(fileSearchService, times(1)).indexFile(any(FileIndexDTO.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: 异常 JSON 仍 ack 不阻塞")
    void onFileParsed_malformedJsonStillAcks() {
        assertDoesNotThrow(() -> listener.onFileParsed(record("not a json"), acknowledgment));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onFileParsed: 处理过程抛异常仍 ack")
    void onFileParsed_exceptionStillAcks() {
        String json = buildEventJson("file.parsed", 1L, "file.pdf", "pdf");
        when(searchIndexTaskMapper.selectOne(any())).thenThrow(new RuntimeException("DB 错误"));

        assertDoesNotThrow(() -> listener.onFileParsed(record(json), acknowledgment));
        verify(acknowledgment).acknowledge();
    }
}
