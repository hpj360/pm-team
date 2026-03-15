package com.redteam.profile.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.redteam.profile.dto.TargetProfileDTO;
import com.redteam.profile.entity.TargetEntity;

import java.util.List;

/**
 * 目标画像服务接口
 *
 * @author 红方团队
 */
public interface TargetService extends IService<TargetEntity> {

    /**
     * 创建目标
     *
     * @param name        目标名称
     * @param type        目标类型
     * @param description 描述
     * @param tags        标签
     * @return 目标信息
     */
    TargetEntity createTarget(String name, Integer type, String description, String tags);

    /**
     * 获取目标详情
     *
     * @param id 目标ID
     * @return 目标信息
     */
    TargetEntity getTargetById(Long id);

    /**
     * 获取目标画像
     *
     * @param id 目标ID
     * @return 画像信息
     */
    TargetProfileDTO getTargetProfile(Long id);

    /**
     * 生成目标画像
     *
     * @param id 目标ID
     * @return 画像信息
     */
    TargetProfileDTO generateProfile(Long id);

    /**
     * 更新目标信息
     *
     * @param id          目标ID
     * @param name        名称
     * @param description 描述
     * @param tags        标签
     * @param riskLevel   风险等级
     * @return 目标信息
     */
    TargetEntity updateTarget(Long id, String name, String description, String tags, Integer riskLevel);

    /**
     * 删除目标
     *
     * @param id 目标ID
     * @return 是否成功
     */
    boolean deleteTarget(Long id);

    /**
     * 关注目标
     *
     * @param id        目标ID
     * @param isFollowed 是否关注
     * @return 是否成功
     */
    boolean followTarget(Long id, Boolean isFollowed);

    /**
     * 获取目标的关联文件
     *
     * @param id 目标ID
     * @return 文件ID列表
     */
    List<Long> getTargetFiles(Long id);

    /**
     * 搜索目标
     *
     * @param keyword 关键词
     * @param type    类型
     * @return 目标列表
     */
    List<TargetEntity> searchTargets(String keyword, Integer type);
}
