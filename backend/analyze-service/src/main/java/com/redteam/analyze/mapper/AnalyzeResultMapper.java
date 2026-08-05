package com.redteam.analyze.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.analyze.entity.AnalyzeResultEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分析结果 Mapper 接口
 *
 * @author 红方团队
 */
@Mapper
public interface AnalyzeResultMapper extends BaseMapper<AnalyzeResultEntity> {

}
