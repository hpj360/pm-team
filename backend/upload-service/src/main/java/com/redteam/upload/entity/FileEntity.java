package com.redteam.upload.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件实体类
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_file")
public class FileEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 文件名
     */
    private String filename;

    /**
     * 原始文件名
     */
    private String originalFilename;

    /**
     * 存储路径
     */
    private String storagePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型（扩展名）
     */
    private String fileType;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * 文件MD5值
     */
    private String fileMd5;

    /**
     * 文件SHA256值
     */
    private String fileSha256;

    /**
     * 来源类型（1-上传，2-爬取，3-导入）
     */
    private Integer sourceType;

    /**
     * 来源URL
     */
    private String sourceUrl;

    /**
     * 目标ID
     */
    private Long targetId;

    /**
     * 标签列表
     */
    private String tags;

    /**
     * 文件描述
     */
    private String description;

    /**
     * 敏感等级（1-低，2-中，3-高）
     */
    private Integer sensitiveLevel;

    /**
     * 是否公开（0-否，1-是）
     */
    private Integer isPublic;

    /**
     * 解析状态（0-未解析，1-解析中，2-已解析，3-解析失败）
     */
    private Integer parseStatus;

    /**
     * 索引状态（0-未索引，1-索引中，2-已索引，3-索引失败）
     */
    private Integer indexStatus;

    /**
     * 下载次数
     */
    private Integer downloadCount;

    /**
     * 预览次数
     */
    private Integer previewCount;
}
