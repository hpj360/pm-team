package com.redteam.common.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.redteam.common.api.dto.FileTagVO;
import com.redteam.common.api.dto.TagDictDTO;
import com.redteam.common.api.dto.TagTreeVO;
import com.redteam.common.entity.FileTagEntity;
import com.redteam.common.entity.TagDictEntity;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.FileTagMapper;
import com.redteam.common.mapper.TagDictMapper;
import com.redteam.common.result.ResultCode;
import com.redteam.common.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 标签服务实现
 *
 * <p>基于 MyBatis-Plus BaseMapper 实现标签字典 CRUD、文件打标与按标签检索。
 * 文件打标采用「先查后插」的去重策略，避免唯一键冲突；删除标签时同步清理 file_tags 关联。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    /**
     * 默认标签来源（未指定 source 时使用）
     */
    private static final String DEFAULT_SOURCE = "MANUAL";

    /**
     * 虚拟根节点名称（标签树顶层占位）
     */
    private static final String VIRTUAL_ROOT_NAME = "全部标签";

    private final TagDictMapper tagDictMapper;
    private final FileTagMapper fileTagMapper;

    // ==================== 标签字典 CRUD ====================

    /**
     * 标签列表查询（参数为 null 时不筛选）
     */
    @Override
    public List<TagDictEntity> listTags(String layer, String category, Integer enabled) {
        LambdaQueryWrapper<TagDictEntity> wrapper = new LambdaQueryWrapper<>();
        if (layer != null) {
            wrapper.eq(TagDictEntity::getLayer, layer);
        }
        if (category != null) {
            wrapper.eq(TagDictEntity::getCategory, category);
        }
        if (enabled != null) {
            wrapper.eq(TagDictEntity::getEnabled, enabled);
        }
        wrapper.orderByAsc(TagDictEntity::getLayer)
                .orderByAsc(TagDictEntity::getParentCode)
                .orderByAsc(TagDictEntity::getTagCode);
        return tagDictMapper.selectList(wrapper);
    }

    /**
     * 构建标签层级树
     */
    @Override
    public TagTreeVO getTagTree() {
        // 仅查询启用标签，按层级排序保证父节点先于子节点出现
        LambdaQueryWrapper<TagDictEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TagDictEntity::getEnabled, 1)
                .orderByAsc(TagDictEntity::getLayer)
                .orderByAsc(TagDictEntity::getParentCode)
                .orderByAsc(TagDictEntity::getTagCode);
        List<TagDictEntity> tags = tagDictMapper.selectList(wrapper);

        // 按 parentCode 分组，根节点单独收集
        Map<String, List<TagDictEntity>> childrenByParentCode = new HashMap<>();
        List<TagDictEntity> roots = new ArrayList<>();
        for (TagDictEntity tag : tags) {
            if (StrUtil.isBlank(tag.getParentCode())) {
                roots.add(tag);
            } else {
                childrenByParentCode.computeIfAbsent(tag.getParentCode(), k -> new ArrayList<>()).add(tag);
            }
        }

        // 递归构建子树
        List<TagTreeVO> rootNodes = roots.stream()
                .map(tag -> buildTreeNode(tag, childrenByParentCode))
                .collect(Collectors.toList());

        // 返回虚拟根节点，便于前端统一渲染
        TagTreeVO virtualRoot = new TagTreeVO();
        virtualRoot.setTagCode(null);
        virtualRoot.setTagName(VIRTUAL_ROOT_NAME);
        virtualRoot.setEnabled(1);
        virtualRoot.setChildren(rootNodes);
        return virtualRoot;
    }

    /**
     * 按 ID 查询标签
     */
    @Override
    public TagDictEntity getTagById(Long id) {
        TagDictEntity entity = tagDictMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "标签不存在");
        }
        return entity;
    }

    /**
     * 创建标签（校验 tagCode 唯一性）
     */
    @Override
    public TagDictEntity createTag(TagDictDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getTagCode())) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "标签编码不能为空");
        }
        // 校验编码唯一性
        TagDictEntity existing = tagDictMapper.selectByCode(dto.getTagCode());
        if (existing != null) {
            throw BusinessException.of(ResultCode.RESOURCE_EXISTS, "标签编码已存在: " + dto.getTagCode());
        }

        TagDictEntity entity = new TagDictEntity();
        BeanUtil.copyProperties(dto, entity);
        // 设置默认值
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        if (entity.getIsMulti() == null) {
            entity.setIsMulti(0);
        }

        tagDictMapper.insert(entity);
        log.info("创建标签成功: id={}, tagCode={}", entity.getId(), entity.getTagCode());
        return entity;
    }

    /**
     * 更新标签（不允许修改 tagCode）
     */
    @Override
    public TagDictEntity updateTag(Long id, TagDictDTO dto) {
        if (dto == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "标签参数不能为空");
        }
        TagDictEntity entity = tagDictMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "标签不存在");
        }

        // 保留原编码（不允许修改 tagCode）
        String originalCode = entity.getTagCode();
        BeanUtil.copyProperties(dto, entity, "tagCode", "id", "createdAt", "updatedAt");
        entity.setTagCode(originalCode);

        tagDictMapper.updateById(entity);
        log.info("更新标签成功: id={}, tagCode={}", id, originalCode);
        return entity;
    }

    /**
     * 启用/禁用切换
     */
    @Override
    public void toggleTag(Long id) {
        TagDictEntity entity = tagDictMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "标签不存在");
        }
        Integer current = entity.getEnabled();
        entity.setEnabled(current != null && current == 1 ? 0 : 1);
        tagDictMapper.updateById(entity);
        log.info("切换标签状态: id={}, enabled={} -> {}", id, current, entity.getEnabled());
    }

    /**
     * 删除标签（同时删除 file_tags 中的关联）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        TagDictEntity entity = tagDictMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "标签不存在");
        }
        // 删除标签字典记录
        tagDictMapper.deleteById(id);
        // 同步删除文件标签关联
        fileTagMapper.delete(new LambdaQueryWrapper<FileTagEntity>()
                .eq(FileTagEntity::getTagId, id));
        log.info("删除标签成功: id={}, tagCode={}, 已清理文件标签关联", id, entity.getTagCode());
    }

    // ==================== 文件打标 ====================

    /**
     * 批量文件打标（去重，已有标签跳过）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addFileTags(Long fileId, List<Long> tagIds, String source) {
        if (fileId == null || tagIds == null || tagIds.isEmpty()) {
            return;
        }
        String tagSource = StrUtil.isBlank(source) ? DEFAULT_SOURCE : source;

        // 查询文件已有标签，构建已存在集合用于去重
        List<FileTagEntity> existing = fileTagMapper.selectByFileId(fileId);
        Set<Long> existingTagIds = existing.stream()
                .map(FileTagEntity::getTagId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 过滤出需要新增的标签（去 null、去重、跳过已存在）
        List<Long> toAdd = tagIds.stream()
                .filter(Objects::nonNull)
                .filter(tagId -> !existingTagIds.contains(tagId))
                .distinct()
                .collect(Collectors.toList());

        if (toAdd.isEmpty()) {
            log.info("文件打标无新增: fileId={}, 共 {} 个标签均已存在", fileId, tagIds.size());
            return;
        }

        // 查询标签信息以冗余 tagCode
        List<TagDictEntity> tags = tagDictMapper.selectBatchIds(toAdd);
        Map<Long, String> tagCodeMap = tags.stream()
                .collect(Collectors.toMap(TagDictEntity::getId, TagDictEntity::getTagCode, (a, b) -> a));

        List<FileTagEntity> entities = toAdd.stream().map(tagId -> {
            FileTagEntity ft = new FileTagEntity();
            ft.setFileId(fileId);
            ft.setTagId(tagId);
            ft.setTagCode(tagCodeMap.get(tagId));
            ft.setSource(tagSource);
            return ft;
        }).collect(Collectors.toList());

        fileTagMapper.batchInsert(entities);
        log.info("文件打标成功: fileId={}, 新增 {} 个标签（跳过 {} 个已存在）",
                fileId, toAdd.size(), tagIds.size() - toAdd.size());
    }

    /**
     * 取消文件标签
     */
    @Override
    public void removeFileTag(Long fileId, Long tagId) {
        int rows = fileTagMapper.deleteFileTag(fileId, tagId);
        log.info("取消文件标签: fileId={}, tagId={}, 删除 {} 条记录", fileId, tagId, rows);
    }

    /**
     * 查询文件标签（关联 tag_dict_v2）
     */
    @Override
    public List<FileTagVO> getFileTags(Long fileId) {
        List<FileTagEntity> fileTags = fileTagMapper.selectByFileId(fileId);
        if (fileTags == null || fileTags.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询标签字典信息
        List<Long> tagIds = fileTags.stream()
                .map(FileTagEntity::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, TagDictEntity> tagMap = tagIds.isEmpty()
                ? Collections.emptyMap()
                : tagDictMapper.selectBatchIds(tagIds).stream()
                        .collect(Collectors.toMap(TagDictEntity::getId, t -> t, (a, b) -> a));

        return fileTags.stream().map(ft -> {
            FileTagVO vo = new FileTagVO();
            vo.setFileId(ft.getFileId());
            vo.setTagId(ft.getTagId());
            vo.setTagCode(ft.getTagCode());
            vo.setSource(ft.getSource());
            vo.setCreatedAt(ft.getCreatedAt());
            TagDictEntity tag = tagMap.get(ft.getTagId());
            if (tag != null) {
                vo.setTagName(tag.getTagName());
                vo.setLayer(tag.getLayer());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== 按标签检索文件 ====================

    /**
     * 按标签ID检索文件
     */
    @Override
    public List<Long> getFileIdsByTagId(Long tagId) {
        if (tagId == null) {
            return Collections.emptyList();
        }
        return fileTagMapper.selectByTagId(tagId).stream()
                .map(FileTagEntity::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 按标签编码检索文件
     */
    @Override
    public List<Long> getFileIdsByTagCode(String tagCode) {
        if (StrUtil.isBlank(tagCode)) {
            return Collections.emptyList();
        }
        TagDictEntity tag = tagDictMapper.selectByCode(tagCode);
        if (tag == null) {
            log.info("标签编码不存在: {}", tagCode);
            return Collections.emptyList();
        }
        return getFileIdsByTagId(tag.getId());
    }

    // ==================== 私有方法 ====================

    /**
     * 递归构建标签树节点
     *
     * @param entity             标签实体
     * @param childrenByParentCode 父编码 -> 子标签列表
     * @return 树节点
     */
    private TagTreeVO buildTreeNode(TagDictEntity entity,
                                    Map<String, List<TagDictEntity>> childrenByParentCode) {
        TagTreeVO vo = new TagTreeVO();
        vo.setId(entity.getId());
        vo.setTagCode(entity.getTagCode());
        vo.setTagName(entity.getTagName());
        vo.setLayer(entity.getLayer());
        vo.setCategory(entity.getCategory());
        vo.setEnabled(entity.getEnabled());

        List<TagDictEntity> children = childrenByParentCode.getOrDefault(entity.getTagCode(), Collections.emptyList());
        vo.setChildren(children.stream()
                .map(child -> buildTreeNode(child, childrenByParentCode))
                .collect(Collectors.toList()));
        return vo;
    }
}
