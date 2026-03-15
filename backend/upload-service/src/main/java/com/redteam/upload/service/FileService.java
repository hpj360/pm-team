package com.redteam.upload.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.result.PageResult;
import com.redteam.upload.entity.FileEntity;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件服务接口
 *
 * @author 红方团队
 */
public interface FileService extends IService<FileEntity> {

    /**
     * 上传文件
     *
     * @param file        文件
     * @param targetId    目标ID
     * @param tags        标签
     * @param description 描述
     * @param sensitiveLevel 敏感等级
     * @param isPublic    是否公开
     * @return 文件信息
     */
    FileInfoDTO upload(MultipartFile file, Long targetId, String tags, String description, Integer sensitiveLevel, Integer isPublic);

    /**
     * 分片上传初始化
     *
     * @param filename    文件名
     * @param fileSize    文件大小
     * @param fileMd5     文件MD5
     * @param targetId    目标ID
     * @return 上传ID
     */
    String initMultipartUpload(String filename, Long fileSize, String fileMd5, Long targetId);

    /**
     * 分片上传
     *
     * @param uploadId    上传ID
     * @param partNumber  分片序号
     * @param partFile    分片文件
     * @return 分片ETag
     */
    String uploadPart(String uploadId, Integer partNumber, MultipartFile partFile);

    /**
     * 完成分片上传
     *
     * @param uploadId    上传ID
     * @param parts       分片信息
     * @return 文件信息
     */
    FileInfoDTO completeMultipartUpload(String uploadId, String parts);

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
     * @return 预览URL
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
     * 删除文件
     *
     * @param id 文件ID
     * @return 是否成功
     */
    boolean deleteFile(Long id);

    /**
     * 更新文件信息
     *
     * @param id          文件ID
     * @param tags        标签
     * @param description 描述
     * @param sensitiveLevel 敏感等级
     * @param isPublic    是否公开
     * @return 文件信息
     */
    FileInfoDTO updateFileInfo(Long id, String tags, String description, Integer sensitiveLevel, Integer isPublic);

    /**
     * 根据MD5检查文件是否存在
     *
     * @param fileMd5 文件MD5
     * @return 文件信息
     */
    FileInfoDTO checkFileByMd5(String fileMd5);
}
