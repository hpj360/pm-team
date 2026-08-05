package com.redteam.report.listener;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.report.dto.ReportGenerateDTO;
import com.redteam.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 任务事件监听器
 *
 * <p>订阅 {@code redteam.task.events} 主题，根据事件类型自动触发对应类型报告的异步生成流程：</p>
 * <ul>
 *   <li>{@code task.completed} → {@code TASK_SUMMARY} PDF 报告</li>
 *   <li>{@code task.analyzed}  → {@code TARGET_PROFILE} HTML 报告（目标画像完成时）</li>
 *   <li>{@code task.scanned}   → {@code VULNERABILITY_SCAN} PDF 报告（漏洞扫描完成时）</li>
 * </ul>
 *
 * <p>事件消息示例（JSON）：</p>
 * <pre>{@code
 * {
 *   "eventType": "task.completed",
 *   "taskId": "task-1001",
 *   "taskName": "Q3渗透测试任务",
 *   "operatorId": 10086,
 *   "completedAt": "2026-07-27 10:30:00"
 * }
 * }</pre>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportEventListener {

    private static final String EVENT_TYPE = "eventType";
    private static final String TASK_COMPLETED = "task.completed";
    private static final String TASK_ANALYZED = "task.analyzed";
    private static final String TASK_SCANNED = "task.scanned";

    private final ReportService reportService;

    /**
     * 处理任务事件
     *
     * <p>仅消费 {@code task.completed} / {@code task.analyzed} / {@code task.scanned} 事件，
     * 其他事件直接 ack 跳过。消费失败时仍会 ack，避免阻塞分区（异常已记录日志，可后续接入死信队列）。</p>
     *
     * @param record Kafka 消息记录
     * @param ack    手动提交 offset 句柄
     */
    @KafkaListener(topics = "${spring.kafka.consumer.topic:redteam.task.events}",
            groupId = "${spring.kafka.consumer.group-id:report-service-group}")
    public void onTaskEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String message = record.value();
        log.info("收到任务事件: topic={}, partition={}, offset={}, value={}",
                record.topic(), record.partition(), record.offset(), message);

        try {
            if (StrUtil.isBlank(message)) {
                log.warn("任务事件消息为空，跳过处理");
                return;
            }

            JSONObject event = JSONUtil.parseObj(message);
            String eventType = event.getStr(EVENT_TYPE);

            if (StrUtil.isBlank(eventType)) {
                log.warn("事件缺少 eventType 字段，跳过");
                return;
            }

            switch (eventType) {
                case TASK_COMPLETED -> handleTaskCompleted(event);
                case TASK_ANALYZED -> handleTaskAnalyzed(event);
                case TASK_SCANNED -> handleTaskScanned(event);
                default -> log.debug("非关注事件类型，跳过: eventType={}", eventType);
            }
        } catch (Exception e) {
            log.error("处理任务事件失败: value={}", message, e);
        } finally {
            ack.acknowledge();
        }
    }

    /**
     * 处理 task.completed 事件：触发任务总结报告（PDF）
     *
     * @param event 事件内容
     */
    private void handleTaskCompleted(JSONObject event) {
        String taskId = event.getStr("taskId");
        String taskName = event.getStr("taskName");

        log.info("任务完成事件触发报告生成: taskId={}, taskName={}", taskId, taskName);

        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportName(StrUtil.blankToDefault(taskName, "任务总结报告") + "-" + taskId);
        dto.setReportType("TASK_SUMMARY");
        dto.setTaskId(taskId);
        dto.setFormat("PDF");

        reportService.generateReport(dto);
    }

    /**
     * 处理 task.analyzed 事件：触发目标画像报告（HTML）
     *
     * @param event 事件内容
     */
    private void handleTaskAnalyzed(JSONObject event) {
        String taskId = event.getStr("taskId");
        String taskName = event.getStr("taskName");
        String targetId = event.getStr("targetId");

        log.info("目标分析完成事件触发报告生成: taskId={}, targetId={}", taskId, targetId);

        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportName("目标画像报告-" + StrUtil.blankToDefault(targetId, taskId));
        dto.setReportType("TARGET_PROFILE");
        dto.setTaskId(taskId);
        dto.setTargetId(targetId);
        dto.setFormat("HTML");

        reportService.generateReport(dto);
    }

    /**
     * 处理 task.scanned 事件：触发漏洞扫描报告（PDF）
     *
     * @param event 事件内容
     */
    private void handleTaskScanned(JSONObject event) {
        String taskId = event.getStr("taskId");
        String taskName = event.getStr("taskName");
        String targetId = event.getStr("targetId");

        log.info("漏洞扫描完成事件触发报告生成: taskId={}, targetId={}", taskId, targetId);

        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportName("漏洞扫描报告-" + StrUtil.blankToDefault(targetId, taskId));
        dto.setReportType("VULNERABILITY_SCAN");
        dto.setTaskId(taskId);
        dto.setTargetId(targetId);
        dto.setFormat("PDF");

        reportService.generateReport(dto);
    }
}
