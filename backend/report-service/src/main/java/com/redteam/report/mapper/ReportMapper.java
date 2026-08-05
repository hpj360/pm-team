package com.redteam.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.report.entity.ReportEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报告 Mapper 接口
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供报告实体的 CRUD 能力，
 * 复杂查询可在对应的 {@code resources/mapper/ReportMapper.xml} 中扩展。</p>
 *
 * @author 红方团队
 */
@Mapper
public interface ReportMapper extends BaseMapper<ReportEntity> {

}
