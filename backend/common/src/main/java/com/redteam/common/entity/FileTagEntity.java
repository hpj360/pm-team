package com.redteam.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件标签关联实体类
 *
 * <p>对应数据库表 file_tags，记录文件与标签字典的多对多关联关系。
 * tag_code 字段冗余存储，便于按编码直接查询，避免连表。</p>
 *
 * @author 红方团队
 */
@Data
@TableName("file_tags")
public class FileTagEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 标签ID
     */
    private Long tagId;

    /**
     * 标签编码（冗余，便于查询）
     */
    private String tagCode;

    /**
     * 标签来源：AUTO自动 / MANUAL手动
     */
    private String source;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
