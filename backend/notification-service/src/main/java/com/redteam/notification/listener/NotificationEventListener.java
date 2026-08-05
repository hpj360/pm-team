package com.redteam.notification.listener;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.notification.dto.NotificationDTO;
import com.redteam.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 通知事件监听器
 *
 * <p>订阅 redteam.task.events / redteam.file.events / redteam.system.events /
 * redteam.report.events / redteam.profile.events 五个主题，
 * 根据事件类型自动生成并分发通知。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    /**
     * 任务事件主题
     */
    public static final String TOPIC_TASK_EVENTS = "redteam.task.events";

    /**
     * 文件事件主题
     */
    public static final String TOPIC_FILE_EVENTS = "redteam.file.events";

    /**
     * 系统事件主题
     */
    public static final String TOPIC_SYSTEM_EVENTS = "redteam.system.events";

    /**
     * 报告事件主题
     */
    public static final String TOPIC_REPORT_EVENTS = "redteam.report.events";

    /**
     * 画像事件主题
     */
    public static final String TOPIC_PROFILE_EVENTS = "redteam.profile.events";

    /**
     * 事件字段：事件类型
     */
    private static final String FIELD_EVENT_TYPE = "eventType";

    /**
     * 事件字段：用户ID
     */
    private static final String FIELD_USER_ID = "userId";

    /**
     * 事件字段：发送者ID
     */
    private static final String FIELD_SENDER_ID = "senderId";

    /**
     * 事件字段：标题
     */
    private static final String FIELD_TITLE = "title";

    /**
     * 事件字段：内容
     */
    private static final String FIELD_CONTENT = "content";

    /**
     * 事件字段：关联业务ID
     */
    private static final String FIELD_RELATED_ID = "relatedId";

    /**
     * 事件字段：关联业务类型
     */
    private static final String FIELD_RELATED_TYPE = "relatedType";

    /**
     * 事件字段：通知级别
     */
    private static final String FIELD_LEVEL = "level";

    /**
     * 事件字段：通知通道
     */
    private static final String FIELD_CHANNEL = "channel";

    private final NotificationService notificationService;

    /**
     * 监听任务事件
     *
     * @param record Kafka 消息记录
     */
    @KafkaListener(topics = TOPIC_TASK_EVENTS, groupId = "${spring.kafka.consumer.group-id:notification-group}")
    public void listenTaskEvents(ConsumerRecord<String, String> record) {
        handleEvent(record, TOPIC_TASK_EVENTS, "TASK");
    }

    /**
     * 监听文件事件
     *
     * @param record Kafka 消息记录
     */
    @KafkaListener(topics = TOPIC_FILE_EVENTS, groupId = "${spring.kafka.consumer.group-id:notification-group}")
    public void listenFileEvents(ConsumerRecord<String, String> record) {
        handleEvent(record, TOPIC_FILE_EVENTS, "FILE");
    }

    /**
     * 监听系统事件
     *
     * @param record Kafka 消息记录
     */
    @KafkaListener(topics = TOPIC_SYSTEM_EVENTS, groupId = "${spring.kafka.consumer.group-id:notification-group}")
    public void listenSystemEvents(ConsumerRecord<String, String> record) {
        handleEvent(record, TOPIC_SYSTEM_EVENTS, "SYSTEM");
    }

    /**
     * 监听报告事件
     *
     * @param record Kafka 消息记录
     */
    @KafkaListener(topics = TOPIC_REPORT_EVENTS, groupId = "${spring.kafka.consumer.group-id:notification-group}")
    public void listenReportEvents(ConsumerRecord<String, String> record) {
        handleEvent(record, TOPIC_REPORT_EVENTS, "SYSTEM");
    }

    /**
     * 监听画像事件
     *
     * @param record Kafka 消息记录
     */
    @KafkaListener(topics = TOPIC_PROFILE_EVENTS, groupId = "${spring.kafka.consumer.group-id:notification-group}")
    public void listenProfileEvents(ConsumerRecord<String, String> record) {
        handleEvent(record, TOPIC_PROFILE_EVENTS, "SYSTEM");
    }

    /**
     * 统一处理事件消息
     *
     * @param record    Kafka 消息记录
     * @param topic     来源主题
     * @param defaultType 默认通知类型
     */
    private void handleEvent(ConsumerRecord<String, String> record, String topic, String defaultType) {
        String value = record.value();
        if (StrUtil.isBlank(value)) {
            log.warn("收到空消息，跳过: topic={}, partition={}, offset={}",
                    topic, record.partition(), record.offset());
            return;
        }
        log.info("收到事件: topic={}, offset={}, value={}", topic, record.offset(), value);

        try {
            JSONObject json = JSONUtil.parseObj(value);
            Long userId = json.getLong(FIELD_USER_ID);
            if (userId == null) {
                log.warn("事件缺少 userId，跳过: topic={}", topic);
                return;
            }

            String eventType = json.getStr(FIELD_EVENT_TYPE);
            NotificationDTO dto = new NotificationDTO();
            dto.setUserId(userId);
            dto.setSenderId(json.getLong(FIELD_SENDER_ID));
            dto.setType(StrUtil.blankToDefault(json.getStr(FIELD_EVENT_TYPE), defaultType));
            dto.setLevel(StrUtil.blankToDefault(json.getStr(FIELD_LEVEL), resolveDefaultLevel(eventType)));
            dto.setTitle(StrUtil.blankToDefault(json.getStr(FIELD_TITLE), buildDefaultTitle(defaultType, eventType)));
            dto.setContent(StrUtil.blankToDefault(json.getStr(FIELD_CONTENT), "您有一条新通知"));
            dto.setRelatedId(json.getStr(FIELD_RELATED_ID));
            dto.setRelatedType(json.getStr(FIELD_RELATED_TYPE));
            // 事件触发的通知默认全通道分发，但允许事件覆盖
            dto.setChannel(StrUtil.blankToDefault(json.getStr(FIELD_CHANNEL), "ALL"));

            notificationService.sendNotification(dto);
            log.info("事件已处理并发送通知: topic={}, userId={}", topic, userId);
        } catch (Exception e) {
            // 单条消息异常不影响整体消费
            log.error("事件处理失败: topic={}, offset={}", topic, record.offset(), e);
        }
    }

    /**
     * 根据事件类型推导默认通知级别
     *
     * @param eventType 事件类型
     * @return 通知级别
     */
    private String resolveDefaultLevel(String eventType) {
        if (eventType == null) {
            return "INFO";
        }
        return switch (eventType) {
            case "task.failed", "file.upload.failed", "report.failed", "system.error" -> "ERROR";
            case "task.cancelled", "system.warning" -> "WARN";
            case "security.alert", "alert.triggered" -> "CRITICAL";
            default -> "INFO";
        };
    }

    /**
     * 根据事件类型与默认类型生成默认标题
     *
     * @param type      默认类型
     * @param eventType 事件类型
     * @return 默认标题
     */
    private String buildDefaultTitle(String type, String eventType) {
        if (StrUtil.isNotBlank(eventType)) {
            return switch (eventType) {
                case "task.created" -> "新任务创建";
                case "task.assigned" -> "任务分配通知";
                case "task.completed" -> "任务完成通知";
                case "task.failed" -> "任务失败告警";
                case "file.uploaded" -> "文件上传成功";
                case "file.parsed" -> "文件解析完成";
                case "report.completed" -> "报告生成完成";
                case "report.failed" -> "报告生成失败";
                case "security.alert" -> "安全告警";
                default -> buildDefaultTitle(type);
            };
        }
        return buildDefaultTitle(type);
    }

    /**
     * 根据事件类型生成默认标题
     *
     * @param type 事件类型
     * @return 默认标题
     */
    private String buildDefaultTitle(String type) {
        return switch (type) {
            case "TASK" -> "任务通知";
            case "FILE" -> "文件通知";
            case "SYSTEM" -> "系统通知";
            case "SECURITY" -> "安全告警";
            case "ALERT" -> "告警通知";
            default -> "新通知";
        };
    }
}
