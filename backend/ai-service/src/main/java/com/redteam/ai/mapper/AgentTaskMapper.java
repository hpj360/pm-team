package com.redteam.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.ai.entity.AgentTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 任务 Mapper 接口
 *
 * @author 红方团队
 */
@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTaskEntity> {

    /**
     * 查询用户最近的 Agent 任务列表（按创建时间倒序）
     *
     * @param userId 用户ID
     * @param limit  返回条数上限
     * @return 任务列表
     */
    List<AgentTaskEntity> selectByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}
