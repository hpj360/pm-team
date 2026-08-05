package com.redteam.search.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检索索引任务实体
 *
 * <p>对应表 {@code t_search_index_task}，用于追踪每个文件的 ES + Milvus 索引状态。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_search_index_task")
public class SearchIndexTaskEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件 ID（唯一）
     */
    private Long fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件 SM3 指纹
     */
    private String fileSm3;

    /**
     * 是否已索引到 ES
     */
    private Boolean esIndexed;

    /**
     * 是否已索引到 Milvus
     */
    private Boolean milvusIndexed;

    /**
     * 索引状态：PENDING / INDEXING / SUCCESS / FAILED
     */
    private String indexStatus;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 状态常量：待索引
     */
    public static final String STATUS_PENDING = "PENDING";
    /**
     * 状态常量：索引中
     */
    public static final String STATUS_INDEXING = "INDEXING";
    /**
     * 状态常量：索引成功
     */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /**
     * 状态常量：索引失败
     */
    public static final String STATUS_FAILED = "FAILED";
}
