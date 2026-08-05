package com.redteam.parse.producer;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.redteam.parse.dto.ParseResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析事件 Kafka 生产者
 *
 * <p>统一封装文件解析完成 / 解析失败事件的投递逻辑。
 * 消息发送失败仅记录日志，不抛异常，避免影响主解析流程。</p>
 *
 * <p>事件 JSON 结构：</p>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "eventType": "file.parsed" / "file.parse_failed",
 *   "fileId": 123,
 *   "fileName": "...",
 *   "fileType": "...",
 *   "parseStatus": "SUCCESS" / "FAILED",
 *   "parseDurationMs": 1234,
 *   "textHash": "...",
 *   "yaraMatchCount": 2,
 *   "nerEntityCount": 5,
 *   "error": "...",
 *   "timestamp": 1700000000000
 * }
 * }</pre>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class ParseEventProducer {

    /**
     * 事件类型：文件解析完成
     */
    public static final String EVENT_TYPE_FILE_PARSED = "file.parsed";

    /**
     * 事件类型：文件解析失败
     */
    public static final String EVENT_TYPE_FILE_PARSE_FAILED = "file.parse_failed";

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 解析事件主题
     */
    @Value("${redteam.kafka.topic.parse-events:redteam.parse.events}")
    private String parseEventsTopic;

    /**
     * 构造方法：注入解析事件专用 KafkaTemplate
     *
     * @param kafkaTemplate KafkaTemplate
     */
    public ParseEventProducer(@Qualifier("parseEventKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 发送文件解析完成事件
     *
     * @param parseResult 解析结果
     */
    public void sendFileParsedEvent(ParseResultDTO parseResult) {
        if (parseResult == null || parseResult.getFileId() == null) {
            log.warn("解析完成事件参数非法，跳过发送: parseResult={}", parseResult);
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", IdUtil.fastSimpleUUID());
        event.put("eventType", EVENT_TYPE_FILE_PARSED);
        event.put("fileId", parseResult.getFileId());
        event.put("fileName", parseResult.getFileName());
        event.put("fileType", parseResult.getFileType());
        event.put("parseStatus", parseResult.getParseStatus());
        event.put("parseDurationMs", parseResult.getParseDurationMs());
        event.put("textHash", parseResult.getTextHash());
        event.put("textLength", parseResult.getTextLength());
        event.put("language", parseResult.getLanguage());
        event.put("yaraMatchCount",
                parseResult.getYaraMatches() == null ? 0 : parseResult.getYaraMatches().size());
        event.put("nerEntityCount",
                parseResult.getNerEntities() == null ? 0 : parseResult.getNerEntities().size());
        event.put("timestamp", System.currentTimeMillis());
        send(event);
    }

    /**
     * 发送文件解析失败事件
     *
     * @param fileId 文件ID
     * @param error  错误信息
     */
    public void sendFileParseFailedEvent(Long fileId, String error) {
        if (fileId == null) {
            log.warn("解析失败事件 fileId 为空，跳过发送");
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", IdUtil.fastSimpleUUID());
        event.put("eventType", EVENT_TYPE_FILE_PARSE_FAILED);
        event.put("fileId", fileId);
        event.put("parseStatus", "FAILED");
        event.put("error", error);
        event.put("timestamp", System.currentTimeMillis());
        send(event);
    }

    /**
     * 投递事件到 Kafka
     *
     * <p>失败仅记录日志，不抛异常。</p>
     *
     * @param event 事件体
     */
    private void send(Map<String, Object> event) {
        try {
            String payload = JSONUtil.toJsonStr(event);
            String key = String.valueOf(event.get("fileId"));
            kafkaTemplate.send(parseEventsTopic, key, payload);
            log.info("解析事件已投递: topic={}, eventId={}, type={}, fileId={}",
                    parseEventsTopic, event.get("eventId"), event.get("eventType"), event.get("fileId"));
        } catch (Exception e) {
            log.error("解析事件投递失败: topic={}, eventId={}, type={}, fileId={}",
                    parseEventsTopic, event.get("eventId"), event.get("eventType"), event.get("fileId"), e);
        }
    }
}
