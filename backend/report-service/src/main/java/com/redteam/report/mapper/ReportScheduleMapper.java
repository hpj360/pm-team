package com.redteam.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.report.entity.ReportScheduleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时报告配置 Mapper
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 {@link ReportScheduleEntity} 的 CRUD 能力。</p>
 *
 * @author 红方团队
 */
@Mapper
public interface ReportScheduleMapper extends BaseMapper<ReportScheduleEntity> {

}
