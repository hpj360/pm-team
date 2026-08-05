package com.redteam.analyze.producer;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.redteam.analyze.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分析事件 Kafka 生产者
 *
 * <p>统一封装分析任务请求 / 完成 / 失败事件的投递逻辑。
 * 消息发送失败仅记录日志，不抛异常，避免影响主流程。</p>
 *
 * <p>事件 JSON 结构：</p>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "eventType": "analyze.request" / "analyze.completed" / "analyze.failed",
 *   "taskId": 123,
 *   "fileId": 456,
 *   "analyzeType": 5,
 *   "status": "SUCCESS" / "FAILED",
 *   "durationMs": 1234,
 *   "embeddingId": "...",
 *   "error": "...",
 *   "timestamp": 1700000000000
 * }
 * }</pre>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class AnalyzeEventProducer {

    /**
     * 事件类型：分析请求（异步任务触发）
     */
    public static final String EVENT_TYPE_ANALYZE_REQUEST = "analyze.request";

    /**
     * 事件类型：分析完成
     */
    public static final String EVENT_TYPE_ANALYZE_COMPLETED = "analyze.completed";

    /**
     * 事件类型：分析失败
     */
    public static final String EVENT_TYPE_ANALYZE_FAILED = "analyze.failed";

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final KafkaConfig.Topic topic;

    /**
     * 构造方法：注入分析事件专用 KafkaTemplate
     *
     * @param kafkaTemplate KafkaTemplate
     * @param kafkaConfig   Kafka 配置
     */
    public AnalyzeEventProducer(@Qualifier("analyzeEventKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                                KafkaConfig kafkaConfig) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = kafkaConfig.getTopic();
    }

    /**
     * 发送分析请求事件（触发异步分析）
     *
     * @param taskId      分析任务ID
     * @param fileId      文件ID
     * @param analyzeType 分析类型
     */
    public void sendAnalyzeRequestEvent(Long taskId, Long fileId, Integer analyzeType) {
        if (taskId == null) {
            log.warn("分析请求事件 taskId 为空，跳过发送");
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", IdUtil.fastSimpleUUID());
        event.put("eventType", EVENT_TYPE_ANALYZE_REQUEST);
        event.put("taskId", taskId);
        event.put("fileId", fileId);
        event.put("analyzeType", analyzeType);
        event.put("timestamp", System.currentTimeMillis());
        send(event);
    }

    /**
     * 发送分析完成事件
     *
     * @param taskId      分析任务ID
     * @param fileId      文件ID
     * @param durationMs  分析耗时（毫秒）
     * @param embeddingId 向量嵌入ID（可选）
     */
    public void sendAnalyzeCompletedEvent(Long taskId, Long fileId, Long durationMs, String embeddingId) {
        if (taskId == null) {
            log.warn("分析完成事件 taskId 为空，跳过发送");
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", IdUtil.fastSimpleUUID());
        event.put("eventType", EVENT_TYPE_ANALYZE_COMPLETED);
        event.put("taskId", taskId);
        event.put("fileId", fileId);
        event.put("status", "SUCCESS");
        event.put("durationMs", durationMs);
        if (embeddingId != null) {
            event.put("embeddingId", embeddingId);
        }
        event.put("timestamp", System.currentTimeMillis());
        send(event);
    }

    /**
     * 发送分析失败事件
     *
     * @param taskId 分析任务ID
     * @param fileId 文件ID
     * @param error  错误信息
     */
    public void sendAnalyzeFailedEvent(Long taskId, Long fileId, String error) {
        if (taskId == null) {
            log.warn("分析失败事件 taskId 为空，跳过发送");
            return;
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", IdUtil.fastSimpleUUID());
        event.put("eventType", EVENT_TYPE_ANALYZE_FAILED);
        event.put("taskId", taskId);
        event.put("fileId", fileId);
        event.put("status", "FAILED");
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
            String key = String.valueOf(event.get("taskId"));
            String topicName = topic.getAnalyzeEvents();
            kafkaTemplate.send(topicName, key, payload);
            log.info("分析事件已投递: topic={}, eventId={}, type={}, taskId={}",
                    topicName, event.get("eventId"), event.get("eventType"), event.get("taskId"));
        } catch (Exception e) {
            log.error("分析事件投递失败: eventId={}, type={}, taskId={}",
                    event.get("eventId"), event.get("eventType"), event.get("taskId"), e);
        }
    }
}
