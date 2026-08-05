package com.redteam.ai.consumer;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.ai.service.ThreatSummaryService;
import com.redteam.common.api.dto.NerEntityVO;
import com.redteam.common.entity.ThreatSummaryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件解析完成事件消费者（V4.7-P0-3）
 *
 * <p>监听 {@code file.parsed} 主题，消费 parse-service 投递的文件解析完成事件，
 * 调用 {@link ThreatSummaryService#generateSummary} 生成威胁摘要。</p>
 *
 * <p>事件 JSON 结构：</p>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "fileId": 123,
 *   "fileName": "xxx.pdf",
 *   "fileType": "PDF",
 *   "fileText": "...",
 *   "nerEntities": [{ "entityText": "...", "entityType": "IP", ... }],
 *   "tags": ["L3.ENTITY.IP.PUBLIC"],
 *   "parsedAt": 1700000000000
 * }
 * }</pre>
 *
 * <p>容错策略：</p>
 * <ul>
 *   <li>空消息 / fileId 缺失：记录日志后跳过，不抛异常（避免无意义重试）</li>
 *   <li>已存在成功摘要（status=1）：幂等跳过</li>
 *   <li>生成失败：抛出 RuntimeException，由 {@code DefaultErrorHandler} 重试 3 次后投递死信队列
 *       {@code file.parsed.dlq}，consumer 继续消费下一条，不阻塞</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileParsedEventConsumer {

    /**
     * 文件解析完成主题
     */
    public static final String TOPIC_FILE_PARSED = "file.parsed";

    /**
     * 消费组：ai-service 专用
     */
    public static final String GROUP_ID = "ai-service-group";

    /**
     * 威胁摘要状态：成功
     */
    private static final String SUMMARY_STATUS_SUCCESS = "1";

    private final ThreatSummaryService threatSummaryService;

    /**
     * 消费文件解析完成事件，触发威胁摘要生成
     *
     * @param record Kafka 消息记录
     */
    @KafkaListener(topics = TOPIC_FILE_PARSED, groupId = GROUP_ID)
    public void onFileParsed(ConsumerRecord<String, String> record) {
        String value = record.value();
        if (StrUtil.isBlank(value)) {
            log.warn("收到空消息，跳过: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }

        log.info("收到文件解析完成事件: topic={}, offset={}, key={}", record.topic(), record.offset(), record.key());

        JSONObject event;
        try {
            event = JSONUtil.parseObj(value);
        } catch (Exception e) {
            // JSON 解析失败属于毒丸消息，重试无意义，仅记录日志后跳过
            log.error("事件 JSON 解析失败，跳过: topic={}, offset={}, value={}",
                    record.topic(), record.offset(), value, e);
            return;
        }

        Long fileId = event.getLong("fileId");
        if (fileId == null) {
            log.warn("事件缺少 fileId，跳过: topic={}, offset={}", record.topic(), record.offset());
            return;
        }

        // 幂等保护：若已存在成功的威胁摘要，跳过避免重复生成
        if (isSummaryAlreadyGenerated(fileId)) {
            log.info("文件威胁摘要已存在且成功，跳过生成: fileId={}", fileId);
            return;
        }

        String fileName = event.getStr("fileName");
        String fileType = event.getStr("fileType");
        String fileText = event.getStr("fileText");
        List<NerEntityVO> nerEntities = parseNerEntities(event.getJSONArray("nerEntities"));
        List<String> tags = parseTags(event.getJSONArray("tags"));

        log.info("触发威胁摘要生成: fileId={}, fileName={}, fileType={}, nerCount={}, tagCount={}",
                fileId, fileName, fileType,
                nerEntities == null ? 0 : nerEntities.size(),
                tags == null ? 0 : tags.size());

        try {
            ThreatSummaryEntity summary = threatSummaryService.generateSummary(
                    fileId, fileText, fileName, fileType, nerEntities, tags);
            log.info("威胁摘要生成完成: fileId={}, status={}",
                    fileId, summary == null ? null : summary.getStatus());
        } catch (Exception e) {
            // 抛出异常触发 DefaultErrorHandler 重试 3 次后投递死信队列
            log.error("威胁摘要生成失败，将触发重试: fileId={}", fileId, e);
            throw new RuntimeException("威胁摘要生成失败: fileId=" + fileId + ", reason=" + e.getMessage(), e);
        }
    }

    /**
     * 判断文件是否已生成成功的威胁摘要
     *
     * @param fileId 文件ID
     * @return true 表示已存在 status=1 的成功记录
     */
    private boolean isSummaryAlreadyGenerated(Long fileId) {
        try {
            ThreatSummaryEntity existing = threatSummaryService.getByFileId(fileId);
            return existing != null && SUMMARY_STATUS_SUCCESS.equals(String.valueOf(existing.getStatus()));
        } catch (Exception e) {
            // 查询失败不阻塞生成流程，按未生成处理
            log.warn("查询已有威胁摘要失败，按未生成处理: fileId={}", fileId, e);
            return false;
        }
    }

    /**
     * 解析 NER 实体列表
     *
     * @param array JSON 数组
     * @return NER 实体 VO 列表
     */
    private List<NerEntityVO> parseNerEntities(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(array, NerEntityVO.class);
        } catch (Exception e) {
            log.warn("NER 实体列表解析失败，使用空列表: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 解析标签列表
     *
     * @param array JSON 数组
     * @return 标签编码列表
     */
    private List<String> parseTags(JSONArray array) {
        if (array == null || array.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> tags = new ArrayList<>(array.size());
            for (Object item : array) {
                if (item != null) {
                    tags.add(String.valueOf(item));
                }
            }
            return tags;
        } catch (Exception e) {
            log.warn("标签列表解析失败，使用空列表: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
