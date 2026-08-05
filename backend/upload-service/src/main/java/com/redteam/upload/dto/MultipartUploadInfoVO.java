package com.redteam.upload.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分片上传进度信息 VO
 *
 * <p>用于断点续传场景下返回已上传分片序号及整体进度。</p>
 *
 * @author 红方团队
 */
@Data
@Schema(description = "分片上传进度信息")
public class MultipartUploadInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上传ID（MinIO 多段上传 ID）
     */
    @Schema(description = "上传ID")
    private String uploadId;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String filename;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    /**
     * 单分片大小（字节）
     */
    @Schema(description = "单分片大小（字节）")
    private Long chunkSize;

    /**
     * 分片总数
     */
    @Schema(description = "分片总数")
    private Integer chunkCount;

    /**
     * 已上传分片序号列表（从 1 开始）
     */
    @Schema(description = "已上传分片序号列表")
    private List<Integer> uploadedChunks;

    /**
     * 已上传分片数
     */
    @Schema(description = "已上传分片数")
    private Integer completedChunks;

    /**
     * 上传状态（UPLOADING/COMPLETED/FAILED/CANCELLED）
     */
    @Schema(description = "上传状态")
    private String status;
}
