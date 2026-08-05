package com.redteam.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.search.entity.SearchHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 检索历史 Mapper
 *
 * @author 红方团队
 */
@Mapper
public interface SearchHistoryMapper extends BaseMapper<SearchHistoryEntity> {

}
