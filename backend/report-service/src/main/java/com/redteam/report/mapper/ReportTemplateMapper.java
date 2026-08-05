package com.redteam.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.report.entity.ReportTemplateEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 报告模板 Mapper 接口
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供报告模板的 CRUD 能力。</p>
 *
 * @author 红方团队
 */
@Mapper
public interface ReportTemplateMapper extends BaseMapper<ReportTemplateEntity> {

}
