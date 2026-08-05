package com.redteam.search.listener;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.search.dto.FileIndexDTO;
import com.redteam.search.entity.SearchIndexTaskEntity;
import com.redteam.search.mapper.SearchIndexTaskMapper;
import com.redteam.search.service.FileSearchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 文件解析事件监听器
 *
 * <p>订阅 {@code redteam.parse.events} 主题，当收到 {@code file.parsed} 事件时，
 * 触发 ES + Milvus 索引创建。</p>
 *
 * <p>事件消息示例（JSON，由 parse-service ParseEventProducer 投递）：</p>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "eventType": "file.parsed",
 *   "fileId": 123,
 *   "fileName": "report.pdf",
 *   "fileType": "pdf",
 *   "parseStatus": "SUCCESS",
 *   "parseDurationMs": 1234,
 *   "textHash": "...",
 *   "textLength": 123,
 *   "language": "zh",
 *   "yaraMatchCount": 2,
 *   "nerEntityCount": 5,
 *   "timestamp": 1700000000000
 * }
 * }</pre>
 *
 * <p>幂等：基于 fileId 检查索引任务状态，若已 SUCCESS 则跳过。
 * 失败重试：最多 3 次，指数退避（1s/2s/4s）。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileParsedEventListener {

    /**
     * 事件字段：事件类型
     */
    private static final String FIELD_EVENT_TYPE = "eventType";

    /**
     * 事件类型：文件解析完成
     */
    private static final String EVENT_TYPE_FILE_PARSED = "file.parsed";

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY = 3;

    /**
     * 退避基数（毫秒）
     */
    private static final long BACKOFF_BASE_MS = 1000L;

    private final FileSearchService fileSearchService;
    private final SearchIndexTaskMapper searchIndexTaskMapper;

    /**
     * 监听文件解析事件
     *
     * <p>仅消费 {@code file.parsed} 事件，其他事件直接 ack 跳过。
     * 消费失败仍会 ack（异常已记录日志，可后续接入死信队列），避免阻塞分区。</p>
     *
     * @param record Kafka 消息记录
     * @param ack    手动提交 offset 句柄
     */
    @KafkaListener(topics = "${redteam.kafka.topic.parse-events:redteam.parse.events}",
            groupId = "${spring.kafka.consumer.group-id:search-service-group}")
    public void onFileParsed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String message = record.value();
        log.info("收到解析事件: topic={}, partition={}, offset={}, value={}",
                record.topic(), record.partition(), record.offset(), message);

        try {
            if (StrUtil.isBlank(message)) {
                log.warn("解析事件消息为空，跳过处理");
                return;
            }

            JSONObject event = JSONUtil.parseObj(message);
            String eventType = event.getStr(FIELD_EVENT_TYPE);

            if (!EVENT_TYPE_FILE_PARSED.equals(eventType)) {
                log.debug("非 file.parsed 事件，跳过: eventType={}", eventType);
                return;
            }

            Long fileId = event.getLong("fileId");
            if (fileId == null) {
                log.warn("解析事件缺少 fileId，跳过");
                return;
            }

            // 幂等检查：已 SUCCESS 且 ES/Milvus 均已索引则跳过
            if (isAlreadyIndexed(fileId)) {
                log.info("文件已索引，跳过: fileId={}", fileId);
                return;
            }

            FileIndexDTO dto = buildIndexDto(event);
            retryIndex(dto, message);
        } catch (Exception e) {
            log.error("处理文件解析事件失败: value={}", message, e);
        } finally {
            ack.acknowledge();
        }
    }

    /**
     * 幂等检查：是否已完成索引
     *
     * @param fileId 文件 ID
     * @return true 表示已索引完成
     */
    private boolean isAlreadyIndexed(Long fileId) {
        try {
            SearchIndexTaskEntity task = searchIndexTaskMapper.selectOne(
                    new LambdaQueryWrapper<SearchIndexTaskEntity>()
                            .eq(SearchIndexTaskEntity::getFileId, fileId));
            if (task == null) {
                return false;
            }
            return SearchIndexTaskEntity.STATUS_SUCCESS.equals(task.getIndexStatus())
                    && Boolean.TRUE.equals(task.getEsIndexed())
                    && Boolean.TRUE.equals(task.getMilvusIndexed());
        } catch (Exception e) {
            log.warn("查询索引任务状态失败，按未索引处理: fileId={}", fileId, e);
            return false;
        }
    }

    /**
     * 从事件构造 FileIndexDTO
     *
     * @param event 事件 JSON
     * @return FileIndexDTO
     */
    private FileIndexDTO buildIndexDto(JSONObject event) {
        FileIndexDTO dto = new FileIndexDTO();
        dto.setFileId(event.getLong("fileId"));
        dto.setFileName(event.getStr("fileName"));
        dto.setFileType(event.getStr("fileType"));
        Long fileSize = event.getLong("fileSize");
        dto.setFileSize(fileSize);
        // 解析事件未携带完整文本内容，向量索引将基于文件名降级
        // 实际生产应通过 RPC 调用 parse-service 获取完整 textContent
        dto.setTextContent(event.getStr("textContent"));
        dto.setTargetId(event.getLong("targetId"));
        dto.setUploadTime(LocalDateTime.now());
        dto.setCreateTime(LocalDateTime.now());
        return dto;
    }

    /**
     * 带重试的索引创建
     *
     * <p>最多重试 {@value #MAX_RETRY} 次，指数退避（1s/2s/4s）。</p>
     *
     * @param dto     文件索引数据
     * @param message 原始消息（日志用）
     */
    private void retryIndex(FileIndexDTO dto, String message) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                fileSearchService.indexFile(dto);
                log.info("文件索引创建成功: fileId={}, attempt={}", dto.getFileId(), attempt);
                return;
            } catch (Exception e) {
                log.warn("文件索引创建失败: fileId={}, attempt={}/{}, error={}",
                        dto.getFileId(), attempt, MAX_RETRY, e.getMessage());
                if (attempt >= MAX_RETRY) {
                    log.error("文件索引创建最终失败（已用尽重试）: fileId={}, value={}",
                            dto.getFileId(), message, e);
                    return;
                }
                sleepWithBackoff(attempt);
            }
        }
    }

    /**
     * 指数退避等待
     *
     * @param attempt 当前尝试次数
     */
    private void sleepWithBackoff(int attempt) {
        long delay = BACKOFF_BASE_MS * (1L << (attempt - 1));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("索引重试退避被中断");
        }
    }
}
