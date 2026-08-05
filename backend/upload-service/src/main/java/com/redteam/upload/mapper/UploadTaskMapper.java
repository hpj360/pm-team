package com.redteam.upload.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.redteam.upload.entity.UploadTaskEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上传任务 Mapper
 *
 * @author 红方团队
 */
@Mapper
public interface UploadTaskMapper extends BaseMapper<UploadTaskEntity> {

}
