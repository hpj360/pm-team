package com.redteam.upload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传任务实体
 *
 * <p>对应数据库表 t_upload_task，持久化分片上传任务信息，支持断点续传。</p>
 *
 * @author 红方团队
 */
@Data
@TableName("t_upload_task")
public class UploadTaskEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 上传ID（MinIO 多段上传 ID）
     */
    private String uploadId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件MD5（兼容性字段）
     */
    private String fileMd5;

    /**
     * 文件SM3（完成后填充）
     */
    private String fileSm3;

    /**
     * 分片总数
     */
    private Integer chunkCount;

    /**
     * 单分片大小（字节）
     */
    private Long chunkSize;

    /**
     * 关联目标ID
     */
    private Long targetId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 任务状态（UPLOADING/COMPLETED/FAILED/CANCELLED）
     */
    private String status;

    /**
     * 已上传分片数
     */
    private Integer completedChunks;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
