package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.common.entity.WorkflowReviewEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审批意见 Mapper
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 {@link WorkflowReviewEntity} 的 CRUD 能力，
 * 并扩展按实例ID查询审批记录的自定义方法。对应 XML：
 * {@code resources/mapper/WorkflowReviewMapper.xml}</p>
 *
 * @author 红方团队
 */
@Mapper
public interface WorkflowReviewMapper extends BaseMapper<WorkflowReviewEntity> {

    /**
     * 按实例ID查询审批记录（按创建时间正序，便于按审批先后顺序展示）
     *
     * @param instanceId 实例ID
     * @return 审批记录列表
     */
    List<WorkflowReviewEntity> selectByInstanceId(@Param("instanceId") Long instanceId);
}
