package com.redteam.parse.listener;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.parse.service.FileParseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 文件上传事件监听器
 *
 * <p>订阅 {@code redteam.file.events} 主题，当收到 {@code FILE_UPLOADED} 事件时，
 * 触发异步解析流程。基于 fileId 实现幂等：若已存在 SUCCESS 状态的解析结果则跳过。</p>
 *
 * <p>事件消息示例（JSON，由 upload-service FileEvent 投递）：</p>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "eventType": "FILE_UPLOADED",
 *   "fileId": 123,
 *   "fileSm3": "...",
 *   "fileName": "report.pdf",
 *   "fileType": "pdf",
 *   "fileSize": 12345,
 *   "storagePath": "2026/07/27/uuid.pdf",
 *   "targetId": 456,
 *   "userId": 789,
 *   "timestamp": 1700000000000
 * }
 * }</pre>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadEventListener {

    /**
     * 事件字段：事件类型
     */
    private static final String FIELD_EVENT_TYPE = "eventType";

    /**
     * 事件类型：文件上传完成
     */
    private static final String EVENT_TYPE_FILE_UPLOADED = "FILE_UPLOADED";

    private final FileParseService fileParseService;

    /**
     * 监听文件事件
     *
     * <p>仅消费 {@code FILE_UPLOADED} 事件，其他事件直接 ack 跳过。
     * 消费失败时仍会 ack，避免阻塞分区（异常已记录日志，可后续接入死信队列）。</p>
     *
     * @param record Kafka 消息记录
     * @param ack    手动提交 offset 句柄
     */
    @KafkaListener(topics = "${redteam.kafka.topic.file-events:redteam.file.events}",
            groupId = "${spring.kafka.consumer.group-id:parse-service-group}")
    public void onFileEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String message = record.value();
        log.info("收到文件事件: topic={}, partition={}, offset={}, value={}",
                record.topic(), record.partition(), record.offset(), message);

        try {
            if (StrUtil.isBlank(message)) {
                log.warn("文件事件消息为空，跳过处理");
                return;
            }

            JSONObject event = JSONUtil.parseObj(message);
            String eventType = event.getStr(FIELD_EVENT_TYPE);

            if (!EVENT_TYPE_FILE_UPLOADED.equals(eventType)) {
                log.debug("非 FILE_UPLOADED 事件，跳过: eventType={}", eventType);
                return;
            }

            Long fileId = event.getLong("fileId");
            String storagePath = event.getStr("storagePath");
            String fileName = event.getStr("fileName");
            String fileType = event.getStr("fileType");
            Long fileSize = event.getLong("fileSize");

            if (fileId == null || StrUtil.isBlank(storagePath)) {
                log.warn("文件上传事件缺少 fileId 或 storagePath，跳过: fileId={}, storagePath={}",
                        fileId, storagePath);
                return;
            }

            log.info("文件上传事件触发异步解析: fileId={}, fileName={}, fileType={}, storagePath={}",
                    fileId, fileName, fileType, storagePath);

            // 幂等由 parseFileAsync 内部基于 fileId 状态判断
            fileParseService.parseFileAsync(fileId, storagePath, fileName, fileType, fileSize);
        } catch (Exception e) {
            log.error("处理文件上传事件失败: value={}", message, e);
        } finally {
            ack.acknowledge();
        }
    }
}
