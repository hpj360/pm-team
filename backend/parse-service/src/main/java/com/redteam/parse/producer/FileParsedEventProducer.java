package com.redteam.parse.producer;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.redteam.common.entity.FileTagEntity;
import com.redteam.common.mapper.FileTagMapper;
import com.redteam.parse.dto.NerEntityVO;
import com.redteam.parse.dto.ParseResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件解析完成事件生产者（V4.7-P0-3）
 *
 * <p>文件解析完成后，向 {@code file.parsed} 主题投递事件，
 * 由 ai-service 消费并触发威胁摘要（ThreatSummary）生成。</p>
 *
 * <p>事件 JSON 结构：</p>
 * <pre>{@code
 * {
 *   "eventId": "uuid",
 *   "fileId": 123,
 *   "fileName": "xxx.pdf",
 *   "fileType": "PDF",
 *   "fileText": "前 8000 字符的文件文本",
 *   "nerEntities": [{ "entityText": "...", "entityType": "IP", ... }],
 *   "tags": ["L3.ENTITY.IP.PUBLIC", "L1.FILE.TYPE.PDF"],
 *   "parsedAt": 1700000000000
 * }
 * }</pre>
 *
 * <p>容错策略：</p>
 * <ul>
 *   <li>消息发送失败仅记录日志，不抛异常，避免影响主解析流程</li>
 *   <li>tags 查询失败时传空列表（标签异步生成，时序上可能未就绪）</li>
 *   <li>fileText 截断到前 8000 字符，避免消息体过大</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class FileParsedEventProducer {

    /**
     * fileText 最大长度（截断前 8000 字符）
     */
    private static final int MAX_TEXT_LENGTH = 8000;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final FileTagMapper fileTagMapper;

    /**
     * 文件解析完成主题（触发 AI 摘要）
     */
    @Value("${redteam.kafka.topic.file-parsed:file.parsed}")
    private String fileParsedTopic;

    /**
     * 构造方法：复用解析事件专用 KafkaTemplate
     *
     * @param kafkaTemplate KafkaTemplate（parseEventKafkaTemplate）
     * @param fileTagMapper 文件标签 Mapper（查询 tags）
     */
    public FileParsedEventProducer(@Qualifier("parseEventKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                                   FileTagMapper fileTagMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.fileTagMapper = fileTagMapper;
    }

    /**
     * 发送文件解析完成事件（触发 AI 威胁摘要生成）
     *
     * @param parseResult 解析结果
     */
    public void sendFileParsedEvent(ParseResultDTO parseResult) {
        if (parseResult == null || parseResult.getFileId() == null) {
            log.warn("文件解析完成事件参数非法，跳过发送: parseResult={}", parseResult);
            return;
        }

        Long fileId = parseResult.getFileId();
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", IdUtil.fastSimpleUUID());
        event.put("fileId", fileId);
        event.put("fileName", parseResult.getFileName());
        event.put("fileType", parseResult.getFileType());
        // 截断文本到前 8000 字符，避免消息体过大
        event.put("fileText", truncateText(parseResult.getTextContent()));
        // NER 实体列表（序列化为 JSON 数组，consumer 端反序列化）
        event.put("nerEntities", convertNerEntities(parseResult.getNerEntities()));
        // 标签列表（异步生成，时序上可能为空）
        event.put("tags", loadTags(fileId));
        event.put("parsedAt", System.currentTimeMillis());

        send(event, fileId);
    }

    /**
     * 投递事件到 Kafka
     *
     * <p>失败仅记录日志，不抛异常，避免阻塞主解析流程。</p>
     *
     * @param event  事件体
     * @param fileId 文件ID（作为消息 key）
     */
    private void send(Map<String, Object> event, Long fileId) {
        try {
            String payload = JSONUtil.toJsonStr(event);
            String key = String.valueOf(fileId);
            kafkaTemplate.send(fileParsedTopic, key, payload);
            log.info("文件解析完成事件已投递: topic={}, fileId={}, eventId={}",
                    fileParsedTopic, fileId, event.get("eventId"));
        } catch (Exception e) {
            // 发送失败仅记录日志，不阻塞主流程
            log.error("文件解析完成事件投递失败: topic={}, fileId={}, eventId={}",
                    fileParsedTopic, fileId, event.get("eventId"), e);
        }
    }

    /**
     * 截断文本到前 {@value #MAX_TEXT_LENGTH} 字符
     *
     * @param text 原始文本
     * @return 截断后的文本，null 返回空字符串
     */
    private String truncateText(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }

    /**
     * 将 NER 实体列表转换为可序列化的结构（保留全部字段）
     *
     * @param nerEntities NER 实体列表
     * @return 可序列化列表
     */
    private List<Map<String, Object>> convertNerEntities(List<NerEntityVO> nerEntities) {
        if (CollUtil.isEmpty(nerEntities)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>(nerEntities.size());
        for (NerEntityVO entity : nerEntities) {
            if (entity == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("entityText", entity.getEntityText());
            map.put("entityType", entity.getEntityType());
            map.put("entityLabel", entity.getEntityLabel());
            map.put("startPos", entity.getStartPos());
            map.put("endPos", entity.getEndPos());
            map.put("confidence", entity.getConfidence());
            result.add(map);
        }
        return result;
    }

    /**
     * 查询文件标签编码列表
     *
     * <p>标签由 {@code TagRecognitionTask} 异步生成，查询失败或未就绪时返回空列表。</p>
     *
     * @param fileId 文件ID
     * @return 标签编码列表
     */
    private List<String> loadTags(Long fileId) {
        try {
            List<FileTagEntity> tags = fileTagMapper.selectByFileId(fileId);
            if (CollUtil.isEmpty(tags)) {
                return new ArrayList<>();
            }
            List<String> tagCodes = new ArrayList<>(tags.size());
            for (FileTagEntity tag : tags) {
                if (tag != null && StrUtil.isNotBlank(tag.getTagCode())) {
                    tagCodes.add(tag.getTagCode());
                }
            }
            return tagCodes;
        } catch (Exception e) {
            // 标签查询失败不影响事件发送，传空列表
            log.warn("查询文件标签失败，传空列表: fileId={}", fileId, e);
            return new ArrayList<>();
        }
    }
}
