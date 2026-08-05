package com.redteam.search.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 热门检索词实体
 *
 * <p>对应表 {@code t_search_hot_words}，统计检索词出现次数，用于热门检索词推荐。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("t_search_hot_words")
public class SearchHotWordEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 检索词
     */
    private String word;

    /**
     * 检索次数
     */
    private Integer searchCount;

    /**
     * 最后检索时间
     */
    private LocalDateTime lastSearchedAt;
}
