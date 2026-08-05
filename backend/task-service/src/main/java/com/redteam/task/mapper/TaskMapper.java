package com.redteam.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.task.entity.TaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 任务 Mapper 接口
 * <p>
 * 基于 MyBatis Plus 的 BaseMapper，提供任务实体的基础 CRUD 能力。
 * </p>
 *
 * @author 红方团队
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {

}
