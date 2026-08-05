package com.redteam.parse.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.SM3;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.ResultCode;
import com.redteam.parse.dto.NerEntityVO;
import com.redteam.parse.dto.ParseQueryDTO;
import com.redteam.parse.dto.ParseResultDTO;
import com.redteam.parse.dto.YaraMatchVO;
import com.redteam.parse.entity.NerResultEntity;
import com.redteam.parse.entity.ParseResultEntity;
import com.redteam.parse.mapper.NerResultMapper;
import com.redteam.parse.mapper.ParseResultMapper;
import com.redteam.parse.parser.TikaFileParser;
import com.redteam.parse.producer.FileParsedEventProducer;
import com.redteam.parse.producer.ParseEventProducer;
import com.redteam.parse.service.FileParseService;
import com.redteam.parse.service.NerService;
import com.redteam.parse.service.TagRecognitionTask;
import com.redteam.parse.service.YaraScanService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 文件解析服务实现
 *
 * <p>v2.3 增强版本，集成 YARA 规则扫描与 security-BERT NER 实体识别：</p>
 * <ol>
 *   <li>使用 Apache Tika 提取文本内容。</li>
 *   <li>计算文本 SM3 哈希。</li>
 *   <li>调用 {@link YaraScanService#scanText} 扫描恶意特征（降级安全）。</li>
 *   <li>调用 {@link NerService#extractEntities} 提取实体（降级安全）。</li>
 *   <li>持久化解析结果到 PostgreSQL。</li>
 *   <li>发送 Kafka 事件 redteam.file.parsed。</li>
 * </ol>
 *
 * <p>YARA / NER 失败时仅记录日志，不影响主解析流程。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileParseServiceImpl implements FileParseService {

    /**
     * 解析状态缓存 Key 前缀
     */
    private static final String PARSE_STATUS_PREFIX = "parse:status:";

    /**
     * 解析状态缓存 TTL（小时）
     */
    private static final long PARSE_STATUS_TTL_HOURS = 24L;

    /**
     * 解析状态：成功
     */
    private static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 解析状态：失败
     */
    private static final String STATUS_FAILED = "FAILED";

    /**
     * 解析状态：进行中
     */
    private static final String STATUS_PENDING = "PENDING";

    private final MinioClient minioClient;
    private final StringRedisTemplate redisTemplate;
    private final TikaFileParser tikaFileParser;
    private final YaraScanService yaraScanService;
    private final NerService nerService;
    private final ParseResultMapper parseResultMapper;
    private final NerResultMapper nerResultMapper;
    private final ParseEventProducer parseEventProducer;
    private final FileParsedEventProducer fileParsedEventProducer;
    private final TagRecognitionTask tagRecognitionTask;

    /**
     * MinIO bucket
     */
    @Value("${minio.bucket-name:redteam-files}")
    private String bucketName;

    /**
     * 本地临时目录（用于下载 MinIO 文件供 yara CLI 扫描）
     */
    @Value("${redteam.parse.temp-dir:/tmp/parse-temp}")
    private String tempDir;

    @Override
    public ParseResultDTO parseFile(Long fileId) {
        if (fileId == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件ID不能为空");
        }
        // 优先返回已持久化结果
        ParseResultEntity existing = findByFileId(fileId);
        if (existing != null && STATUS_SUCCESS.equals(existing.getParseStatus())) {
            log.info("命中已解析结果，直接返回: fileId={}", fileId);
            return toDTO(existing);
        }
        throw BusinessException.of(ResultCode.FILE_NOT_FOUND,
                "文件信息不存在，请使用 parseFile(storagePath, filename, fileType) 或通过 Kafka 事件触发");
    }

    @Override
    public ParseResultDTO parseFile(String storagePath, String filename, String fileType) {
        return parseFile(null, storagePath, filename, fileType, null);
    }

    /**
     * 同步解析文件核心方法
     *
     * @param fileId      文件ID（可空，由 Kafka 监听器传入）
     * @param storagePath 存储路径
     * @param filename    文件名
     * @param fileType    文件类型
     * @param fileSize    文件大小（可空）
     * @return 解析结果
     */
    private ParseResultDTO parseFile(Long fileId, String storagePath, String filename,
                                     String fileType, Long fileSize) {
        long start = System.currentTimeMillis();
        log.info("开始解析文件: fileId={}, filename={}, fileType={}", fileId, filename, fileType);

        ParseResultDTO result = new ParseResultDTO();
        result.setFileId(fileId);
        result.setFileName(filename);
        result.setFileType(fileType);
        result.setFileSize(fileSize);
        result.setParseStatus(STATUS_PENDING);

        // 占位记录（防止重复解析）
        if (fileId != null) {
            upsertPendingResult(fileId, filename, fileType, fileSize);
        }

        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(storagePath)
                .build())) {

            // Tika 提取文本
            ParseResultDTO tikaResult = tikaFileParser.parse(is, filename);
            result.setTextContent(tikaResult.getTextContent());
            result.setTextLength(tikaResult.getTextLength());
            result.setPageCount(tikaResult.getPageCount());
            result.setTitle(tikaResult.getTitle());
            result.setAuthor(tikaResult.getAuthor());
            result.setSummary(tikaResult.getSummary());
            result.setMetadata(tikaResult.getMetadata());
            result.setLanguage(tikaResult.getLanguage());
            result.setEncoding(tikaResult.getEncoding());
            result.setDuration(tikaResult.getDuration());
            result.setParseDurationMs(tikaResult.getParseDurationMs());

            if (!Boolean.TRUE.equals(tikaResult.getSuccess())) {
                result.setSuccess(false);
                result.setParseStatus(STATUS_FAILED);
                result.setErrorMessage(tikaResult.getErrorMessage());
                result.setParseError(tikaResult.getErrorMessage());
                if (fileId != null) {
                    updateParseResult(fileId, result, STATUS_FAILED);
                }
                parseEventProducer.sendFileParseFailedEvent(fileId, tikaResult.getErrorMessage());
                return result;
            }

            // 计算 SM3 文本哈希
            result.setTextHash(sm3Hex(result.getTextContent()));

            // YARA 扫描（降级安全）
            try {
                List<YaraMatchVO> yaraMatches = yaraScanService.scanText(fileId, result.getTextContent());
                result.setYaraMatches(yaraMatches);
            } catch (Exception e) {
                log.warn("YARA 扫描失败，降级跳过: fileId={}", fileId, e);
            }

            // NER 实体识别（降级安全）
            try {
                List<NerEntityVO> nerEntities = nerService.extractEntities(result.getTextContent());
                result.setNerEntities(nerEntities);
                if (fileId != null) {
                    persistNerEntities(fileId, nerEntities);
                }
                // 自动标签识别（异步，失败不影响主流程）
                if (fileId != null) {
                    tagRecognitionTask.executeTagRecognition(fileId, result.getTextContent(),
                            filename, fileType, nerEntities);
                }
            } catch (Exception e) {
                log.warn("NER 识别失败，降级跳过: fileId={}", fileId, e);
            }

            result.setSuccess(true);
            result.setParseStatus(STATUS_SUCCESS);
            result.setParseDurationMs(System.currentTimeMillis() - start);

            // 持久化与事件
            if (fileId != null) {
                updateParseResult(fileId, result, STATUS_SUCCESS);
                cacheParseResult(storagePath, result);
            }
            parseEventProducer.sendFileParsedEvent(result);
            // V4.7-P0-3：触发 ai-service 生成威胁摘要（发送失败仅记日志，不阻塞主流程）
            fileParsedEventProducer.sendFileParsedEvent(result);
            log.info("文件解析完成: fileId={}, duration={}ms, yaraMatches={}, nerEntities={}",
                    fileId, result.getParseDurationMs(),
                    result.getYaraMatches() == null ? 0 : result.getYaraMatches().size(),
                    result.getNerEntities() == null ? 0 : result.getNerEntities().size());
        } catch (Exception e) {
            log.error("文件解析失败: fileId={}, filename={}", fileId, filename, e);
            result.setSuccess(false);
            result.setParseStatus(STATUS_FAILED);
            result.setErrorMessage("文件解析失败: " + e.getMessage());
            result.setParseError(result.getErrorMessage());
            result.setParseDurationMs(System.currentTimeMillis() - start);
            if (fileId != null) {
                updateParseResult(fileId, result, STATUS_FAILED);
            }
            parseEventProducer.sendFileParseFailedEvent(fileId, e.getMessage());
        }
        return result;
    }

    @Override
    public void parseFileAsync(Long fileId) {
        if (fileId == null) {
            log.warn("异步解析 fileId 为空，跳过");
            return;
        }
        // 由 Kafka 监听器触发，此处仅占位，实际异步逻辑由监听器直接调用
        log.info("异步解析占位调用: fileId={}（实际由 Kafka 监听器触发）", fileId);
    }

    @Override
    @Async("parseTaskExecutor")
    public void parseFileAsync(Long fileId, String storagePath, String fileName,
                               String fileType, Long fileSize) {
        if (fileId == null || StrUtil.isBlank(storagePath)) {
            log.warn("异步解析参数非法: fileId={}, storagePath={}", fileId, storagePath);
            return;
        }
        // 幂等：已成功解析则跳过
        ParseResultEntity existing = findByFileId(fileId);
        if (existing != null && STATUS_SUCCESS.equals(existing.getParseStatus())) {
            log.info("文件已解析，跳过: fileId={}", fileId);
            return;
        }
        parseFile(fileId, storagePath, fileName, fileType, fileSize);
    }

    @Override
    public ParseResultDTO getParseResult(Long fileId) {
        if (fileId == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件ID不能为空");
        }
        ParseResultEntity entity = findByFileId(fileId);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "解析结果不存在: fileId=" + fileId);
        }
        ParseResultDTO dto = toDTO(entity);
        // 补充 NER 实体
        dto.setNerEntities(loadNerEntities(fileId));
        return dto;
    }

    @Override
    public PageResult<ParseResultDTO> listParseResults(ParseQueryDTO query) {
        if (query == null) {
            query = new ParseQueryDTO();
        }
        long pageNum = query.getPageNum() == null ? 1L : Math.max(1L, query.getPageNum());
        long pageSize = query.getPageSize() == null ? 10L : Math.min(100L, Math.max(1L, query.getPageSize()));

        Page<ParseResultEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ParseResultEntity> wrapper = new LambdaQueryWrapper<>();
        if (query.getFileId() != null) {
            wrapper.eq(ParseResultEntity::getFileId, query.getFileId());
        }
        if (StrUtil.isNotBlank(query.getFileName())) {
            wrapper.like(ParseResultEntity::getFileName, query.getFileName());
        }
        if (StrUtil.isNotBlank(query.getParseStatus())) {
            wrapper.eq(ParseResultEntity::getParseStatus, query.getParseStatus());
        }
        wrapper.orderByDesc(ParseResultEntity::getCreateTime);

        Page<ParseResultEntity> result = parseResultMapper.selectPage(page, wrapper);
        List<ParseResultDTO> records = new ArrayList<>();
        for (ParseResultEntity e : result.getRecords()) {
            ParseResultDTO dto = toDTO(e);
            dto.setNerEntities(loadNerEntities(e.getFileId()));
            records.add(dto);
        }
        return PageResult.of(pageNum, pageSize, result.getTotal(), records);
    }

    // ==================== 私有方法 ====================

    /**
     * 持久化 NER 实体列表
     *
     * @param fileId   文件ID
     * @param entities NER 实体列表
     */
    private void persistNerEntities(Long fileId, List<NerEntityVO> entities) {
        if (fileId == null || entities == null || entities.isEmpty()) {
            return;
        }
        // 先清理旧记录，再批量插入
        LambdaQueryWrapper<NerResultEntity> delete = new LambdaQueryWrapper<>();
        delete.eq(NerResultEntity::getFileId, fileId);
        nerResultMapper.delete(delete);
        for (NerEntityVO vo : entities) {
            NerResultEntity entity = new NerResultEntity();
            entity.setFileId(fileId);
            entity.setEntityText(vo.getEntityText());
            entity.setEntityType(vo.getEntityType());
            entity.setEntityLabel(vo.getEntityLabel());
            entity.setStartPos(vo.getStartPos());
            entity.setEndPos(vo.getEndPos());
            entity.setConfidence(vo.getConfidence());
            nerResultMapper.insert(entity);
        }
    }

    /**
     * 加载文件的 NER 实体列表
     *
     * @param fileId 文件ID
     * @return NER 实体 VO 列表
     */
    private List<NerEntityVO> loadNerEntities(Long fileId) {
        if (fileId == null) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<NerResultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NerResultEntity::getFileId, fileId);
        List<NerResultEntity> entities = nerResultMapper.selectList(wrapper);
        List<NerEntityVO> result = new ArrayList<>();
        for (NerResultEntity e : entities) {
            NerEntityVO vo = new NerEntityVO();
            vo.setEntityText(e.getEntityText());
            vo.setEntityType(e.getEntityType());
            vo.setEntityLabel(e.getEntityLabel());
            vo.setStartPos(e.getStartPos());
            vo.setEndPos(e.getEndPos());
            vo.setConfidence(e.getConfidence());
            result.add(vo);
        }
        return result;
    }

    /**
     * 按 fileId 查询已持久化的解析结果
     *
     * @param fileId 文件ID
     * @return 解析结果实体，不存在返回 null
     */
    private ParseResultEntity findByFileId(Long fileId) {
        if (fileId == null) {
            return null;
        }
        LambdaQueryWrapper<ParseResultEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ParseResultEntity::getFileId, fileId);
        return parseResultMapper.selectOne(wrapper);
    }

    /**
     * 插入 PENDING 占位记录（防止重复解析）
     *
     * @param fileId   文件ID
     * @param filename 文件名
     * @param fileType 文件类型
     * @param fileSize 文件大小
     */
    private void upsertPendingResult(Long fileId, String filename, String fileType, Long fileSize) {
        try {
            ParseResultEntity existing = findByFileId(fileId);
            if (existing != null) {
                return;
            }
            ParseResultEntity entity = new ParseResultEntity();
            entity.setFileId(fileId);
            entity.setFileName(filename);
            entity.setFileType(fileType);
            entity.setFileSize(fileSize);
            entity.setParseStatus(STATUS_PENDING);
            parseResultMapper.insert(entity);
        } catch (Exception e) {
            log.warn("插入 PENDING 占位失败: fileId={}", fileId, e);
        }
    }

    /**
     * 更新解析结果
     *
     * @param fileId  文件ID
     * @param dto    解析结果
     * @param status 解析状态
     */
    private void updateParseResult(Long fileId, ParseResultDTO dto, String status) {
        try {
            ParseResultEntity entity = findByFileId(fileId);
            if (entity == null) {
                entity = new ParseResultEntity();
                entity.setFileId(fileId);
                entity.setFileName(dto.getFileName());
                entity.setFileType(dto.getFileType());
                entity.setFileSize(dto.getFileSize());
            }
            entity.setFileName(dto.getFileName());
            entity.setFileType(dto.getFileType());
            entity.setFileSize(dto.getFileSize());
            entity.setTextContent(dto.getTextContent());
            entity.setTextHash(dto.getTextHash());
            entity.setLanguage(dto.getLanguage());
            entity.setEncoding(dto.getEncoding());
            entity.setPageCount(dto.getPageCount());
            entity.setParseStatus(status);
            entity.setParseError(dto.getParseError());
            entity.setParseDurationMs(dto.getParseDurationMs());
            if (entity.getId() == null) {
                parseResultMapper.insert(entity);
            } else {
                parseResultMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.error("更新解析结果失败: fileId={}", fileId, e);
        }
    }

    /**
     * 缓存解析结果文本到 Redis（用于全文检索兜底）
     *
     * @param storagePath 存储路径
     * @param result      解析结果
     */
    private void cacheParseResult(String storagePath, ParseResultDTO result) {
        try {
            String key = PARSE_STATUS_PREFIX + storagePath;
            redisTemplate.opsForValue().set(key, result.getTextContent(),
                    PARSE_STATUS_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("缓存解析结果失败: storagePath={}", storagePath, e);
        }
    }

    /**
     * 实体转 DTO
     *
     * @param entity 解析结果实体
     * @return 解析结果 DTO
     */
    private ParseResultDTO toDTO(ParseResultEntity entity) {
        ParseResultDTO dto = new ParseResultDTO();
        dto.setFileId(entity.getFileId());
        dto.setFileName(entity.getFileName());
        dto.setFileType(entity.getFileType());
        dto.setFileSize(entity.getFileSize());
        dto.setTextContent(entity.getTextContent());
        dto.setTextHash(entity.getTextHash());
        dto.setLanguage(entity.getLanguage());
        dto.setEncoding(entity.getEncoding());
        dto.setPageCount(entity.getPageCount());
        dto.setParseStatus(entity.getParseStatus());
        dto.setParseError(entity.getParseError());
        dto.setParseDurationMs(entity.getParseDurationMs());
        dto.setDuration(entity.getParseDurationMs());
        dto.setSuccess(STATUS_SUCCESS.equals(entity.getParseStatus()));
        dto.setErrorMessage(entity.getParseError());
        if (entity.getTextContent() != null) {
            dto.setTextLength(entity.getTextContent().length());
        }
        return dto;
    }

    /**
     * 计算 SM3 哈希（十六进制）
     *
     * @param text 原文
     * @return SM3 十六进制摘要
     */
    private static String sm3Hex(String text) {
        if (text == null) {
            return null;
        }
        return new SM3().digestHex(text, StandardCharsets.UTF_8);
    }
}
