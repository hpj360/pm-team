package com.redteam.upload.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.enums.Classification;
import com.redteam.common.exception.FileException;
import com.redteam.common.util.FileUtil;
import com.redteam.common.util.UserContext;
import com.redteam.upload.dto.CompleteUploadDTO;
import com.redteam.upload.dto.MultipartUploadInfoVO;
import com.redteam.upload.entity.FileChunkEntity;
import com.redteam.upload.entity.FileEntity;
import com.redteam.upload.entity.UploadTaskEntity;
import com.redteam.upload.mapper.FileChunkMapper;
import com.redteam.upload.mapper.FileMapper;
import com.redteam.upload.mapper.UploadTaskMapper;
import com.redteam.upload.producer.FileEventProducer;
import com.redteam.upload.service.FileService;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 文件服务实现
 *
 * <p>核心能力：</p>
 * <ul>
 *   <li>单文件上传 + SM3 秒传（命中已上传文件直接返回，不重复存储）</li>
 *   <li>分片上传（基于 MinIO 多段上传 + 数据库任务持久化，支持断点续传）</li>
 *   <li>分片上传使用 Redisson 分布式锁防止并发冲突</li>
 *   <li>文件下载、预签名 URL 预览、软删除</li>
 *   <li>通过 FileEventProducer 投递 Kafka 事件触发下游解析与索引</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, FileEntity> implements FileService {

    /**
     * 默认分片大小（5MB），可由 redteam.upload.chunk-size 覆盖
     */
    private static final long DEFAULT_CHUNK_SIZE = 5L * 1024 * 1024;

    /**
     * 分布式锁等待时间（秒）
     */
    private static final long LOCK_WAIT_SECONDS = 5L;

    /**
     * 分布式锁持有时间（秒）
     */
    private static final long LOCK_LEASE_SECONDS = 30L;

    /**
     * 上传状态常量
     */
    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * L6 安全密级标签前缀（v4.2.3 自动密级设置）
     */
    private static final String L6_TAG_SECRET = "L6.SECURITY.CLASSIFICATION.SECRET";
    private static final String L6_TAG_CONFIDENTIAL = "L6.SECURITY.CLASSIFICATION.CONFIDENTIAL";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final MinioClient minioClient;
    private final RedissonClient redissonClient;
    private final FileMapper fileMapper;
    private final FileChunkMapper fileChunkMapper;
    private final UploadTaskMapper uploadTaskMapper;
    private final FileEventProducer fileEventProducer;

    /**
     * MinIO 存储桶名称（与 redteam.upload.bucket-name 一致，回退到 minio.bucket-name）
     */
    @Value("${redteam.upload.bucket-name:${minio.bucket-name:redteam-files}}")
    private String bucketName;

    /**
     * 单分片大小（字节）
     */
    @Value("${redteam.upload.chunk-size:5242880}")
    private Long chunkSize;

    /**
     * 预签名 URL 过期时间（秒）
     */
    @Value("${redteam.upload.preview-expire-seconds:3600}")
    private Integer previewExpireSeconds;

    // ==================== 单文件上传 + 秒传 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoDTO upload(MultipartFile file, Long targetId, String tags, String description,
                              Integer sensitiveLevel, Integer isPublic) {
        if (file == null || file.isEmpty()) {
            throw FileException.uploadError("上传文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (StrUtil.isBlank(originalFilename)) {
            throw FileException.uploadError("文件名不能为空");
        }
        String extension = FileUtil.getExtension(originalFilename);
        if (!FileUtil.isAllowedType(extension)) {
            throw FileException.typeNotSupported();
        }

        try {
            // 计算 SM3 文件指纹（同时兼容性计算 MD5）
            String fileSm3 = computeSm3(file);
            String fileMd5 = DigestUtil.md5Hex(file.getInputStream());

            // 秒传检查：SM3 命中则直接返回
            FileInfoDTO exist = checkFileBySm3(fileSm3);
            if (exist != null) {
                log.info("文件秒传命中: fileSm3={}, fileId={}", fileSm3, exist.getId());
                return exist;
            }

            // 上传到 MinIO
            String filename = FileUtil.generateUniqueFilename(originalFilename);
            String storagePath = FileUtil.generateStoragePath("files", filename);

            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // 写入数据库
            FileEntity entity = new FileEntity();
            entity.setFilename(filename);
            entity.setOriginalFilename(originalFilename);
            entity.setStoragePath(storagePath);
            entity.setFileSize(file.getSize());
            entity.setFileType(extension);
            entity.setMimeType(file.getContentType());
            entity.setFileMd5(fileMd5);
            entity.setFileSm3(fileSm3);
            entity.setSourceType(1);
            entity.setTargetId(targetId);
            entity.setTags(tags);
            entity.setDescription(description);
            entity.setSensitiveLevel(sensitiveLevel != null ? sensitiveLevel : 1);
            entity.setIsPublic(isPublic != null ? isPublic : 0);
            // 自动密级设置：检查 L6 标签，无匹配则默认 PUBLIC
            entity.setClassification(determineClassification(tags));
            entity.setParseStatus(0);
            entity.setIndexStatus(0);
            entity.setDownloadCount(0);
            entity.setPreviewCount(0);
            entity.setUploadStatus(STATUS_COMPLETED);
            fileMapper.insert(entity);

            // 发送 Kafka 事件触发后续解析与索引
            fileEventProducer.sendFileUploadedEvent(entity, currentUserId());

            log.info("文件上传成功: fileId={}, storagePath={}", entity.getId(), storagePath);
            return convertToDTO(entity);
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败: filename={}", originalFilename, e);
            throw FileException.uploadError("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public FileInfoDTO checkFileByMd5(String fileMd5) {
        if (StrUtil.isBlank(fileMd5)) {
            return null;
        }
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEntity::getFileMd5, fileMd5)
                .eq(FileEntity::getUploadStatus, STATUS_COMPLETED);
        FileEntity entity = fileMapper.selectOne(wrapper);
        return entity == null ? null : convertToDTO(entity);
    }

    @Override
    public FileInfoDTO checkFileBySm3(String fileSm3) {
        if (StrUtil.isBlank(fileSm3)) {
            return null;
        }
        LambdaQueryWrapper<FileEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileEntity::getFileSm3, fileSm3)
                .eq(FileEntity::getUploadStatus, STATUS_COMPLETED);
        FileEntity entity = fileMapper.selectOne(wrapper);
        return entity == null ? null : convertToDTO(entity);
    }

    // ==================== 分片上传 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String initMultipartUpload(String filename, Long fileSize, String fileMd5, Long targetId) {
        if (StrUtil.isBlank(filename)) {
            throw FileException.uploadError("文件名不能为空");
        }
        if (fileSize == null || fileSize <= 0) {
            throw FileException.uploadError("文件大小非法");
        }

        // 使用自生成的 uploadId 作为业务主键，避免强依赖 MinIO 多段上传
        // MinIO 对象按 uploadId/{partNumber} 路径分片存储，完成后通过 composeObject 或客户端合并
        String uploadId = cn.hutool.core.util.IdUtil.fastSimpleUUID();
        long chunk = chunkSize != null && chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
        int chunkCount = (int) ((fileSize + chunk - 1) / chunk);

        UploadTaskEntity task = new UploadTaskEntity();
        task.setUploadId(uploadId);
        task.setFileName(filename);
        task.setFileSize(fileSize);
        task.setFileMd5(fileMd5);
        task.setChunkCount(chunkCount);
        task.setChunkSize(chunk);
        task.setTargetId(targetId);
        task.setUserId(currentUserId());
        task.setStatus(STATUS_UPLOADING);
        task.setCompletedChunks(0);
        uploadTaskMapper.insert(task);

        log.info("初始化分片上传: uploadId={}, filename={}, fileSize={}, chunkCount={}",
                uploadId, filename, fileSize, chunkCount);
        return uploadId;
    }

    @Override
    public MultipartUploadInfoVO getMultipartUploadInfo(String uploadId) {
        UploadTaskEntity task = requireUploadTask(uploadId);
        MultipartUploadInfoVO vo = new MultipartUploadInfoVO();
        vo.setUploadId(task.getUploadId());
        vo.setFilename(task.getFileName());
        vo.setFileSize(task.getFileSize());
        vo.setChunkSize(task.getChunkSize());
        vo.setChunkCount(task.getChunkCount());
        vo.setCompletedChunks(task.getCompletedChunks());
        vo.setStatus(task.getStatus());

        List<FileChunkEntity> chunks = listChunks(uploadId);
        List<Integer> uploaded = chunks.stream()
                .filter(FileChunkEntity::getUploaded)
                .map(FileChunkEntity::getChunkNumber)
                .sorted()
                .collect(Collectors.toList());
        vo.setUploadedChunks(uploaded);
        return vo;
    }

    @Override
    public String uploadPart(String uploadId, Integer partNumber, MultipartFile partFile) {
        if (StrUtil.isBlank(uploadId)) {
            throw FileException.uploadError("上传ID不能为空");
        }
        if (partNumber == null || partNumber < 1) {
            throw FileException.uploadError("分片序号非法");
        }
        if (partFile == null || partFile.isEmpty()) {
            throw FileException.uploadError("分片内容不能为空");
        }

        UploadTaskEntity task = requireUploadTask(uploadId);
        if (STATUS_COMPLETED.equals(task.getStatus()) || STATUS_CANCELLED.equals(task.getStatus())) {
            throw FileException.uploadError("上传任务已结束，无法继续上传: status=" + task.getStatus());
        }

        String lockKey = "upload:part:" + uploadId + ":" + partNumber;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw FileException.uploadError("获取分片上传锁失败，请稍后重试: " + lockKey);
            }

            ensureBucketExists();
            String storagePath = chunkStoragePath(uploadId, partNumber);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(storagePath)
                    .stream(partFile.getInputStream(), partFile.getSize(), -1)
                    .build());

            String etag = DigestUtil.md5Hex(partFile.getInputStream());

            // 写入或更新分片记录（同 uploadId + partNumber 唯一）
            FileChunkEntity exist = findChunk(uploadId, partNumber);
            if (exist == null) {
                FileChunkEntity chunk = new FileChunkEntity();
                chunk.setFileId(null);
                chunk.setUploadId(uploadId);
                chunk.setChunkNumber(partNumber);
                chunk.setChunkSize(partFile.getSize());
                chunk.setEtag(etag);
                chunk.setUploaded(true);
                chunk.setUploadedAt(LocalDateTime.now());
                fileChunkMapper.insert(chunk);
            } else {
                exist.setChunkSize(partFile.getSize());
                exist.setEtag(etag);
                exist.setUploaded(true);
                exist.setUploadedAt(LocalDateTime.now());
                fileChunkMapper.updateById(exist);
            }

            // 更新任务已上传分片数
            int completed = countUploadedChunks(uploadId);
            task.setCompletedChunks(completed);
            uploadTaskMapper.updateById(task);

            log.info("分片上传成功: uploadId={}, partNumber={}, etag={}", uploadId, partNumber, etag);
            return etag;
        } catch (FileException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw FileException.uploadError("获取分片上传锁被中断: " + lockKey);
        } catch (Exception e) {
            log.error("分片上传失败: uploadId={}, partNumber={}", uploadId, partNumber, e);
            throw FileException.uploadError("分片上传失败: " + e.getMessage());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.warn("释放分布式锁异常: key={}", lockKey, e);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoDTO completeMultipartUpload(String uploadId, String parts) {
        UploadTaskEntity task = requireUploadTask(uploadId);
        if (STATUS_COMPLETED.equals(task.getStatus())) {
            throw FileException.uploadError("上传任务已完成，请勿重复提交");
        }
        if (STATUS_CANCELLED.equals(task.getStatus())) {
            throw FileException.uploadError("上传任务已取消，无法完成");
        }

        // 解析 parts JSON
        List<CompleteUploadDTO.PartETag> partList = parseParts(parts);
        if (partList.isEmpty()) {
            // 若前端未传 parts，则从数据库读取已上传分片
            partList = listChunks(uploadId).stream()
                    .filter(FileChunkEntity::getUploaded)
                    .map(c -> new CompleteUploadDTO.PartETag(c.getChunkNumber(), c.getEtag()))
                    .collect(Collectors.toList());
        }

        // 校验分片完整性
        if (partList.size() != task.getChunkCount()) {
            throw FileException.uploadError("分片不完整：期望 " + task.getChunkCount()
                    + " 片，实际 " + partList.size() + " 片");
        }

        try {
            ensureBucketExists();
            // 合并分片：优先使用 MinIO composeObject API（服务端合并，性能更优），
            // 失败时降级到应用层手动下载分片并拼接
            String mergedPath = FileUtil.generateStoragePath("files",
                    cn.hutool.core.util.IdUtil.fastSimpleUUID() + "_" + task.getFileName());

            MergedFile merged = mergeChunks(uploadId, mergedPath, partList);
            String mergedSm3 = merged.sm3;
            long mergedSize = merged.size;
            FileInfoDTO exist = checkFileBySm3(mergedSm3);
            if (exist != null) {
                // 秒传命中：删除新上传合并对象与分片
                safeRemoveObject(mergedPath);
                cleanupChunks(uploadId);
                markTaskStatus(task, STATUS_COMPLETED, mergedSm3);
                log.info("分片合并后秒传命中: uploadId={}, existFileId={}", uploadId, exist.getId());
                return exist;
            }

            // 写入 t_file 记录
            String extension = FileUtil.getExtension(task.getFileName());
            FileEntity entity = new FileEntity();
            entity.setFilename(cn.hutool.core.util.IdUtil.fastSimpleUUID()
                    + (StrUtil.isBlank(extension) ? "" : "." + extension));
            entity.setOriginalFilename(task.getFileName());
            entity.setStoragePath(mergedPath);
            entity.setFileSize(mergedSize);
            entity.setFileType(extension);
            entity.setMimeType(FileUtil.getMimeType(extension));
            entity.setFileMd5(task.getFileMd5());
            entity.setFileSm3(mergedSm3);
            entity.setUploadId(uploadId);
            entity.setChunkCount(task.getChunkCount());
            entity.setChunkSize(task.getChunkSize());
            entity.setSourceType(1);
            entity.setTargetId(task.getTargetId());
            entity.setSensitiveLevel(1);
            entity.setIsPublic(0);
            // 分片上传默认 PUBLIC 密级（分片上传任务未携带 tags，由管理员后续调整）
            entity.setClassification(Classification.PUBLIC.getCode());
            entity.setParseStatus(0);
            entity.setIndexStatus(0);
            entity.setDownloadCount(0);
            entity.setPreviewCount(0);
            entity.setUploadStatus(STATUS_COMPLETED);
            fileMapper.insert(entity);

            // 更新分片记录关联 fileId
            updateChunkFileId(uploadId, entity.getId());

            // 更新任务状态
            markTaskStatus(task, STATUS_COMPLETED, mergedSm3);

            // 发送 Kafka 事件
            fileEventProducer.sendFileUploadedEvent(entity, task.getUserId());

            // 清理 MinIO 中的分片对象（数据库记录保留以便审计）
            cleanupChunkObjects(uploadId, task.getChunkCount());

            log.info("分片上传完成: uploadId={}, fileId={}", uploadId, entity.getId());
            return convertToDTO(entity);
        } catch (FileException e) {
            markTaskStatus(task, STATUS_FAILED, null);
            throw e;
        } catch (Exception e) {
            log.error("完成分片上传失败: uploadId={}", uploadId, e);
            markTaskStatus(task, STATUS_FAILED, null);
            throw FileException.uploadError("完成分片上传失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelMultipartUpload(String uploadId) {
        UploadTaskEntity task = requireUploadTask(uploadId);
        try {
            // 清理 MinIO 中已上传分片
            cleanupChunkObjects(uploadId, task.getChunkCount());
        } catch (Exception e) {
            log.warn("取消上传时清理 MinIO 分片失败: uploadId={}", uploadId, e);
        }
        // 删除数据库分片记录
        cleanupChunks(uploadId);
        // 更新任务状态
        task.setStatus(STATUS_CANCELLED);
        task.setUpdatedAt(LocalDateTime.now());
        uploadTaskMapper.updateById(task);
        log.info("分片上传已取消: uploadId={}", uploadId);
    }

    // ==================== 下载 / 预览 / 详情 / 删除 / 更新 ====================

    @Override
    public void download(Long id, HttpServletResponse response) {
        FileEntity entity = requireFile(id);
        try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(entity.getStoragePath())
                .build())) {
            response.setContentType(entity.getMimeType());
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode(StrUtil.blankToDefault(entity.getOriginalFilename(), entity.getFilename()),
                            StandardCharsets.UTF_8));
            if (entity.getFileSize() != null) {
                response.setHeader("Content-Length", String.valueOf(entity.getFileSize()));
            }
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                response.getOutputStream().write(buffer, 0, bytesRead);
            }
            response.getOutputStream().flush();

            // 更新下载次数（非主流程，失败不影响下载）
            try {
                entity.setDownloadCount((entity.getDownloadCount() == null ? 0 : entity.getDownloadCount()) + 1);
                fileMapper.updateById(entity);
            } catch (Exception e) {
                log.warn("更新下载次数失败: fileId={}", id, e);
            }
        } catch (FileException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件下载失败: fileId={}", id, e);
            throw FileException.uploadError("文件下载失败: " + e.getMessage());
        }
    }

    @Override
    public String getPreviewUrl(Long id) {
        FileEntity entity = requireFile(id);
        try {
            // 更新预览次数
            try {
                entity.setPreviewCount((entity.getPreviewCount() == null ? 0 : entity.getPreviewCount()) + 1);
                fileMapper.updateById(entity);
            } catch (Exception e) {
                log.warn("更新预览次数失败: fileId={}", id, e);
            }
            int expire = previewExpireSeconds != null && previewExpireSeconds > 0 ? previewExpireSeconds : 3600;
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(entity.getStoragePath())
                    .expiry(expire, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("获取预览URL失败: fileId={}", id, e);
            throw FileException.uploadError("获取预览URL失败: " + e.getMessage());
        }
    }

    @Override
    public FileInfoDTO getFileInfo(Long id) {
        return convertToDTO(requireFile(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long id) {
        FileEntity entity = requireFile(id);
        // 软删除数据库记录
        fileMapper.deleteById(id);
        // 通过 Kafka 事件触发 MinIO 对象异步清理（保留审计，由消费者决定是否物理删除）
        try {
            fileEventProducer.sendFileDeletedEvent(entity, currentUserId());
        } catch (Exception e) {
            log.warn("发送文件删除事件失败: fileId={}", id, e);
        }
        // 清理分片记录
        try {
            cleanupChunksByFileId(id);
        } catch (Exception e) {
            log.warn("清理分片记录失败: fileId={}", id, e);
        }
        log.info("文件已软删除: fileId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoDTO updateFileInfo(Long id, String tags, String description,
                                      Integer sensitiveLevel, Integer isPublic) {
        FileEntity entity = requireFile(id);
        if (StrUtil.isNotBlank(tags)) {
            entity.setTags(tags);
        }
        if (StrUtil.isNotBlank(description)) {
            entity.setDescription(description);
        }
        if (sensitiveLevel != null) {
            entity.setSensitiveLevel(sensitiveLevel);
        }
        if (isPublic != null) {
            entity.setIsPublic(isPublic);
        }
        fileMapper.updateById(entity);
        return convertToDTO(entity);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 确保 MinIO 存储桶存在
     */
    private void ensureBucketExists() throws Exception {
        try {
            minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.warn("检查存储桶失败，将尝试创建: bucket={}", bucketName, e);
            minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 合并分片：优先使用 MinIO composeObject API（服务端合并），失败时降级到应用层合并
     *
     * <p>composeObject 要求每个源对象（最后一片除外）不小于 5MB，当分片大小不满足或
     * MinIO 版本不支持时会抛出异常，此时自动降级到应用层手动下载拼接逻辑。</p>
     *
     * @param uploadId   上传ID
     * @param mergedPath 合并后对象在 MinIO 中的存储路径
     * @param partList   分片列表（已校验完整性）
     * @return 合并结果（包含 SM3 指纹与文件大小）
     * @throws Exception 合并过程发生异常
     */
    private MergedFile mergeChunks(String uploadId, String mergedPath,
                                   List<CompleteUploadDTO.PartETag> partList) throws Exception {
        // 尝试使用 composeObject API（服务端合并，性能更优）
        try {
            partList.sort(Comparator.comparing(CompleteUploadDTO.PartETag::getPartNumber));
            List<ComposeSource> sources = partList.stream()
                    .map(part -> ComposeSource.builder()
                            .bucket(bucketName)
                            .object(chunkStoragePath(uploadId, part.getPartNumber()))
                            .build())
                    .collect(Collectors.toList());

            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucketName)
                    .object(mergedPath)
                    .sources(sources)
                    .build());

            // 获取合并后对象大小
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(mergedPath)
                    .build());
            long mergedSize = stat.size();

            // 流式计算 SM3（用于秒传校验，无需将整个文件加载到内存）
            String mergedSm3;
            try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(mergedPath)
                    .build())) {
                mergedSm3 = computeSm3(in);
            }

            log.info("使用 composeObject API 合并分片成功: uploadId={}, mergedPath={}, size={}",
                    uploadId, mergedPath, mergedSize);
            return new MergedFile(mergedSm3, mergedSize);
        } catch (Exception e) {
            log.warn("composeObject 合并失败，降级到应用层合并: uploadId={}", uploadId, e);
        }

        // 降级路径：应用层手动下载分片并拼接
        byte[] mergedBytes = mergeChunksByAppLayer(uploadId, partList);
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucketName)
                .object(mergedPath)
                .stream(new java.io.ByteArrayInputStream(mergedBytes), mergedBytes.length, -1)
                .build());
        log.info("应用层合并分片完成（降级路径）: uploadId={}, mergedPath={}, size={}",
                uploadId, mergedPath, mergedBytes.length);
        return new MergedFile(computeSm3(mergedBytes), mergedBytes.length);
    }

    /**
     * 应用层合并：按 partNumber 顺序从 MinIO 下载分片并拼接为字节数组
     *
     * @param uploadId 上传ID
     * @param partList 已按 partNumber 排序的分片列表
     * @return 合并后的字节数组
     * @throws Exception 下载或拼接过程发生异常
     */
    private byte[] mergeChunksByAppLayer(String uploadId,
                                         List<CompleteUploadDTO.PartETag> partList) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        for (CompleteUploadDTO.PartETag part : partList) {
            String chunkPath = chunkStoragePath(uploadId, part.getPartNumber());
            try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(chunkPath)
                    .build())) {
                in.transferTo(baos);
            }
        }
        return baos.toByteArray();
    }

    /**
     * 合并结果持有者，封装 SM3 指纹与文件大小
     */
    private static final class MergedFile {
        /** 合并后文件的 SM3 指纹 */
        final String sm3;
        /** 合并后文件大小（字节） */
        final long size;

        MergedFile(String sm3, long size) {
            this.sm3 = sm3;
            this.size = size;
        }
    }

    /**
     * 计算文件 SM3（流式）
     */
    private String computeSm3(MultipartFile file) throws Exception {
        try (InputStream in = file.getInputStream()) {
            return computeSm3(in);
        }
    }

    /**
     * 计算输入流 SM3（流式，使用 BouncyCastle Provider）
     */
    private String computeSm3(InputStream in) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SM3", BouncyCastleProvider.PROVIDER_NAME);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            md.update(buffer, 0, bytesRead);
        }
        return toHex(md.digest());
    }

    /**
     * 计算字节数组 SM3
     */
    private String computeSm3(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SM3", BouncyCastleProvider.PROVIDER_NAME);
        return toHex(md.digest(bytes));
    }

    /**
     * 字节数组转十六进制小写字符串
     */
    private String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        String hex = "0123456789abcdef";
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = hex.charAt(v >>> 4);
            hexChars[i * 2 + 1] = hex.charAt(v & 0x0F);
        }
        return new String(hexChars);
    }

    /**
     * 解析 parts JSON 为 PartETag 列表
     */
    private List<CompleteUploadDTO.PartETag> parseParts(String parts) {
        if (StrUtil.isBlank(parts)) {
            return new ArrayList<>();
        }
        try {
            List<CompleteUploadDTO.PartETag> list = JSONUtil.toList(parts, CompleteUploadDTO.PartETag.class);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            log.warn("解析 parts JSON 失败，将使用数据库分片记录: parts={}", parts, e);
            return new ArrayList<>();
        }
    }

    /**
     * 分片在 MinIO 中的存储路径
     */
    private String chunkStoragePath(String uploadId, Integer partNumber) {
        return "chunks/" + uploadId + "/" + partNumber;
    }

    /**
     * 查询上传任务（不存在则抛异常）
     */
    private UploadTaskEntity requireUploadTask(String uploadId) {
        if (StrUtil.isBlank(uploadId)) {
            throw FileException.uploadError("上传ID不能为空");
        }
        LambdaQueryWrapper<UploadTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UploadTaskEntity::getUploadId, uploadId);
        UploadTaskEntity task = uploadTaskMapper.selectOne(wrapper);
        if (task == null) {
            throw FileException.uploadError("上传任务不存在或已过期: " + uploadId);
        }
        return task;
    }

    /**
     * 查询文件（不存在则抛异常）
     */
    private FileEntity requireFile(Long id) {
        if (id == null) {
            throw FileException.notFound();
        }
        FileEntity entity = fileMapper.selectById(id);
        if (entity == null) {
            throw FileException.notFound();
        }
        return entity;
    }

    /**
     * 列出上传任务的所有分片记录
     */
    private List<FileChunkEntity> listChunks(String uploadId) {
        LambdaQueryWrapper<FileChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileChunkEntity::getUploadId, uploadId);
        return fileChunkMapper.selectList(wrapper);
    }

    /**
     * 查询单个分片记录
     */
    private FileChunkEntity findChunk(String uploadId, Integer partNumber) {
        LambdaQueryWrapper<FileChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileChunkEntity::getUploadId, uploadId)
                .eq(FileChunkEntity::getChunkNumber, partNumber);
        return fileChunkMapper.selectOne(wrapper);
    }

    /**
     * 统计已上传分片数
     */
    private int countUploadedChunks(String uploadId) {
        LambdaQueryWrapper<FileChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileChunkEntity::getUploadId, uploadId)
                .eq(FileChunkEntity::getUploaded, true);
        Long count = fileChunkMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    /**
     * 更新分片记录的 fileId
     */
    private void updateChunkFileId(String uploadId, Long fileId) {
        LambdaQueryWrapper<FileChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileChunkEntity::getUploadId, uploadId);
        List<FileChunkEntity> chunks = fileChunkMapper.selectList(wrapper);
        for (FileChunkEntity c : chunks) {
            c.setFileId(fileId);
            fileChunkMapper.updateById(c);
        }
    }

    /**
     * 清理分片数据库记录
     */
    private void cleanupChunks(String uploadId) {
        LambdaQueryWrapper<FileChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileChunkEntity::getUploadId, uploadId);
        fileChunkMapper.delete(wrapper);
    }

    /**
     * 按 fileId 清理分片记录
     */
    private void cleanupChunksByFileId(Long fileId) {
        LambdaQueryWrapper<FileChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FileChunkEntity::getFileId, fileId);
        fileChunkMapper.delete(wrapper);
    }

    /**
     * 清理 MinIO 中的分片对象
     */
    private void cleanupChunkObjects(String uploadId, int chunkCount) {
        for (int i = 1; i <= chunkCount; i++) {
            safeRemoveObject(chunkStoragePath(uploadId, i));
        }
    }

    /**
     * 安全删除 MinIO 对象（失败仅记录日志）
     */
    private void safeRemoveObject(String path) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(path)
                    .build());
        } catch (Exception e) {
            log.warn("删除 MinIO 对象失败: path={}", path, e);
        }
    }

    /**
     * 标记上传任务状态
     */
    private void markTaskStatus(UploadTaskEntity task, String status, String fileSm3) {
        task.setStatus(status);
        if (StrUtil.isNotBlank(fileSm3)) {
            task.setFileSm3(fileSm3);
        }
        task.setUpdatedAt(LocalDateTime.now());
        try {
            uploadTaskMapper.updateById(task);
        } catch (Exception e) {
            log.warn("更新上传任务状态失败: uploadId={}, status={}", task.getUploadId(), status, e);
        }
    }

    /**
     * 获取当前操作用户ID（未登录返回 null）
     */
    private Long currentUserId() {
        try {
            return UserContext.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 根据上传标签自动判定文件密级（v4.2.3 新增）
     *
     * <p>判定规则：</p>
     * <ul>
     *   <li>标签包含 {@code L6.SECURITY.CLASSIFICATION.SECRET} → SECRET</li>
     *   <li>标签包含 {@code L6.SECURITY.CLASSIFICATION.CONFIDENTIAL} → CONFIDENTIAL</li>
     *   <li>其他情况（含 tags 为空）→ PUBLIC（由管理员后续手动调整）</li>
     * </ul>
     *
     * @param tags 上传时传入的标签字符串
     * @return 密级编码
     */
    private String determineClassification(String tags) {
        if (StrUtil.isBlank(tags)) {
            return Classification.PUBLIC.getCode();
        }
        if (tags.contains(L6_TAG_SECRET)) {
            return Classification.SECRET.getCode();
        }
        if (tags.contains(L6_TAG_CONFIDENTIAL)) {
            return Classification.CONFIDENTIAL.getCode();
        }
        return Classification.PUBLIC.getCode();
    }

    /**
     * 实体转 DTO
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
