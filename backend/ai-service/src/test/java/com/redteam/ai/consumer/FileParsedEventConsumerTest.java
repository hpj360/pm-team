package com.redteam.ai.consumer;

import com.redteam.ai.service.ThreatSummaryService;
import com.redteam.common.api.dto.NerEntityVO;
import com.redteam.common.entity.ThreatSummaryEntity;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文件解析完成事件消费者单元测试（V4.7-P0-3）
 *
 * <p>覆盖正常消费、空消息、非法 JSON、缺失 fileId、幂等跳过、生成失败抛异常等场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileParsedEventConsumerTest {

    @Mock
    private ThreatSummaryService threatSummaryService;

    @InjectMocks
    private FileParsedEventConsumer consumer;

    /**
     * 构造 Kafka 消息记录
     */
    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("file.parsed", 0, 0L, "key", value);
    }

    /**
     * 构造文件解析完成事件 JSON
     */
    private String buildEventJson(Long fileId, String nerEntitiesJson) {
        return "{"
                + "\"eventId\":\"evt-1\","
                + "\"fileId\":" + fileId + ","
                + "\"fileName\":\"test.pdf\","
                + "\"fileType\":\"PDF\","
                + "\"fileText\":\"文件内容\","
                + "\"nerEntities\":" + nerEntitiesJson + ","
                + "\"tags\":[\"L3.ENTITY.IP.PUBLIC\"],"
                + "\"parsedAt\":1700000000000"
                + "}";
    }

    @Test
    @DisplayName("onFileParsed: 空消息跳过，不调用生成")
    void onFileParsed_nullValue_skips() {
        consumer.onFileParsed(record(""));
        consumer.onFileParsed(record(null));
        verify(threatSummaryService, never()).generateSummary(
                anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("onFileParsed: 非法 JSON 跳过，不调用生成")
    void onFileParsed_invalidJson_skips() {
        consumer.onFileParsed(record("not-a-json"));
        verify(threatSummaryService, never()).generateSummary(
                anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("onFileParsed: 缺失 fileId 跳过，不调用生成")
    void onFileParsed_nullFileId_skips() {
        String json = "{\"eventId\":\"e\",\"fileName\":\"x.pdf\"}";
        consumer.onFileParsed(record(json));
        verify(threatSummaryService, never()).generateSummary(
                anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("onFileParsed: 已存在成功摘要(status=1)幂等跳过")
    void onFileParsed_alreadyGenerated_skips() {
        Long fileId = 10L;
        ThreatSummaryEntity existing = new ThreatSummaryEntity();
        existing.setFileId(fileId);
        existing.setStatus(1);
        when(threatSummaryService.getByFileId(fileId)).thenReturn(existing);

        consumer.onFileParsed(record(buildEventJson(fileId, "[]")));

        verify(threatSummaryService, never()).generateSummary(
                anyLong(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("onFileParsed: 未生成摘要时调用 generateSummary，参数正确透传")
    void onFileParsed_notGenerated_callsGenerateSummary() {
        Long fileId = 20L;
        when(threatSummaryService.getByFileId(fileId)).thenReturn(null);
        ThreatSummaryEntity result = new ThreatSummaryEntity();
        result.setFileId(fileId);
        result.setStatus(1);
        when(threatSummaryService.generateSummary(
                eq(fileId), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(result);

        String nerJson = "[{\"entityText\":\"1.2.3.4\",\"entityType\":\"IP\",\"confidence\":0.9}]";
        consumer.onFileParsed(record(buildEventJson(fileId, nerJson)));

        verify(threatSummaryService).generateSummary(
                eq(fileId), eq("文件内容"), eq("test.pdf"), eq("PDF"), any(), any());
    }

    @Test
    @DisplayName("onFileParsed: generateSummary 抛异常时传播 RuntimeException（触发重试+死信）")
    void onFileParsed_generateSummaryThrows_propagatesException() {
        Long fileId = 30L;
        when(threatSummaryService.getByFileId(fileId)).thenReturn(null);
        when(threatSummaryService.generateSummary(
                anyLong(), anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("LLM 不可用"));

        assertThrows(RuntimeException.class,
                () -> consumer.onFileParsed(record(buildEventJson(fileId, "[]"))));
    }

    @Test
    @DisplayName("onFileParsed: getByFileId 抛异常时按未生成处理，继续生成")
    void onFileParsed_getByFileIdThrows_treatsAsNotGenerated() {
        Long fileId = 40L;
        when(threatSummaryService.getByFileId(fileId)).thenThrow(new RuntimeException("db down"));
        ThreatSummaryEntity result = new ThreatSummaryEntity();
        result.setStatus(1);
        when(threatSummaryService.generateSummary(
                anyLong(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(result);

        assertDoesNotThrow(() -> consumer.onFileParsed(record(buildEventJson(fileId, "[]"))));
        verify(threatSummaryService).generateSummary(
                eq(fileId), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("onFileParsed: NER 实体列表正确解析并透传")
    void onFileParsed_nerEntitiesParsedAndPassed() {
        Long fileId = 50L;
        when(threatSummaryService.getByFileId(fileId)).thenReturn(null);
        when(threatSummaryService.generateSummary(
                anyLong(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new ThreatSummaryEntity());

        String nerJson = "[{\"entityText\":\"cve-2024-1\",\"entityType\":\"CVE\"},"
                + "{\"entityText\":\"evil.com\",\"entityType\":\"DOMAIN\"}]";
        consumer.onFileParsed(record(buildEventJson(fileId, nerJson)));

        org.mockito.ArgumentCaptor<List<NerEntityVO>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(threatSummaryService).generateSummary(
                anyLong(), anyString(), anyString(), anyString(), captor.capture(), any());
        assertEquals(2, captor.getValue().size());
    }

    /**
     * 断言列表大小（避免额外 import）
     */
    private static void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
