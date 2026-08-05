package com.redteam.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.notification.entity.NotificationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知 Mapper 接口
 *
 * @author 红方团队
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {

}
