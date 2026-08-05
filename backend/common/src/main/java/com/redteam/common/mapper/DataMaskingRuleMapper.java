package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.common.entity.DataMaskingRuleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据脱敏规则 Mapper
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 {@link DataMaskingRuleEntity} 的 CRUD 能力，
 * 并扩展按密级查询的自定义方法。对应 XML：resources/mapper/DataMaskingRuleMapper.xml</p>
 *
 * @author 红方团队
 */
@Mapper
public interface DataMaskingRuleMapper extends BaseMapper<DataMaskingRuleEntity> {

    /**
     * 按密级查询脱敏规则（含禁用规则）
     *
     * @param level 密级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET
     * @return 规则列表
     */
    List<DataMaskingRuleEntity> selectByClassificationLevel(@Param("level") String level);

    /**
     * 按密级查询启用的脱敏规则
     *
     * @param level 密级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET
     * @return 启用规则列表
     */
    List<DataMaskingRuleEntity> selectEnabledByClassificationLevel(@Param("level") String level);
}
