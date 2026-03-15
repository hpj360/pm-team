package com.redteam.profile.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.profile.dto.TargetProfileDTO;
import com.redteam.profile.entity.TargetEntity;
import com.redteam.profile.mapper.TargetMapper;
import com.redteam.profile.service.TargetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 目标画像服务实现类
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetServiceImpl extends ServiceImpl<TargetMapper, TargetEntity> implements TargetService {

    private final StringRedisTemplate redisTemplate;

    private static final String TARGET_CACHE_PREFIX = "target:profile:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetEntity createTarget(String name, Integer type, String description, String tags) {
        log.info("创建目标: name={}", name);

        TargetEntity target = new TargetEntity();
        target.setName(name);
        target.setType(type);
        target.setDescription(description);
        target.setTags(tags);
        target.setFileCount(0);
        target.setProfileStatus(0); // 未生成
        target.setRiskLevel(1); // 低风险
        target.setIsFollowed(0);

        this.save(target);
        return target;
    }

    @Override
    public TargetEntity getTargetById(Long id) {
        TargetEntity target = this.getById(id);
        if (target == null) {
            throw new BusinessException(ResultCode.TARGET_NOT_FOUND);
        }
        return target;
    }

    @Override
    public TargetProfileDTO getTargetProfile(Long id) {
        // 先从缓存获取
        String cacheKey = TARGET_CACHE_PREFIX + id;
        // TODO: 从Redis获取缓存的画像数据

        TargetEntity target = getTargetById(id);

        TargetProfileDTO profile = new TargetProfileDTO();
        profile.setId(target.getId());
        profile.setName(target.getName());
        profile.setType(target.getType());
        profile.setDescription(target.getDescription());
        profile.setFileCount(target.getFileCount());
        profile.setRiskLevel(target.getRiskLevel());
        profile.setCreateTime(target.getCreateTime());
        profile.setUpdateTime(target.getUpdateTime());

        // 解析标签
        if (StrUtil.isNotBlank(target.getTags())) {
            profile.setTags(List.of(target.getTags().split(",")));
        }

        return profile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetProfileDTO generateProfile(Long id) {
        log.info("生成目标画像: id={}", id);

        TargetEntity target = getTargetById(id);

        // 更新画像状态
        target.setProfileStatus(1); // 生成中
        this.updateById(target);

        try {
            // TODO: 实现画像生成逻辑
            // 1. 收集目标相关的所有文件
            // 2. 分析文件内容
            // 3. 提取关键信息
            // 4. 生成画像

            TargetProfileDTO profile = new TargetProfileDTO();
            profile.setId(id);
            profile.setName(target.getName());
            profile.setType(target.getType());
            profile.setFileCount(target.getFileCount());

            // 更新画像状态
            target.setProfileStatus(2); // 已生成
            this.updateById(target);

            // 缓存画像
            String cacheKey = TARGET_CACHE_PREFIX + id;
            // TODO: 缓存画像数据到Redis

            return profile;

        } catch (Exception e) {
            log.error("生成画像失败", e);
            target.setProfileStatus(3); // 生成失败
            this.updateById(target);
            throw new BusinessException(ResultCode.PROFILE_GENERATE_ERROR, "画像生成失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetEntity updateTarget(Long id, String name, String description, String tags, Integer riskLevel) {
        log.info("更新目标: id={}", id);

        TargetEntity target = getTargetById(id);

        if (StrUtil.isNotBlank(name)) {
            target.setName(name);
        }
        if (StrUtil.isNotBlank(description)) {
            target.setDescription(description);
        }
        if (StrUtil.isNotBlank(tags)) {
            target.setTags(tags);
        }
        if (riskLevel != null) {
            target.setRiskLevel(riskLevel);
        }

        this.updateById(target);
        return target;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTarget(Long id) {
        log.info("删除目标: id={}", id);

        // 删除缓存
        String cacheKey = TARGET_CACHE_PREFIX + id;
        redisTemplate.delete(cacheKey);

        return this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean followTarget(Long id, Boolean isFollowed) {
        log.info("关注目标: id={}, isFollowed={}", id, isFollowed);

        TargetEntity target = getTargetById(id);
        target.setIsFollowed(isFollowed ? 1 : 0);
        return this.updateById(target);
    }

    @Override
    public List<Long> getTargetFiles(Long id) {
        // TODO: 从数据库查询关联的文件ID
        log.info("获取目标文件: id={}", id);
        return new ArrayList<>();
    }

    @Override
    public List<TargetEntity> searchTargets(String keyword, Integer type) {
        log.info("搜索目标: keyword={}, type={}", keyword, type);

        LambdaQueryWrapper<TargetEntity> queryWrapper = new LambdaQueryWrapper<>();

        if (StrUtil.isNotBlank(keyword)) {
            queryWrapper.like(TargetEntity::getName, keyword)
                    .or()
                    .like(TargetEntity::getDescription, keyword)
                    .or()
                    .like(TargetEntity::getTags, keyword);
        }

        if (type != null) {
            queryWrapper.eq(TargetEntity::getType, type);
        }

        return this.list(queryWrapper);
    }
}
