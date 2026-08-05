package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.common.entity.ThreatSummaryEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 威胁摘要 Mapper
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 {@link ThreatSummaryEntity} 的 CRUD 能力，
 * 并扩展按文件ID查询威胁摘要的自定义方法。</p>
 *
 * @author 红方团队
 */
@Mapper
public interface ThreatSummaryMapper extends BaseMapper<ThreatSummaryEntity> {

    /**
     * 按文件ID查询威胁摘要（返回最新一条）
     *
     * @param fileId 文件ID
     * @return 威胁摘要实体，无记录时返回 null
     */
    ThreatSummaryEntity selectByFileId(@Param("fileId") Long fileId);
}
