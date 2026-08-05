package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.common.entity.FileTagEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件标签关联 Mapper 接口
 *
 * <p>基于 MyBatis-Plus BaseMapper 提供标准 CRUD，并扩展按文件/标签查询、批量插入、
 * 删除文件标签、按编码统计等自定义方法。
 * 对应 XML：resources/mapper/FileTagMapper.xml</p>
 *
 * @author 红方团队
 */
@Mapper
public interface FileTagMapper extends BaseMapper<FileTagEntity> {

    /**
     * 按文件ID查询标签
     *
     * @param fileId 文件ID
     * @return 文件标签关联列表
     */
    List<FileTagEntity> selectByFileId(@Param("fileId") Long fileId);

    /**
     * 按标签ID查询文件
     *
     * @param tagId 标签ID
     * @return 文件标签关联列表
     */
    List<FileTagEntity> selectByTagId(@Param("tagId") Long tagId);

    /**
     * 批量插入文件标签
     *
     * @param list 文件标签关联列表
     * @return 插入条数
     */
    int batchInsert(@Param("list") List<FileTagEntity> list);

    /**
     * 删除文件的某个标签
     *
     * @param fileId 文件ID
     * @param tagId  标签ID
     * @return 删除条数
     */
    int deleteFileTag(@Param("fileId") Long fileId, @Param("tagId") Long tagId);

    /**
     * 按标签编码统计
     *
     * @param tagCode 标签编码
     * @return 关联文件数
     */
    Long countByTagCode(@Param("tagCode") String tagCode);
}
