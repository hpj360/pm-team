package com.redteam.upload.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.exception.FileException;
import com.redteam.common.result.ResultCode;
import com.redteam.common.util.FileUtil;
import com.redteam.common.util.UserContext;
import com.redteam.upload.entity.FileEntity;
import com.redteam.upload.mapper.FileMapper;
import com.redteam.upload.service.FileService;
import io.minio.*;
import io.minio.http.Method;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 文件服务实现类
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, FileEntity> implements FileService {

    private final MinioClient minioClient;
    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.presigned-expiry}")
    private Integer presignedExpiry;

    private static final String UPLOAD_INFO_PREFIX = "upload:info:";
    private static final String FILE_PARSE_TOPIC = "file-parse-topic";
    private static final String FILE_INDEX_TOPIC = "file-index-topic";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoDTO upload(MultipartFile file, Long targetId, String tags, String description, Integer sensitiveLevel, Integer isPublic) {
        try {
            // 获取原始文件名
            String originalFilename = file.getOriginalFilename();
            if (StrUtil.isBlank(originalFilename)) {
                throw FileException.uploadError("文件名不能为空");
            }

            // 获取文件扩展名
            String extension = FileUtil.getExtension(originalFilename);
            if (!FileUtil.isAllowedType(extension)) {
                throw FileException.typeNotSupported();
            }

            // 计算文件MD5和SHA256
            String fileMd5 = cn.hutool.crypto.digest.DigestUtil.md5Hex(file.getInputStream());
            String fileSha256 = cn.hutool.crypto.digest.DigestUtil.sha256Hex(file.getInputStream());

            // 检查文件是否已存在（秒传）
            LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(FileEntity::getFileMd5, fileMd5);
            FileEntity existFile = this.getOne(queryWrapper);
            if (existFile != null) {
                log.info("文件已存在，实现秒传: {}", fileMd5);
                return convertToDTO(existFile);
            }

            // 生成唯一文件名
            String filename = FileUtil.generateUniqueFilename(originalFilename);

            // 生成存储路径
            String storagePath = FileUtil.generateStoragePath("files", filename);

            // 确保存储桶存在
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
            }

            // 上传文件到MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // 保存文件信息到数据库
            FileEntity fileEntity = new FileEntity();
            fileEntity.setFilename(filename);
            fileEntity.setOriginalFilename(originalFilename);
            fileEntity.setStoragePath(storagePath);
            fileEntity.setFileSize(file.getSize());
            fileEntity.setFileType(extension);
            fileEntity.setMimeType(file.getContentType());
            fileEntity.setFileMd5(fileMd5);
            fileEntity.setFileSha256(fileSha256);
            fileEntity.setSourceType(1); // 上传
            fileEntity.setTargetId(targetId);
            fileEntity.setTags(tags);
            fileEntity.setDescription(description);
            fileEntity.setSensitiveLevel(sensitiveLevel != null ? sensitiveLevel : 1);
            fileEntity.setIsPublic(isPublic != null ? isPublic : 0);
            fileEntity.setParseStatus(0); // 未解析
            fileEntity.setIndexStatus(0); // 未索引
            fileEntity.setDownloadCount(0);
            fileEntity.setPreviewCount(0);

            this.save(fileEntity);

            // 发送消息到Kafka，触发文件解析和索引
            sendFileMessage(fileEntity.getId(), FILE_PARSE_TOPIC);
            sendFileMessage(fileEntity.getId(), FILE_INDEX_TOPIC);

            log.info("文件上传成功: {}", filename);

            return convertToDTO(fileEntity);
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败", e);
            throw FileException.uploadError("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public String initMultipartUpload(String filename, Long fileSize, String fileMd5, Long targetId) {
        // 生成上传ID
        String uploadId = cn.hutool.core.util.IdUtil.fastSimpleUUID();

        // 保存上传信息到Redis
        Map<String, String> uploadInfo = new HashMap<>();
        uploadInfo.put("filename", filename);
        uploadInfo.put("fileSize", String.valueOf(fileSize));
        uploadInfo.put("fileMd5", fileMd5);
        uploadInfo.put("targetId", String.valueOf(targetId));
        uploadInfo.put("createTime", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForHash().putAll(UPLOAD_INFO_PREFIX + uploadId, uploadInfo);
        redisTemplate.expire(UPLOAD_INFO_PREFIX + uploadId, 24, TimeUnit.HOURS);

        return uploadId;
    }

    @Override
    public String uploadPart(String uploadId, Integer partNumber, MultipartFile partFile) {
        try {
            // 获取上传信息
            Map<Object, Object> uploadInfo = redisTemplate.opsForHash().entries(UPLOAD_INFO_PREFIX + uploadId);
            if (uploadInfo.isEmpty()) {
                throw FileException.uploadError("上传任务不存在或已过期");
            }

            String filename = (String) uploadInfo.get("filename");
            String storagePath = FileUtil.generateStoragePath("chunks", uploadId + "/" + partNumber);

            // 上传分片到MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .stream(partFile.getInputStream(), partFile.getSize(), -1)
                    .build());

            return cn.hutool.crypto.digest.DigestUtil.md5Hex(partFile.getInputStream());
        } catch (Exception e) {
            log.error("分片上传失败", e);
            throw FileException.uploadError("分片上传失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoDTO completeMultipartUpload(String uploadId, String parts) {
        // TODO: 实现分片合并逻辑
        throw FileException.uploadError("分片上传功能开发中");
    }

    @Override
    public void download(Long id, HttpServletResponse response) {
        try {
            FileEntity fileEntity = this.getById(id);
            if (fileEntity == null) {
                throw FileException.notFound();
            }

            // 获取文件流
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileEntity.getStoragePath())
                    .build();

            try (InputStream inputStream = minioClient.getObject(getObjectArgs)) {
                // 设置响应头
                response.setContentType(fileEntity.getMimeType());
                response.setHeader("Content-Disposition", "attachment;filename=" +
                        URLEncoder.encode(fileEntity.getOriginalFilename(), StandardCharsets.UTF_8));
                response.setHeader("Content-Length", String.valueOf(fileEntity.getFileSize()));

                // 写入响应流
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, bytesRead);
                }
                response.getOutputStream().flush();

                // 更新下载次数
                fileEntity.setDownloadCount(fileEntity.getDownloadCount() + 1);
                this.updateById(fileEntity);
            }
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件下载失败", e);
            throw FileException.uploadError("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public String getPreviewUrl(Long id) {
        try {
            FileEntity fileEntity = this.getById(id);
            if (fileEntity == null) {
                throw FileException.notFound();
            }

            // 更新预览次数
            fileEntity.setPreviewCount(fileEntity.getPreviewCount() + 1);
            this.updateById(fileEntity);

            // 生成预签名URL
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(fileEntity.getStoragePath())
                    .expiry(presignedExpiry, TimeUnit.SECONDS)
                    .build());
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取预览URL失败", e);
            throw FileException.uploadError("获取预览URL失败: " + e.getMessage());
        }
    }

    @Override
    public FileInfoDTO getFileInfo(Long id) {
        FileEntity fileEntity = this.getById(id);
        if (fileEntity == null) {
            throw FileException.notFound();
        }
        return convertToDTO(fileEntity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteFile(Long id) {
        try {
            FileEntity fileEntity = this.getById(id);
            if (fileEntity == null) {
                throw FileException.notFound();
            }

            // 删除MinIO中的文件
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(fileEntity.getStoragePath())
                    .build());

            // 逻辑删除数据库记录
            return this.removeById(id);
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件删除失败", e);
            throw FileException.uploadError("文件删除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoDTO updateFileInfo(Long id, String tags, String description, Integer sensitiveLevel, Integer isPublic) {
        FileEntity fileEntity = this.getById(id);
        if (fileEntity == null) {
            throw FileException.notFound();
        }

        if (StrUtil.isNotBlank(tags)) {
            fileEntity.setTags(tags);
        }
        if (StrUtil.isNotBlank(description)) {
            fileEntity.setDescription(description);
        }
        if (sensitiveLevel != null) {
            fileEntity.setSensitiveLevel(sensitiveLevel);
        }
        if (isPublic != null) {
            fileEntity.setIsPublic(isPublic);
        }

        this.updateById(fileEntity);
        return convertToDTO(fileEntity);
    }

    @Override
    public FileInfoDTO checkFileByMd5(String fileMd5) {
        LambdaQueryWrapper<FileEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FileEntity::getFileMd5, fileMd5);
        FileEntity fileEntity = this.getOne(queryWrapper);
        if (fileEntity != null) {
            return convertToDTO(fileEntity);
        }
        return null;
    }

    /**
     * 发送文件消息到Kafka
     *
     * @param fileId 文件ID
     * @param topic  主题
     */
    private void sendFileMessage(Long fileId, String topic) {
        try {
            kafkaTemplate.send(topic, String.valueOf(fileId));
            log.debug("发送文件消息到Kafka: fileId={}, topic={}", fileId, topic);
        } catch (Exception e) {
            log.error("发送文件消息失败", e);
        }
    }

    /**
     * 实体转DTO
     *
     * @param entity 实体
     * @return DTO
     */
    private FileInfoDTO convertToDTO(FileEntity entity) {
        FileInfoDTO dto = new FileInfoDTO();
        dto.setId(entity.getId());
        dto.setFilename(entity.getFilename());
        dto.setOriginalFilename(entity.getOriginalFilename());
        dto.setStoragePath(entity.getStoragePath());
        dto.setFileSize(entity.getFileSize());
        dto.setFileType(entity.getFileType());
        dto.setMimeType(entity.getMimeType());
        dto.setFileMd5(entity.getFileMd5());
        dto.setFileSha256(entity.getFileSha256());
        dto.setSourceType(entity.getSourceType());
        dto.setSourceUrl(entity.getSourceUrl());
        dto.setTargetId(entity.getTargetId());
        dto.setTags(entity.getTags());
        dto.setDescription(entity.getDescription());
        dto.setSensitiveLevel(entity.getSensitiveLevel());
        dto.setIsPublic(entity.getIsPublic());
        dto.setParseStatus(entity.getParseStatus());
        dto.setIndexStatus(entity.getIndexStatus());
        dto.setDownloadCount(entity.getDownloadCount());
        dto.setPreviewCount(entity.getPreviewCount());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }
}
