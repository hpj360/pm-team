package com.redteam.common.service;

import com.redteam.common.api.dto.TagDictDTO;
import com.redteam.common.api.dto.TagTreeVO;
import com.redteam.common.api.dto.FileTagVO;
import com.redteam.common.entity.TagDictEntity;

import java.util.List;

/**
 * 标签服务接口
 *
 * <p>提供标签字典 CRUD、文件打标及按标签检索文件的能力。
 * 字典层覆盖 L1-L6 六层架构，文件打标支持去重与多来源（AUTO/MANUAL）。</p>
 *
 * @author 红方团队
 */
public interface TagService {

    // ==================== 标签字典 CRUD ====================

    /**
     * 标签列表查询（参数为 null 时不筛选）
     *
     * @param layer   层级：L1-L6（可空）
     * @param category 分类：FILE/BUSINESS/ENTITY/SCENE/INTEL/COMPLIANCE（可空）
     * @param enabled 启用状态：0禁用 1启用（可空）
     * @return 标签列表
     */
    List<TagDictEntity> listTags(String layer, String category, Integer enabled);

    /**
     * 获取标签层级树（仅启用标签，按 parent_code 组装）
     *
     * @return 标签树根节点列表
     */
    TagTreeVO getTagTree();

    /**
     * 按 ID 查询标签
     *
     * @param id 标签ID
     * @return 标签实体
     */
    TagDictEntity getTagById(Long id);

    /**
     * 创建标签（校验 tagCode 唯一性）
     *
     * @param dto 标签数据
     * @return 创建后的标签实体
     */
    TagDictEntity createTag(TagDictDTO dto);

    /**
     * 更新标签（不允许修改 tagCode）
     *
     * @param id  标签ID
     * @param dto 标签数据
     * @return 更新后的标签实体
     */
    TagDictEntity updateTag(Long id, TagDictDTO dto);

    /**
     * 启用/禁用标签切换
     *
     * @param id 标签ID
     */
    void toggleTag(Long id);

    /**
     * 删除标签（同时删除 file_tags 中的关联）
     *
     * @param id 标签ID
     */
    void deleteTag(Long id);

    // ==================== 文件打标 ====================

    /**
     * 批量为文件打标（已有标签跳过，source 默认 MANUAL）
     *
     * @param fileId 文件ID
     * @param tagIds 标签ID列表
     * @param source 标签来源：AUTO/MANUAL（为空时默认 MANUAL）
     */
    void addFileTags(Long fileId, List<Long> tagIds, String source);

    /**
     * 取消文件的某个标签
     *
     * @param fileId 文件ID
     * @param tagId  标签ID
     */
    void removeFileTag(Long fileId, Long tagId);

    /**
     * 查询文件的标签列表（关联 tag_dict_v2）
     *
     * @param fileId 文件ID
     * @return 文件标签 VO 列表
     */
    List<FileTagVO> getFileTags(Long fileId);

    // ==================== 按标签检索文件 ====================

    /**
     * 按标签ID检索文件
     *
     * @param tagId 标签ID
     * @return 文件ID列表
     */
    List<Long> getFileIdsByTagId(Long tagId);

    /**
     * 按标签编码检索文件（先查 tag_dict_v2 获取 tagId，再查 file_tags）
     *
     * @param tagCode 标签编码
     * @return 文件ID列表
     */
    List<Long> getFileIdsByTagCode(String tagCode);
}
