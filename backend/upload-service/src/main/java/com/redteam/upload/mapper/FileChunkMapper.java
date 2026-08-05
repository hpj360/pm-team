package com.redteam.upload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.upload.entity.FileChunkEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件分片记录 Mapper
 *
 * @author 红方团队
 */
@Mapper
public interface FileChunkMapper extends BaseMapper<FileChunkEntity> {

}
