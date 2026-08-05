package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.common.entity.WorkflowInstanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 审批实例 Mapper
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 {@link WorkflowInstanceEntity} 的 CRUD 能力，
 * 并扩展按业务ID/提交人/状态查询的自定义方法。对应 XML：
 * {@code resources/mapper/WorkflowInstanceMapper.xml}</p>
 *
 * @author 红方团队
 */
@Mapper
public interface WorkflowInstanceMapper extends BaseMapper<WorkflowInstanceEntity> {

    /**
     * 按业务ID+业务类型查询实例列表（按创建时间倒序）
     *
     * @param businessId   业务ID
     * @param businessType 业务类型
     * @return 实例列表
     */
    List<WorkflowInstanceEntity> selectByBusinessId(@Param("businessId") String businessId,
                                                     @Param("businessType") String businessType);

    /**
     * 按提交人ID查询实例列表（按创建时间倒序）
     *
     * @param submitterId 提交人ID
     * @return 实例列表
     */
    List<WorkflowInstanceEntity> selectBySubmitter(@Param("submitterId") Long submitterId);

    /**
     * 按状态查询实例列表（按创建时间倒序）
     *
     * @param status 状态
     * @return 实例列表
     */
    List<WorkflowInstanceEntity> selectByStatus(@Param("status") String status);
}
