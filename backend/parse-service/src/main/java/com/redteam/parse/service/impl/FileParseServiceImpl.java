package com.redteam.parse.service.impl;

import com.redteam.parse.dto.ParseResultDTO;
import com.redteam.parse.service.FileParseService;
import com.redteam.parse.service.FileParser;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 文件解析服务实现类
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileParseServiceImpl implements FileParseService {

    private final MinioClient minioClient;
    private final List<FileParser> fileParsers;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${minio.bucket-name}")
    private String bucketName;

    private static final String PARSE_STATUS_PREFIX = "parse:status:";
    private static final String FILE_ANALYZE_TOPIC = "file-analyze-topic";

    @Override
    public ParseResultDTO parseFile(Long fileId) {
        // TODO: 从数据库获取文件信息
        // 这里简化处理，实际应该从数据库查询文件信息
        throw new UnsupportedOperationException("请使用parseFile(storagePath, filename, fileType)方法");
    }

    @Override
    public ParseResultDTO parseFile(String storagePath, String filename, String fileType) {
        log.info("开始解析文件: {}", filename);

        try {
            // 从MinIO获取文件流
            InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .build());

            // 查找合适的解析器
            FileParser parser = findParser(fileType);
            if (parser == null) {
                log.warn("不支持的文件类型: {}", fileType);
                return ParseResultDTO.fail("不支持的文件类型: " + fileType);
            }

            // 执行解析
            ParseResultDTO result = parser.parse(inputStream, filename);

            // 缓存解析结果
            if (result.getSuccess()) {
                cacheParseResult(storagePath, result);
            }

            inputStream.close();
            return result;

        } catch (Exception e) {
            log.error("文件解析失败: {}", filename, e);
            return ParseResultDTO.fail("文件解析失败: " + e.getMessage());
        }
    }

    @Override
    public void parseFileAsync(Long fileId) {
        // 发送消息到Kafka，异步处理
        kafkaTemplate.send(FILE_ANALYZE_TOPIC, String.valueOf(fileId));
        log.info("发送异步解析任务: fileId={}", fileId);
    }

    /**
     * 查找合适的解析器
     *
     * @param fileType 文件类型
     * @return 解析器
     */
    private FileParser findParser(String fileType) {
        for (FileParser parser : fileParsers) {
            if (parser.supports(fileType)) {
                return parser;
            }
        }
        return null;
    }

    /**
     * 缓存解析结果
     *
     * @param storagePath 存储路径
     * @param result      解析结果
     */
    private void cacheParseResult(String storagePath, ParseResultDTO result) {
        try {
            String key = PARSE_STATUS_PREFIX + storagePath;
            redisTemplate.opsForValue().set(key, result.getTextContent(), 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("缓存解析结果失败", e);
        }
    }
}
