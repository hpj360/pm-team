package com.redteam.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.search.entity.SearchIndexTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 检索索引任务 Mapper
 *
 * @author 红方团队
 */
@Mapper
public interface SearchIndexTaskMapper extends BaseMapper<SearchIndexTaskEntity> {

}
