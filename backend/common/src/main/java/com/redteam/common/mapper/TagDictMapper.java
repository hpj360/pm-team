package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.common.entity.TagDictEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 标签字典 Mapper 接口
 *
 * <p>基于 MyBatis-Plus BaseMapper 提供标准 CRUD，并扩展树形查询、按层级/编码/启用状态查询等自定义方法。
 * 对应 XML：resources/mapper/TagDictMapper.xml</p>
 *
 * @author 红方团队
 */
@Mapper
public interface TagDictMapper extends BaseMapper<TagDictEntity> {

    /**
     * 树形查询：按层级（L1-L6）排序返回全部标签
     *
     * @return 按层级排序的标签列表
     */
    List<TagDictEntity> selectAllOrderByLayer();

    /**
     * 按层级查询
     *
     * @param layer 层级：L1-L6
     * @return 该层级下的标签列表
     */
    List<TagDictEntity> selectByLayer(@Param("layer") String layer);

    /**
     * 按编码查询
     *
     * @param tagCode 标签编码
     * @return 标签实体
     */
    TagDictEntity selectByCode(@Param("tagCode") String tagCode);

    /**
     * 按启用状态查询
     *
     * @param enabled 启用状态：0禁用 1启用
     * @return 标签列表
     */
    List<TagDictEntity> selectByEnabled(@Param("enabled") Integer enabled);
}
