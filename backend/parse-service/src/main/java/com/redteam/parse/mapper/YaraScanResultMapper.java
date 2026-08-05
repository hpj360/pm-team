package com.redteam.parse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.parse.entity.YaraScanResultEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * YARA 扫描结果 Mapper
 *
 * @author 红方团队
 */
@Mapper
public interface YaraScanResultMapper extends BaseMapper<YaraScanResultEntity> {

}
