package com.redteam.upload.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件事件消息体
 *
 * <p>统一封装文件上传完成 / 文件删除等事件，由 upload-service 投递到 Kafka 主题
 * {@code redteam.file.events}，下游 parse-service / search-service / notification-service
 * 消费并触发后续处理。</p>
 *
 * @author 红方团队
 */
@Data
public class FileEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件ID（UUID）
     */
    private String eventId;

    /**
     * 事件类型（FILE_UPLOADED / FILE_DELETED）
     */
    private String eventType;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 文件SM3指纹
     */
    private String fileSm3;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型（扩展名）
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MinIO 存储路径
     */
    private String storagePath;

    /**
     * 关联目标ID
     */
    private Long targetId;

    /**
     * 操作用户ID
     */
    private Long userId;

    /**
     * 事件时间戳
     */
    private Long timestamp;
}
