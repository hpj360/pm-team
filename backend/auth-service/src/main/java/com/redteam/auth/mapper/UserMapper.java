package com.redteam.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper接口
 *
 * @author 红方团队
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

}
