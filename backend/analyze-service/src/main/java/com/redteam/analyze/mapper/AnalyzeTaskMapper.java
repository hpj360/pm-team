package com.redteam.analyze.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.analyze.entity.AnalyzeTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分析任务 Mapper 接口
 *
 * @author 红方团队
 */
@Mapper
public interface AnalyzeTaskMapper extends BaseMapper<AnalyzeTaskEntity> {

}
