package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.common.entity.WorkflowDefinitionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 工作流定义 Mapper
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 {@link WorkflowDefinitionEntity} 的 CRUD 能力，
 * 并扩展按业务类型/启用状态查询的自定义方法。对应 XML：
 * {@code resources/mapper/WorkflowDefinitionMapper.xml}</p>
 *
 * @author 红方团队
 */
@Mapper
public interface WorkflowDefinitionMapper extends BaseMapper<WorkflowDefinitionEntity> {

    /**
     * 按业务类型查询工作流定义列表（按版本号倒序）
     *
     * @param businessType 业务类型
     * @return 工作流定义列表
     */
    List<WorkflowDefinitionEntity> selectByBusinessType(@Param("businessType") String businessType);

    /**
     * 按启用状态查询工作流定义列表
     *
     * @param enabled 启用状态：0/1
     * @return 工作流定义列表
     */
    List<WorkflowDefinitionEntity> selectEnabled(@Param("enabled") Integer enabled);
}
