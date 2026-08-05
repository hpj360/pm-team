package com.redteam.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.search.entity.SearchHotWordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 热门检索词 Mapper
 *
 * @author 红方团队
 */
@Mapper
public interface SearchHotWordMapper extends BaseMapper<SearchHotWordEntity> {

    /**
     * 原子累加检索词次数（存在则 +1 并更新时间，不存在则插入）
     *
     * @param word 检索词
     * @return 影响行数
     */
    @Update("INSERT INTO t_search_hot_words (word, search_count, last_searched_at) " +
            "VALUES (#{word}, 1, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (word) DO UPDATE SET " +
            "search_count = t_search_hot_words.search_count + 1, " +
            "last_searched_at = CURRENT_TIMESTAMP")
    int incrementCount(@Param("word") String word);
}
