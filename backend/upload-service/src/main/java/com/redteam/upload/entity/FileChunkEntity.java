package com.redteam.upload.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件分片记录实体
 *
 * <p>对应数据库表 t_file_chunk，用于断点续传场景下记录每个分片的上传状态。</p>
 *
 * @author 红方团队
 */
@Data
@TableName("t_file_chunk")
public class FileChunkEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联文件ID
     */
    private Long fileId;

    /**
     * 上传ID（MinIO 多段上传 ID）
     */
    private String uploadId;

    /**
     * 分片序号（从 1 开始）
     */
    private Integer chunkNumber;

    /**
     * 分片大小（字节）
     */
    private Long chunkSize;

    /**
     * 分片 ETag
     */
    private String etag;

    /**
     * 是否已上传
     */
    private Boolean uploaded;

    /**
     * 上传时间
     */
    private LocalDateTime uploadedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
