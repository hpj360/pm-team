package com.redteam.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.ai.entity.KnowledgeEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 知识库 Mapper 接口
 *
 * @author 红方团队
 */
@Mapper
public interface KnowledgeMapper extends BaseMapper<KnowledgeEntity> {

    /**
     * 查询全部知识库文档（按创建时间倒序）
     *
     * @return 知识库文档列表
     */
    List<KnowledgeEntity> selectAllOrderByCreatedAtDesc();
}
