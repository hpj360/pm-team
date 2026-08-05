package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.common.entity.SearchTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 搜索模板 Mapper
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 {@link SearchTemplateEntity} 的 CRUD 能力，
 * 并扩展按用户ID查询模板列表的自定义方法。</p>
 *
 * @author 红方团队
 */
@Mapper
public interface SearchTemplateMapper extends BaseMapper<SearchTemplateEntity> {

    /**
     * 按用户ID查询搜索模板列表（按创建时间倒序）
     *
     * @param userId 用户ID
     * @return 模板列表
     */
    List<SearchTemplateEntity> selectByUserId(@Param("userId") Long userId);
}
