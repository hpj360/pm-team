package com.redteam.common.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 文件上传请求DTO
 *
 * @author 红方团队
 */
@Data
@Schema(description = "文件上传请求")
public class FileUploadDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 文件名
     */
    @Schema(description = "文件名")
    @NotBlank(message = "文件名不能为空")
    private String filename;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小（字节）")
    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    /**
     * 文件类型（扩展名）
     */
    @Schema(description = "文件类型（扩展名）")
    private String fileType;

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
     * 来源类型（1-上传，2-爬取，3-导入）
     */
    @Schema(description = "来源类型（1-上传，2-爬取，3-导入）")
    private Integer sourceType;

    /**
     * 来源URL
     */
    @Schema(description = "来源URL")
    private String sourceUrl;

    /**
     * 目标ID（关联的目标）
     */
    @Schema(description = "目标ID")
    private Long targetId;

    /**
     * 标签列表（逗号分隔）
     */
    @Schema(description = "标签列表（逗号分隔）")
    private String tags;

    /**
     * 文件描述
     */
    @Schema(description = "文件描述")
    private String description;

    /**
     * 敏感等级（1-低，2-中，3-高）
     */
    @Schema(description = "敏感等级（1-低，2-中，3-高）")
    private Integer sensitiveLevel;

    /**
     * 是否公开（0-否，1-是）
     */
    @Schema(description = "是否公开（0-否，1-是）")
    private Integer isPublic;
}
