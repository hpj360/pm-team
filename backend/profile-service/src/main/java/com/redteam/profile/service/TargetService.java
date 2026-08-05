package com.redteam.profile.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.redteam.common.result.PageResult;
import com.redteam.profile.dto.TargetDTO;
import com.redteam.profile.dto.TargetProfileDTO;
import com.redteam.profile.dto.TargetQueryDTO;
import com.redteam.profile.dto.TargetRelationDTO;
import com.redteam.profile.dto.TargetRelationRequestDTO;
import com.redteam.profile.entity.TargetEntity;

/**
 * 目标画像服务接口
 *
 * <p>提供目标的 CRUD、画像聚合、关系图谱管理等能力。</p>
 *
 * @author 红方团队
 */
public interface TargetService extends IService<TargetEntity> {

    /**
     * 创建目标
     *
     * @param dto 目标创建 DTO
     * @return 创建后的目标实体
     */
    TargetEntity createTarget(TargetDTO dto);

    /**
     * 更新目标信息
     *
     * @param id  目标ID
     * @param dto 目标更新 DTO
     * @return 更新后的目标实体
     */
    TargetEntity updateTarget(Long id, TargetDTO dto);

    /**
     * 删除目标（逻辑删除）
     *
     * @param id 目标ID
     * @return 是否删除成功
     */
    boolean deleteTarget(Long id);

    /**
     * 根据目标ID获取目标详情
     *
     * @param id 目标ID
     * @return 目标实体
     */
    TargetEntity getTarget(Long id);

    /**
     * 分页查询目标列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<TargetEntity> listTargets(TargetQueryDTO query);

    /**
     * 获取目标完整画像（基本信息+关联文件+关联IOC+关联事件+关联目标）
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
     * 获取目标关系图谱数据（节点+边，用于前端 ECharts 关系图）
     *
     * @param rootId     根目标ID
     * @param depth      关系展开深度（默认1层）
     * @return 关系图谱数据
     */
    TargetRelationDTO getRelationGraph(Long rootId, Integer depth);

    /**
     * 添加目标关系
     *
     * @param dto 关系创建请求
     * @return 创建后的关系实体
     */
    boolean addRelation(TargetRelationRequestDTO dto);

    /**
     * 删除目标关系
     *
     * @param relationId 关系ID
     * @return 是否删除成功
     */
    boolean removeRelation(Long relationId);

    /**
     * 关注/取消关注目标
     *
     * @param id         目标ID
     * @param isFollowed 是否关注
     * @return 是否成功
     */
    boolean followTarget(Long id, Boolean isFollowed);

    /**
     * 获取目标的关联文件ID列表
     *
     * @param id 目标ID
     * @return 文件ID列表
     */
    java.util.List<Long> getTargetFiles(Long id);

    /**
     * 搜索目标
     *
     * @param keyword 关键词
     * @param type    类型
     * @return 目标列表
     */
    java.util.List<TargetEntity> searchTargets(String keyword, Integer type);
}
