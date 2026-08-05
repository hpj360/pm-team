package com.redteam.parse.producer;

import cn.hutool.json.JSONUtil;
import com.redteam.common.entity.FileTagEntity;
import com.redteam.common.mapper.FileTagMapper;
import com.redteam.parse.dto.NerEntityVO;
import com.redteam.parse.dto.ParseResultDTO;
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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文件解析完成事件生产者单元测试（V4.7-P0-3）
 *
 * <p>覆盖正常发送、参数校验、文本截断、标签查询降级、发送失败容错等场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileParsedEventProducerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private FileTagMapper fileTagMapper;

    @InjectMocks
    private FileParsedEventProducer producer;

    @BeforeEach
    void setUp() {
        // 注入 topic 配置（@Value 字段）
        ReflectionTestUtils.setField(producer, "fileParsedTopic", "file.parsed");
    }

    /**
     * 构造成功解析结果
     */
    private ParseResultDTO buildResult(Long fileId, String text) {
        ParseResultDTO dto = new ParseResultDTO();
        dto.setFileId(fileId);
        dto.setFileName("test.pdf");
        dto.setFileType("PDF");
        dto.setTextContent(text);
        dto.setParseStatus("SUCCESS");
        return dto;
    }

    @Test
    @DisplayName("sendFileParsedEvent: null 入参跳过发送")
    void sendFileParsedEvent_nullResult_skips() {
        producer.sendFileParsedEvent(null);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendFileParsedEvent: fileId 为空跳过发送")
    void sendFileParsedEvent_nullFileId_skips() {
        ParseResultDTO dto = buildResult(null, "content");
        producer.sendFileParsedEvent(dto);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("sendFileParsedEvent: 正常发送事件，payload 含全字段")
    void sendFileParsedEvent_success_sendsFullPayload() {
        Long fileId = 1001L;
        ParseResultDTO dto = buildResult(fileId, "文件内容");
        dto.setNerEntities(List.of(buildNerVO()));

        FileTagEntity tag = new FileTagEntity();
        tag.setTagCode("L3.ENTITY.IP.PUBLIC");
        when(fileTagMapper.selectByFileId(fileId)).thenReturn(List.of(tag));

        producer.sendFileParsedEvent(dto);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();
        var json = JSONUtil.parseObj(payload);
        assertEquals(fileId, json.getLong("fileId"));
        assertEquals("test.pdf", json.getStr("fileName"));
        assertEquals("PDF", json.getStr("fileType"));
        assertEquals("文件内容", json.getStr("fileText"));
        assertNotNull(json.getJSONArray("nerEntities"));
        assertEquals(1, json.getJSONArray("nerEntities").size());
        assertEquals("L3.ENTITY.IP.PUBLIC", json.getJSONArray("tags").get(0));
        assertNotNull(json.getLong("parsedAt"));
    }

    @Test
    @DisplayName("sendFileParsedEvent: 超长文本截断到 8000 字符")
    void sendFileParsedEvent_longText_truncated() {
        // 构造 10000 字符的文本
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("a");
        }
        ParseResultDTO dto = buildResult(2L, sb.toString());

        producer.sendFileParsedEvent(dto);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
        var json = JSONUtil.parseObj(payloadCaptor.getValue());
        String fileText = json.getStr("fileText");
        assertEquals(8000, fileText.length());
    }

    @Test
    @DisplayName("sendFileParsedEvent: 标签查询失败仍发送事件（tags 为空列表）")
    void sendFileParsedEvent_tagsQueryFails_sendsEmptyTags() {
        Long fileId = 3L;
        ParseResultDTO dto = buildResult(fileId, "content");
        when(fileTagMapper.selectByFileId(fileId)).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> producer.sendFileParsedEvent(dto));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
        var json = JSONUtil.parseObj(payloadCaptor.getValue());
        assertTrue(json.getJSONArray("tags").isEmpty());
    }

    @Test
    @DisplayName("sendFileParsedEvent: 空标签与空 NER 实体正常发送")
    void sendFileParsedEvent_emptyTagsAndNer_sendsSuccessfully() {
        Long fileId = 4L;
        ParseResultDTO dto = buildResult(fileId, "content");
        dto.setNerEntities(Collections.emptyList());
        when(fileTagMapper.selectByFileId(fileId)).thenReturn(Collections.emptyList());

        producer.sendFileParsedEvent(dto);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), anyString(), payloadCaptor.capture());
        var json = JSONUtil.parseObj(payloadCaptor.getValue());
        assertTrue(json.getJSONArray("nerEntities").isEmpty());
        assertTrue(json.getJSONArray("tags").isEmpty());
    }

    @Test
    @DisplayName("sendFileParsedEvent: Kafka 发送抛异常时不传播，仅记日志")
    void sendFileParsedEvent_kafkaThrows_noException() {
        Long fileId = 5L;
        ParseResultDTO dto = buildResult(fileId, "content");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("kafka unavailable"));

        // 不抛异常，保证主解析流程不受影响
        assertDoesNotThrow(() -> producer.sendFileParsedEvent(dto));
    }

    /**
     * 构造 NER 实体 VO
     */
    private NerEntityVO buildNerVO() {
        NerEntityVO vo = new NerEntityVO();
        vo.setEntityText("192.168.1.1");
        vo.setEntityType("IP");
        vo.setConfidence(0.95f);
        return vo;
    }
}
