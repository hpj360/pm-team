package com.redteam.upload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.upload.entity.FileEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件Mapper接口
 *
 * @author 红方团队
 */
@Mapper
public interface FileMapper extends BaseMapper<FileEntity> {

}
