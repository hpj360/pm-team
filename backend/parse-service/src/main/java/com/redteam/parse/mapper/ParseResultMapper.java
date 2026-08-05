package com.redteam.parse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.parse.entity.ParseResultEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件解析结果 Mapper
 *
 * @author 红方团队
 */
@Mapper
public interface ParseResultMapper extends BaseMapper<ParseResultEntity> {

}
