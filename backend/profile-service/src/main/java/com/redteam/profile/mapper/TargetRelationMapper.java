package com.redteam.profile.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.profile.entity.TargetRelationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 目标关系 Mapper 接口
 *
 * <p>基于 MyBatis Plus 的 BaseMapper，提供目标关系实体的基础 CRUD 能力。</p>
 *
 * @author 红方团队
 */
@Mapper
public interface TargetRelationMapper extends BaseMapper<TargetRelationEntity> {

}
