package com.redteam.upload.producer;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.redteam.upload.dto.FileEvent;
import com.redteam.upload.entity.FileEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 文件事件 Kafka 生产者
 *
 * <p>统一封装文件相关事件投递逻辑。消息发送失败仅记录日志，不抛异常，避免影响主流程。</p>
 *
 * <p>事件 JSON 结构：</p>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "eventType": "FILE_UPLOADED",
 *   "fileId": 123,
 *   "fileSm3": "...",
 *   "fileName": "...",
 *   "fileType": "...",
 *   "fileSize": 12345,
 *   "storagePath": "...",
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
public class FileEventProducer {

    /**
     * 事件类型：文件上传完成
     */
    public static final String EVENT_TYPE_FILE_UPLOADED = "FILE_UPLOADED";

    /**
     * 事件类型：文件删除
     */
    public static final String EVENT_TYPE_FILE_DELETED = "FILE_DELETED";

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 构造方法：注入文件事件专用 KafkaTemplate
     *
     * @param kafkaTemplate KafkaTemplate
     */
    public FileEventProducer(@Qualifier("fileEventKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 文件事件主题
     */
    @Value("${redteam.kafka.topic.file-events:redteam.file.events}")
    private String fileEventsTopic;

    /**
     * 发送文件上传完成事件
     *
     * @param fileEntity 文件实体
     * @param userId     操作用户ID
     */
    public void sendFileUploadedEvent(FileEntity fileEntity, Long userId) {
        if (fileEntity == null || fileEntity.getId() == null) {
            log.warn("文件上传事件参数非法，跳过发送: fileEntity={}", fileEntity);
            return;
        }
        FileEvent event = buildBaseEvent(fileEntity, userId, EVENT_TYPE_FILE_UPLOADED);
        send(event);
    }

    /**
     * 发送文件删除事件
     *
     * @param fileEntity 文件实体
     * @param userId     操作用户ID
     */
    public void sendFileDeletedEvent(FileEntity fileEntity, Long userId) {
        if (fileEntity == null || fileEntity.getId() == null) {
            log.warn("文件删除事件参数非法，跳过发送: fileEntity={}", fileEntity);
            return;
        }
        FileEvent event = buildBaseEvent(fileEntity, userId, EVENT_TYPE_FILE_DELETED);
        send(event);
    }

    /**
     * 发送文件删除事件（仅 fileId）
     *
     * @param fileId 文件ID
     * @param userId 操作用户ID
     */
    public void sendFileDeletedEvent(Long fileId, Long userId) {
        if (fileId == null) {
            log.warn("文件删除事件 fileId 为空，跳过发送");
            return;
        }
        FileEvent event = new FileEvent();
        event.setEventId(IdUtil.fastSimpleUUID());
        event.setEventType(EVENT_TYPE_FILE_DELETED);
        event.setFileId(fileId);
        event.setUserId(userId);
        event.setTimestamp(System.currentTimeMillis());
        send(event);
    }

    /**
     * 构造基础事件对象
     *
     * @param fileEntity 文件实体
     * @param userId     操作用户ID
     * @param eventType  事件类型
     * @return 文件事件
     */
    private FileEvent buildBaseEvent(FileEntity fileEntity, Long userId, String eventType) {
        FileEvent event = new FileEvent();
        event.setEventId(IdUtil.fastSimpleUUID());
        event.setEventType(eventType);
        event.setFileId(fileEntity.getId());
        event.setFileSm3(fileEntity.getFileSm3());
        event.setFileName(fileEntity.getOriginalFilename());
        event.setFileType(fileEntity.getFileType());
        event.setFileSize(fileEntity.getFileSize());
        event.setStoragePath(fileEntity.getStoragePath());
        event.setTargetId(fileEntity.getTargetId());
        event.setUserId(userId);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }

    /**
     * 投递事件到 Kafka
     *
     * <p>失败仅记录日志，不抛异常。</p>
     *
     * @param event 文件事件
     */
    private void send(FileEvent event) {
        try {
            String payload = JSONUtil.toJsonStr(event);
            String key = String.valueOf(event.getFileId());
            kafkaTemplate.send(fileEventsTopic, key, payload);
            log.info("文件事件已投递: topic={}, eventId={}, type={}, fileId={}",
                    fileEventsTopic, event.getEventId(), event.getEventType(), event.getFileId());
        } catch (Exception e) {
            // Kafka 发送失败不影响主流程，仅记录日志
            log.error("文件事件投递失败: topic={}, eventId={}, type={}, fileId={}",
                    fileEventsTopic, event.getEventId(), event.getEventType(), event.getFileId(), e);
        }
    }
}
