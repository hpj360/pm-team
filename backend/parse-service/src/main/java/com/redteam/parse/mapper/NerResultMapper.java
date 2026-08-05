package com.redteam.parse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.parse.entity.NerResultEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * NER 实体识别结果 Mapper
 *
 * @author 红方团队
 */
@Mapper
public interface NerResultMapper extends BaseMapper<NerResultEntity> {

}
