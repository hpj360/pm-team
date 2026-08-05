package com.redteam.analyze.listener;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.analyze.service.FileAnalyzeService;
import com.redteam.common.api.dto.FileAnalyzeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 分析事件监听器
 *
 * <p>订阅两个主题：</p>
 * <ul>
 *   <li>{@code redteam.parse.events}：消费 {@code file.parsed} 事件，触发自动分析（全文分析 + 向量嵌入）。</li>
 *   <li>{@code redteam.analyze.events}：消费 {@code analyze.request} 事件，执行异步分析任务处理。</li>
 * </ul>
 *
 * <p>幂等策略：基于 Redis SET NX 实现文件级与任务级去重，重复事件直接跳过。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeEventListener {

    /**
     * 事件类型：文件解析完成
     */
    public static final String EVENT_TYPE_FILE_PARSED = "file.parsed";

    /**
     * 事件类型：分析请求
     */
    public static final String EVENT_TYPE_ANALYZE_REQUEST = "analyze.request";

    /**
     * 文件解析幂等 Redis Key 前缀
     */
    private static final String FILE_PARDED_IDEMPOTENT_KEY = "analyze:file-parsed:";

    /**
     * 任务处理幂等 Redis Key 前缀
     */
    private static final String TASK_IDEMPOTENT_KEY = "analyze:task:";

    /**
     * 幂等 Key TTL（秒）
     */
    private static final long IDEMPOTENT_TTL_SECONDS = 86400L;

    /**
     * 全文分析类型
     */
    private static final int ANALYZE_TYPE_FULL = 5;

    private final FileAnalyzeService fileAnalyzeService;

    private final StringRedisTemplate redisTemplate;

    /**
     * 监听文件解析事件（file.parsed）
     *
     * <p>解析完成后自动触发全文分析 + 向量嵌入。</p>
     *
     * @param record Kafka 消息记录
     */
    @KafkaListener(topics = "${redteam.kafka.topic.parse-events:redteam.parse.events}",
            groupId = "${spring.kafka.consumer.group-id:analyze-service-group}")
    public void listenFileParsed(ConsumerRecord<String, String> record) {
        String value = record.value();
        if (StrUtil.isBlank(value)) {
            log.warn("收到空消息，跳过: topic={}, offset={}", record.topic(), record.offset());
            return;
        }
        log.info("收到文件解析事件: topic={}, offset={}", record.topic(), record.offset());

        try {
            JSONObject json = JSONUtil.parseObj(value);
            String eventType = json.getStr("eventType");
            if (!EVENT_TYPE_FILE_PARSED.equals(eventType)) {
                log.debug("非 file.parsed 事件，跳过: eventType={}", eventType);
                return;
            }

            Long fileId = json.getLong("fileId");
            if (fileId == null) {
                log.warn("file.parsed 事件缺少 fileId，跳过");
                return;
            }

            // 幂等：同一 fileId 只触发一次自动分析
            String idempotentKey = FILE_PARDED_IDEMPOTENT_KEY + fileId;
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(acquired)) {
                log.info("file.parsed 事件已处理过，跳过: fileId={}", fileId);
                return;
            }

            String filePath = json.getStr("filePath");
            FileAnalyzeDTO dto = new FileAnalyzeDTO();
            dto.setFileId(fileId);
            dto.setFilePath(filePath);
            dto.setAnalyzeType(ANALYZE_TYPE_FULL);
            dto.setGenerateEmbedding(Boolean.TRUE);

            Long taskId = fileAnalyzeService.analyzeAsync(dto);
            log.info("file.parsed 触发异步分析: fileId={}, taskId={}", fileId, taskId);
        } catch (Exception e) {
            log.error("file.parsed 事件处理失败: topic={}, offset={}", record.topic(), record.offset(), e);
        }
    }

    /**
     * 监听分析事件（analyze.request / analyze.completed / analyze.failed）
     *
     * <p>仅处理 {@code analyze.request}，触发任务执行。</p>
     *
     * @param record Kafka 消息记录
     */
    @KafkaListener(topics = "${redteam.kafka.topic.analyze-events:redteam.analyze.events}",
            groupId = "${spring.kafka.consumer.group-id:analyze-service-group}")
    public void listenAnalyzeEvent(ConsumerRecord<String, String> record) {
        String value = record.value();
        if (StrUtil.isBlank(value)) {
            log.warn("收到空消息，跳过: topic={}, offset={}", record.topic(), record.offset());
            return;
        }
        log.info("收到分析事件: topic={}, offset={}", record.topic(), record.offset());

        try {
            JSONObject json = JSONUtil.parseObj(value);
            String eventType = json.getStr("eventType");
            if (!EVENT_TYPE_ANALYZE_REQUEST.equals(eventType)) {
                log.debug("非 analyze.request 事件，跳过: eventType={}", eventType);
                return;
            }

            Long taskId = json.getLong("taskId");
            if (taskId == null) {
                log.warn("analyze.request 事件缺少 taskId，跳过");
                return;
            }

            // 幂等：同一 taskId 只处理一次
            String idempotentKey = TASK_IDEMPOTENT_KEY + taskId;
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(idempotentKey, "1", IDEMPOTENT_TTL_SECONDS, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(acquired)) {
                log.info("analyze.request 事件已处理过，跳过: taskId={}", taskId);
                return;
            }

            fileAnalyzeService.processAnalyzeTask(taskId);
            log.info("analyze.request 处理完成: taskId={}", taskId);
        } catch (Exception e) {
            log.error("analyze.request 事件处理失败: topic={}, offset={}", record.topic(), record.offset(), e);
        }
    }

    // ==================== 测试可见方法 ====================

    /**
     * 测试用：构造文件解析事件 JSON
     *
     * @param fileId   文件ID
     * @param filePath 文件路径
     * @return 事件 JSON
     */
    public static String buildFileParsedEvent(Long fileId, String filePath) {
        JSONObject json = new JSONObject();
        json.set("eventId", cn.hutool.core.util.IdUtil.fastSimpleUUID());
        json.set("eventType", EVENT_TYPE_FILE_PARSED);
        json.set("fileId", fileId);
        json.set("filePath", filePath);
        json.set("timestamp", System.currentTimeMillis());
        return json.toString();
    }

    /**
     * 测试用：构造分析请求事件 JSON
     *
     * @param taskId 任务ID
     * @param fileId 文件ID
     * @return 事件 JSON
     */
    public static String buildAnalyzeRequestEvent(Long taskId, Long fileId) {
        JSONObject json = new JSONObject();
        json.set("eventId", cn.hutool.core.util.IdUtil.fastSimpleUUID());
        json.set("eventType", EVENT_TYPE_ANALYZE_REQUEST);
        json.set("taskId", taskId);
        json.set("fileId", fileId);
        json.set("timestamp", System.currentTimeMillis());
        return json.toString();
    }
}
