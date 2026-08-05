package com.redteam.search.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检索历史记录实体
 *
 * <p>对应表 {@code t_search_history}，记录每次检索行为，用于用户行为分析。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_search_history")
public class SearchHistoryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 检索类型：KEYWORD / VECTOR / HYBRID
     */
    private String searchType;

    /**
     * 查询文本
     */
    private String queryText;

    /**
     * 过滤条件（JSON 字符串）
     */
    private String filters;

    /**
     * 命中数量
     */
    private Integer resultCount;

    /**
     * 响应耗时（毫秒）
     */
    private Long responseTimeMs;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
