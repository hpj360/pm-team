package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.common.entity.AttackChainEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * AI 攻击链推理结果 Mapper 接口
 *
 * @author 红方团队
 */
@Mapper
public interface AttackChainMapper extends BaseMapper<AttackChainEntity> {

    /**
     * 根据文件ID查询最新的攻击链推理结果
     *
     * @param fileId 文件ID
     * @return 推理结果实体，无结果返回 null
     */
    AttackChainEntity selectByFileId(@Param("fileId") Long fileId);
}
