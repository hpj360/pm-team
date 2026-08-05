package com.redteam.parse.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.parse.entity.YaraRuleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * YARA 规则 Mapper
 *
 * @author 红方团队
 */
@Mapper
public interface YaraRuleMapper extends BaseMapper<YaraRuleEntity> {

}
