package com.redteam.upload.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.upload.dto.MultipartUploadInfoVO;
import com.redteam.upload.entity.FileEntity;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务接口
 *
 * <p>支持单文件秒传（SM3 去重）、分片上传（断点续传）、文件下载/预览等能力。</p>
 *
 * @author 红方团队
 */
public interface FileService extends IService<FileEntity> {

    /**
     * 上传单个文件
     *
     * <p>内部先计算文件 SM3 哈希，命中已上传文件则实现秒传；
     * 否则上传到 MinIO 并写入数据库，发送 Kafka 事件触发后续解析与索引。</p>
     *
     * @param file           文件
     * @param targetId       目标ID
     * @param tags           标签
     * @param description    描述
     * @param sensitiveLevel 敏感等级（1-低,2-中,3-高）
     * @param isPublic       是否公开（0-否,1-是）
     * @return 文件信息
     */
    FileInfoDTO upload(MultipartFile file, Long targetId, String tags, String description, Integer sensitiveLevel, Integer isPublic);

    /**
     * 根据MD5检查文件是否存在（秒传检查，兼容入口）
     *
     * @param fileMd5 文件MD5
     * @return 文件信息；不存在返回 null
     */
    FileInfoDTO checkFileByMd5(String fileMd5);

    /**
     * 根据SM3检查文件是否存在（秒传检查，主用入口）
     *
     * @param fileSm3 文件SM3
     * @return 文件信息；不存在返回 null
     */
    FileInfoDTO checkFileBySm3(String fileSm3);

    /**
     * 初始化分片上传
     *
     * <p>调用 MinIO initiateMultipartUpload 获取 uploadId，并落库到 t_upload_task 表。
     * 计算分片数：chunkSize 默认 5MB（可配置）。</p>
     *
     * @param filename 文件名
     * @param fileSize 文件大小
     * @param fileMd5  文件MD5（兼容性字段，可空）
     * @param targetId 目标ID
     * @return MinIO 多段上传 uploadId
     */
    String initMultipartUpload(String filename, Long fileSize, String fileMd5, Long targetId);

    /**
     * 查询分片上传进度（断点续传）
     *
     * <p>从 MinIO listParts 与 t_file_chunk 表综合获取已上传分片序号。</p>
     *
     * @param uploadId 上传ID
     * @return 分片上传信息
     */
    MultipartUploadInfoVO getMultipartUploadInfo(String uploadId);

    /**
     * 上传单个分片
     *
     * <p>使用 Redisson 分布式锁防止并发冲突（key: upload:part:{uploadId}:{partNumber}）。</p>
     *
     * @param uploadId   上传ID
     * @param partNumber 分片序号（从 1 开始）
     * @param partFile   分片文件
     * @return 分片ETag
     */
    String uploadPart(String uploadId, Integer partNumber, MultipartFile partFile);

    /**
     * 完成分片上传
     *
     * <p>合并所有分片；异步计算合并后 SM3；若 SM3 命中秒传，删除新上传文件并返回旧记录。</p>
     *
     * @param uploadId 上传ID
     * @param parts    分片信息（JSON 数组：[{"partNumber":1,"eTag":"..."}]）
     * @return 文件信息
     */
    FileInfoDTO completeMultipartUpload(String uploadId, String parts);

    /**
     * 取消分片上传
     *
     * <p>调用 MinIO abortMultipartUpload，清理分片记录与上传任务。</p>
     *
     * @param uploadId 上传ID
     */
    void cancelMultipartUpload(String uploadId);

    /**
     * 下载文件
     *
     * @param id       文件ID
     * @param response HTTP响应
     */
    void download(Long id, HttpServletResponse response);

    /**
     * 获取文件预览URL
     *
     * @param id 文件ID
     * @return 预签名URL（默认 1 小时有效）
     */
    String getPreviewUrl(Long id);

    /**
     * 获取文件详情
     *
     * @param id 文件ID
     * @return 文件信息
     */
    FileInfoDTO getFileInfo(Long id);

    /**
     * 删除文件（软删除）
     *
     * <p>逻辑删除数据库记录；通过 Kafka 事件触发 MinIO 对象异步清理。</p>
     *
     * @param id 文件ID
     */
    void deleteFile(Long id);

    /**
     * 更新文件信息
     *
     * @param id             文件ID
     * @param tags           标签
     * @param description    描述
     * @param sensitiveLevel 敏感等级
     * @param isPublic       是否公开
     * @return 文件信息
     */
    FileInfoDTO updateFileInfo(Long id, String tags, String description, Integer sensitiveLevel, Integer isPublic);
}
