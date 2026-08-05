package com.redteam.profile.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.ResultCode;
import com.redteam.profile.dto.TargetDTO;
import com.redteam.profile.dto.TargetProfileDTO;
import com.redteam.profile.dto.TargetQueryDTO;
import com.redteam.profile.dto.TargetRelationDTO;
import com.redteam.profile.dto.TargetRelationRequestDTO;
import com.redteam.profile.entity.TargetEntity;
import com.redteam.profile.entity.TargetRelationEntity;
import com.redteam.profile.mapper.TargetMapper;
import com.redteam.profile.mapper.TargetRelationMapper;
import com.redteam.profile.service.TargetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 目标画像服务实现类
 *
 * <p>基于 MyBatis Plus 实现目标 CRUD、画像聚合、关系图谱管理。
 * 画像数据可缓存到 Redis，缓存时长 30 分钟。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetServiceImpl extends ServiceImpl<TargetMapper, TargetEntity> implements TargetService {

    /**
     * 画像缓存 Redis Key 前缀
     */
    private static final String TARGET_CACHE_PREFIX = "target:profile:";

    /**
     * 画像缓存有效期：30 分钟
     */
    private static final long CACHE_TTL_MINUTES = 30L;

    /**
     * 默认关系展开深度
     */
    private static final int DEFAULT_GRAPH_DEPTH = 1;

    /**
     * 最大关系展开深度，避免图谱过大
     */
    private static final int MAX_GRAPH_DEPTH = 3;

    /**
     * 默认关系权重
     */
    private static final double DEFAULT_RELATION_WEIGHT = 0.5;

    private final StringRedisTemplate redisTemplate;
    private final TargetRelationMapper targetRelationMapper;

    /**
     * 创建目标
     *
     * @param dto 目标创建 DTO
     * @return 创建后的目标实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetEntity createTarget(TargetDTO dto) {
        log.info("创建目标: name={}, type={}", dto.getName(), dto.getType());

        TargetEntity target = new TargetEntity();
        target.setName(dto.getName());
        target.setType(dto.getType());
        target.setIndustry(dto.getIndustry());
        target.setDescription(dto.getDescription());
        target.setAttackSurface(dto.getAttackSurface());
        target.setTechAssets(dto.getTechAssets());
        target.setOrgStructure(dto.getOrgStructure());
        target.setTags(dto.getTags());
        target.setFileCount(0);
        target.setProfileStatus(0);
        target.setRiskLevel(Optional.ofNullable(dto.getRiskLevel()).orElse(1));
        target.setIsFollowed(0);

        this.save(target);
        log.info("目标创建成功: id={}, name={}", target.getId(), target.getName());
        return target;
    }

    /**
     * 更新目标信息
     *
     * @param id  目标ID
     * @param dto 目标更新 DTO
     * @return 更新后的目标实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetEntity updateTarget(Long id, TargetDTO dto) {
        log.info("更新目标: id={}", id);

        TargetEntity target = getTarget(id);

        if (StrUtil.isNotBlank(dto.getName())) {
            target.setName(dto.getName());
        }
        if (dto.getType() != null) {
            target.setType(dto.getType());
        }
        if (StrUtil.isNotBlank(dto.getIndustry())) {
            target.setIndustry(dto.getIndustry());
        }
        if (dto.getDescription() != null) {
            target.setDescription(dto.getDescription());
        }
        if (dto.getAttackSurface() != null) {
            target.setAttackSurface(dto.getAttackSurface());
        }
        if (dto.getTechAssets() != null) {
            target.setTechAssets(dto.getTechAssets());
        }
        if (dto.getOrgStructure() != null) {
            target.setOrgStructure(dto.getOrgStructure());
        }
        if (StrUtil.isNotBlank(dto.getTags())) {
            target.setTags(dto.getTags());
        }
        if (dto.getRiskLevel() != null) {
            target.setRiskLevel(dto.getRiskLevel());
        }

        this.updateById(target);
        // 失效画像缓存
        invalidateProfileCache(id);
        log.info("目标更新成功: id={}", id);
        return target;
    }

    /**
     * 删除目标（逻辑删除）
     *
     * @param id 目标ID
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteTarget(Long id) {
        log.info("删除目标: id={}", id);
        TargetEntity target = getTarget(id);
        invalidateProfileCache(id);
        boolean ok = baseMapper.deleteById(id) > 0;
        // 删除该目标相关的所有关系
        LambdaQueryWrapper<TargetRelationEntity> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(TargetRelationEntity::getSourceId, id)
                .or().eq(TargetRelationEntity::getTargetId, id);
        targetRelationMapper.delete(relWrapper);
        log.info("目标删除{}: id={}", ok ? "成功" : "失败", id);
        return ok;
    }

    /**
     * 根据目标ID获取目标详情
     *
     * @param id 目标ID
     * @return 目标实体
     */
    @Override
    public TargetEntity getTarget(Long id) {
        TargetEntity target = this.getById(id);
        if (target == null) {
            throw new BusinessException(ResultCode.TARGET_NOT_FOUND);
        }
        return target;
    }

    /**
     * 分页查询目标列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<TargetEntity> listTargets(TargetQueryDTO query) {
        log.info("分页查询目标: pageNum={}, pageSize={}", query.getPageNum(), query.getPageSize());

        long pageNum = query.getPageNum() == null ? 1L : Math.max(1L, query.getPageNum());
        long pageSize = query.getPageSize() == null ? 10L : Math.min(100L, Math.max(1L, query.getPageSize()));

        Page<TargetEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<TargetEntity> wrapper = new LambdaQueryWrapper<>();

        if (query.getType() != null) {
            wrapper.eq(TargetEntity::getType, query.getType());
        }
        if (StrUtil.isNotBlank(query.getIndustry())) {
            wrapper.eq(TargetEntity::getIndustry, query.getIndustry());
        }
        if (query.getRiskLevel() != null) {
            wrapper.eq(TargetEntity::getRiskLevel, query.getRiskLevel());
        }
        if (query.getIsFollowed() != null) {
            wrapper.eq(TargetEntity::getIsFollowed, query.getIsFollowed());
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(TargetEntity::getName, query.getKeyword())
                    .or().like(TargetEntity::getDescription, query.getKeyword())
                    .or().like(TargetEntity::getTags, query.getKeyword()));
        }
        wrapper.orderByDesc(TargetEntity::getCreateTime);

        Page<TargetEntity> result = this.page(page, wrapper);
        return PageResult.of(pageNum, pageSize, result.getTotal(), result.getRecords());
    }

    /**
     * 获取目标完整画像
     *
     * @param id 目标ID
     * @return 画像信息
     */
    @Override
    public TargetProfileDTO getTargetProfile(Long id) {
        log.info("获取目标画像: id={}", id);
        TargetEntity target = getTarget(id);

        TargetProfileDTO profile = new TargetProfileDTO();
        profile.setId(target.getId());
        profile.setName(target.getName());
        profile.setType(target.getType());
        profile.setIndustry(target.getIndustry());
        profile.setDescription(target.getDescription());
        profile.setFileCount(target.getFileCount());
        profile.setRiskLevel(target.getRiskLevel());
        profile.setProfileStatus(target.getProfileStatus());
        profile.setIsFollowed(target.getIsFollowed());
        profile.setCreateTime(target.getCreateTime());
        profile.setUpdateTime(target.getUpdateTime());

        // 解析标签
        if (StrUtil.isNotBlank(target.getTags())) {
            profile.setTags(List.of(target.getTags().split(",")));
        }

        // 基本信息
        Map<String, Object> basicInfo = new LinkedHashMap<>();
        basicInfo.put("type", target.getType());
        basicInfo.put("industry", target.getIndustry());
        basicInfo.put("riskLevel", target.getRiskLevel());
        basicInfo.put("isFollowed", target.getIsFollowed());
        profile.setBasicInfo(basicInfo);

        // 组织架构
        profile.setOrgStructure(parseJsonArray(target.getOrgStructure()));

        // 技术资产
        profile.setTechAssets(parseTechAssets(target.getTechAssets()));

        // 攻击面
        profile.setAttackSurface(parseAttackSurface(target.getAttackSurface()));

        // 关联目标（来自关系表）
        profile.setRelatedTargets(loadRelatedTargets(id));

        // 关联文件 IOC 占位（实际场景需调用文件服务）
        profile.setRelatedFileIds(getTargetFiles(id));
        profile.setRelatedIocs(new ArrayList<>());

        // 历史事件占位
        profile.setHistoryEvents(new ArrayList<>());

        return profile;
    }

    /**
     * 生成目标画像
     *
     * @param id 目标ID
     * @return 画像信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TargetProfileDTO generateProfile(Long id) {
        log.info("生成目标画像: id={}", id);

        TargetEntity target = getTarget(id);
        target.setProfileStatus(1);
        this.updateById(target);

        try {
            TargetProfileDTO profile = getTargetProfile(id);

            // 将画像数据序列化存储
            target.setProfileData(JSONUtil.toJsonStr(profile));
            target.setProfileStatus(2);
            this.updateById(target);

            // 缓存画像
            cacheProfile(id, profile);

            return profile;
        } catch (Exception e) {
            log.error("生成画像失败: id={}", id, e);
            target.setProfileStatus(3);
            this.updateById(target);
            throw new BusinessException(ResultCode.PROFILE_GENERATE_ERROR, "画像生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取目标关系图谱数据
     *
     * @param rootId 根目标ID
     * @param depth  关系展开深度
     * @return 关系图谱数据
     */
    @Override
    public TargetRelationDTO getRelationGraph(Long rootId, Integer depth) {
        int actualDepth = depth == null ? DEFAULT_GRAPH_DEPTH : Math.min(Math.max(1, depth), MAX_GRAPH_DEPTH);
        log.info("获取目标关系图谱: rootId={}, depth={}", rootId, actualDepth);

        // 校验根目标存在
        getTarget(rootId);

        Set<Long> visited = new HashSet<>();
        Set<Long> toVisit = new HashSet<>();
        toVisit.add(rootId);

        List<TargetRelationEntity> allRelations = new ArrayList<>();
        Set<Long> nodeIds = new HashSet<>();
        nodeIds.add(rootId);

        // BFS 逐层展开
        for (int d = 0; d < actualDepth && !toVisit.isEmpty(); d++) {
            Set<Long> nextLevel = new HashSet<>();
            for (Long currentId : toVisit) {
                if (!visited.add(currentId)) {
                    continue;
                }
                List<TargetRelationEntity> rels = findRelationsOf(currentId);
                allRelations.addAll(rels);
                for (TargetRelationEntity rel : rels) {
                    Long other = rel.getSourceId().equals(currentId) ? rel.getTargetId() : rel.getSourceId();
                    if (!visited.contains(other)) {
                        nextLevel.add(other);
                        nodeIds.add(other);
                    }
                }
            }
            toVisit = nextLevel;
        }

        // 加载所有节点
        List<TargetEntity> nodes = listByIds(nodeIds);
        Map<Long, TargetEntity> nodeMap = nodes.stream()
                .collect(Collectors.toMap(TargetEntity::getId, n -> n));

        // 统计每个节点的关联数
        Map<Long, Integer> degreeMap = new HashMap<>();
        for (TargetRelationEntity rel : allRelations) {
            degreeMap.merge(rel.getSourceId(), 1, Integer::sum);
            degreeMap.merge(rel.getTargetId(), 1, Integer::sum);
        }

        TargetRelationDTO graph = new TargetRelationDTO();
        graph.setNodes(nodeMap.values().stream().map(t -> toGraphNode(t, degreeMap.getOrDefault(t.getId(), 0))).toList());
        graph.setEdges(allRelations.stream().map(this::toGraphEdge).toList());

        return graph;
    }

    /**
     * 添加目标关系
     *
     * @param dto 关系创建请求
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addRelation(TargetRelationRequestDTO dto) {
        log.info("添加目标关系: sourceId={}, targetId={}, type={}",
                dto.getSourceId(), dto.getTargetId(), dto.getRelationType());

        if (dto.getSourceId().equals(dto.getTargetId())) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "源目标与目标目标不能相同");
        }

        // 校验两端目标均存在
        getTarget(dto.getSourceId());
        getTarget(dto.getTargetId());

        // 校验关系是否已存在
        LambdaQueryWrapper<TargetRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TargetRelationEntity::getSourceId, dto.getSourceId())
                .eq(TargetRelationEntity::getTargetId, dto.getTargetId())
                .eq(TargetRelationEntity::getRelationType, dto.getRelationType());
        Long exists = targetRelationMapper.selectCount(wrapper);
        if (exists != null && exists > 0) {
            throw BusinessException.of(ResultCode.RESOURCE_EXISTS, "该关系已存在");
        }

        TargetRelationEntity entity = new TargetRelationEntity();
        entity.setSourceId(dto.getSourceId());
        entity.setTargetId(dto.getTargetId());
        entity.setRelationType(dto.getRelationType());
        entity.setWeight(dto.getWeight() == null ? DEFAULT_RELATION_WEIGHT : dto.getWeight());
        entity.setDescription(dto.getDescription());

        int rows = targetRelationMapper.insert(entity);
        log.info("目标关系添加{}: sourceId={}, targetId={}", rows > 0 ? "成功" : "失败",
                dto.getSourceId(), dto.getTargetId());
        return rows > 0;
    }

    /**
     * 删除目标关系
     *
     * @param relationId 关系ID
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeRelation(Long relationId) {
        log.info("删除目标关系: relationId={}", relationId);
        TargetRelationEntity entity = targetRelationMapper.selectById(relationId);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "目标关系不存在: " + relationId);
        }
        int rows = targetRelationMapper.deleteById(relationId);
        return rows > 0;
    }

    /**
     * 关注/取消关注目标
     *
     * @param id         目标ID
     * @param isFollowed 是否关注
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean followTarget(Long id, Boolean isFollowed) {
        log.info("关注目标: id={}, isFollowed={}", id, isFollowed);
        TargetEntity target = getTarget(id);
        target.setIsFollowed(Boolean.TRUE.equals(isFollowed) ? 1 : 0);
        return this.updateById(target);
    }

    /**
     * 获取目标的关联文件ID列表
     *
     * @param id 目标ID
     * @return 文件ID列表
     */
    @Override
    public List<Long> getTargetFiles(Long id) {
        log.info("获取目标关联文件: id={}", id);
        getTarget(id);
        // 当前服务不直接持有文件表，跨服务查询应通过 RPC 或文件服务 API。
        // 此处返回空列表作为骨架实现，后续接入文件服务后补充。
        return new ArrayList<>();
    }

    /**
     * 搜索目标
     *
     * @param keyword 关键词
     * @param type    类型
     * @return 目标列表
     */
    @Override
    public List<TargetEntity> searchTargets(String keyword, Integer type) {
        log.info("搜索目标: keyword={}, type={}", keyword, type);

        LambdaQueryWrapper<TargetEntity> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            queryWrapper.like(TargetEntity::getName, keyword)
                    .or().like(TargetEntity::getDescription, keyword)
                    .or().like(TargetEntity::getTags, keyword);
        }
        if (type != null) {
            queryWrapper.eq(TargetEntity::getType, type);
        }
        return this.list(queryWrapper);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 失效画像缓存
     *
     * @param targetId 目标ID
     */
    private void invalidateProfileCache(Long targetId) {
        try {
            redisTemplate.delete(TARGET_CACHE_PREFIX + targetId);
        } catch (Exception e) {
            log.warn("失效画像缓存失败: id={}", targetId, e);
        }
    }

    /**
     * 缓存画像到 Redis
     *
     * @param targetId 目标ID
     * @param profile  画像数据
     */
    private void cacheProfile(Long targetId, TargetProfileDTO profile) {
        try {
            redisTemplate.opsForValue().set(
                    TARGET_CACHE_PREFIX + targetId,
                    JSONUtil.toJsonStr(profile),
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("缓存画像失败: id={}", targetId, e);
        }
    }

    /**
     * 解析 JSON 数组为 List<Map>
     *
     * @param json JSON 字符串
     * @return List<Map>，解析失败返回 null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> parseJsonArray(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            JSONArray array = JSONUtil.parseArray(json);
            List raw = array.toList(Map.class);
            return (List<Map<String, Object>>) (List) raw;
        } catch (Exception e) {
            log.warn("解析 JSON 数组失败: {}", json, e);
            return null;
        }
    }

    /**
     * 解析技术资产 JSON
     *
     * @param json JSON 字符串
     * @return 技术资产列表
     */
    private List<TargetProfileDTO.TechAsset> parseTechAssets(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSONUtil.toList(json, TargetProfileDTO.TechAsset.class);
        } catch (Exception e) {
            log.warn("解析技术资产 JSON 失败: {}", json, e);
            return null;
        }
    }

    /**
     * 解析攻击面 JSON
     *
     * @param json JSON 字符串
     * @return 攻击面对象
     */
    private TargetProfileDTO.AttackSurface parseAttackSurface(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSONUtil.toBean(json, TargetProfileDTO.AttackSurface.class);
        } catch (Exception e) {
            log.warn("解析攻击面 JSON 失败: {}", json, e);
            return null;
        }
    }

    /**
     * 加载目标的关联目标列表
     *
     * @param targetId 目标ID
     * @return 关联目标列表
     */
    private List<TargetProfileDTO.RelatedTarget> loadRelatedTargets(Long targetId) {
        List<TargetRelationEntity> relations = findRelationsOf(targetId);
        Set<Long> otherIds = new HashSet<>();
        for (TargetRelationEntity rel : relations) {
            Long other = rel.getSourceId().equals(targetId) ? rel.getTargetId() : rel.getSourceId();
            otherIds.add(other);
        }
        Map<Long, String> nameMap = new HashMap<>();
        if (!otherIds.isEmpty()) {
            listByIds(otherIds).forEach(t -> nameMap.put(t.getId(), t.getName()));
        }

        List<TargetProfileDTO.RelatedTarget> result = new ArrayList<>();
        for (TargetRelationEntity rel : relations) {
            Long other = rel.getSourceId().equals(targetId) ? rel.getTargetId() : rel.getSourceId();
            TargetProfileDTO.RelatedTarget r = new TargetProfileDTO.RelatedTarget();
            r.setTargetId(other);
            r.setTargetName(nameMap.get(other));
            r.setRelationType(rel.getRelationType());
            r.setStrength(rel.getWeight());
            result.add(r);
        }
        return result;
    }

    /**
     * 查询与指定目标相关的所有关系（源或目标任一端匹配）
     *
     * @param targetId 目标ID
     * @return 关系列表
     */
    private List<TargetRelationEntity> findRelationsOf(Long targetId) {
        LambdaQueryWrapper<TargetRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TargetRelationEntity::getSourceId, targetId)
                .or().eq(TargetRelationEntity::getTargetId, targetId);
        return targetRelationMapper.selectList(wrapper);
    }

    /**
     * 目标实体转换为图谱节点
     *
     * @param entity 目标实体
     * @param degree 关联数
     * @return 图谱节点
     */
    private TargetRelationDTO.GraphTarget toGraphNode(TargetEntity entity, int degree) {
        TargetRelationDTO.GraphTarget node = new TargetRelationDTO.GraphTarget();
        node.setId(String.valueOf(entity.getId()));
        node.setName(entity.getName());
        node.setType(entity.getType());
        node.setRiskLevel(entity.getRiskLevel());
        node.setCategory(mapTypeToCategory(entity.getType()));
        // 节点大小：基础 30 + 关联数 * 8，上限 100
        node.setSymbolSize(Math.min(100, 30 + degree * 8));
        node.setValue(degree);
        return node;
    }

    /**
     * 关系实体转换为图谱边
     *
     * @param rel 关系实体
     * @return 图谱边
     */
    private TargetRelationDTO.GraphEdge toGraphEdge(TargetRelationEntity rel) {
        TargetRelationDTO.GraphEdge edge = new TargetRelationDTO.GraphEdge();
        edge.setSource(String.valueOf(rel.getSourceId()));
        edge.setTarget(String.valueOf(rel.getTargetId()));
        edge.setRelationType(rel.getRelationType());
        edge.setWeight(rel.getWeight());
        edge.setDescription(rel.getDescription());
        return edge;
    }

    /**
     * 将目标类型枚举映射为可读类别名
     *
     * @param type 类型
     * @return 类别名
     */
    private String mapTypeToCategory(Integer type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case 1 -> "个人";
            case 2 -> "组织";
            case 3 -> "网站";
            case 4 -> "IP";
            case 5 -> "域名";
            case 6 -> "其他";
            default -> "未知";
        };
    }
}
