package com.redteam.common.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件信息响应DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "文件信息响应")
public class FileInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件ID
     */
    @Schema(description = "文件ID")
    private Long id;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    private String filename;

    /**
     * 原始文件名
     */
    @Schema(description = "原始文件名")
    private String originalFilename;

    /**
     * 存储路径
     */
    @Schema(description = "存储路径")
    private String storagePath;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    /**
     * 文件类型（扩展名）
     */
    @Schema(description = "文件类型（扩展名）")
    private String fileType;

    /**
     * MIME类型
     */
    @Schema(description = "MIME类型")
    private String mimeType;

    /**
     * 文件MD5值
     */
    @Schema(description = "文件MD5值")
    private String fileMd5;

    /**
     * 文件SHA256值
     */
    @Schema(description = "文件SHA256值")
    private String fileSha256;

    /**
     * 来源类型
     */
    @Schema(description = "来源类型（1-上传，2-爬取，3-导入）")
    private Integer sourceType;

    /**
     * 来源URL
     */
    @Schema(description = "来源URL")
    private String sourceUrl;

    /**
     * 目标ID
     */
    @Schema(description = "目标ID")
    private Long targetId;

    /**
     * 目标名称
     */
    @Schema(description = "目标名称")
    private String targetName;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表")
    private String tags;

    /**
     * 文件描述
     */
    @Schema(description = "文件描述")
    private String description;

    /**
     * 敏感等级
     */
    @Schema(description = "敏感等级（1-低，2-中，3-高）")
    private Integer sensitiveLevel;

    /**
     * 是否公开
     */
    @Schema(description = "是否公开（0-否，1-是）")
    private Integer isPublic;

    /**
     * 解析状态（0-未解析，1-解析中，2-已解析，3-解析失败）
     */
    @Schema(description = "解析状态（0-未解析，1-解析中，2-已解析，3-解析失败）")
    private Integer parseStatus;

    /**
     * 索引状态（0-未索引，1-索引中，2-已索引，3-索引失败）
     */
    @Schema(description = "索引状态（0-未索引，1-索引中，2-已索引，3-索引失败）")
    private Integer indexStatus;

    /**
     * 下载次数
     */
    @Schema(description = "下载次数")
    private Integer downloadCount;

    /**
     * 预览次数
     */
    @Schema(description = "预览次数")
    private Integer previewCount;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    @Schema(description = "创建人")
    private String createByName;
}
