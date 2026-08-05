package com.redteam.search.controller;

import com.redteam.common.annotation.AuditLog;
import com.redteam.common.annotation.RateLimit;
import com.redteam.common.api.dto.FileTagVO;
import com.redteam.common.api.dto.TagDictDTO;
import com.redteam.common.api.dto.TagTreeVO;
import com.redteam.common.entity.TagDictEntity;
import com.redteam.common.result.Result;
import com.redteam.common.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 标签管理控制器
 *
 * <p>提供标签字典 CRUD、文件打标、按标签检索文件等接口。
 * 标签体系覆盖 L1-L6 六层架构，详见 {@code tag_dict_v2} 表。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@Tag(name = "标签管理", description = "标签字典CRUD、文件打标与按标签检索文件接口")
public class TagController {

    private final TagService tagService;

    /**
     * 标签列表查询（支持按层级/分类/启用状态筛选）
     *
     * @param layer    层级：L1-L6（可空）
     * @param category 分类：FILE/BUSINESS/ENTITY/SCENE/INTEL/COMPLIANCE（可空）
     * @param enabled  启用状态：0禁用 1启用（可空）
     * @return 标签列表
     */
    @GetMapping
    @Operation(summary = "标签列表查询", description = "按层级/分类/启用状态筛选，参数为空时不筛选")
    @RateLimit(qps = 20, limitType = "USER")
    public Result<List<TagDictEntity>> list(
            @RequestParam(required = false) String layer,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer enabled) {
        List<TagDictEntity> list = tagService.listTags(layer, category, enabled);
        return Result.success(list);
    }

    /**
     * 获取标签层级树
     *
     * @return 标签树
     */
    @GetMapping("/tree")
    @Operation(summary = "标签层级树", description = "返回启用标签按 parent_code 组装的树形结构")
    public Result<TagTreeVO> tree() {
        TagTreeVO tree = tagService.getTagTree();
        return Result.success(tree);
    }

    /**
     * 标签详情
     *
     * @param id 标签ID
     * @return 标签实体
     */
    @GetMapping("/{id}")
    @Operation(summary = "标签详情", description = "按ID查询标签详情")
    public Result<TagDictEntity> getById(@PathVariable("id") Long id) {
        TagDictEntity entity = tagService.getTagById(id);
        return Result.success(entity);
    }

    /**
     * 创建标签
     *
     * @param dto 标签数据
     * @return 创建后的标签
     */
    @PostMapping
    @Operation(summary = "创建标签", description = "创建标签，需保证 tagCode 唯一")
    public Result<TagDictEntity> create(@RequestBody @Valid TagDictDTO dto) {
        log.info("创建标签: tagCode={}", dto.getTagCode());
        TagDictEntity entity = tagService.createTag(dto);
        return Result.success(entity);
    }

    /**
     * 更新标签（不允许修改 tagCode）
     *
     * @param id  标签ID
     * @param dto 标签数据
     * @return 更新后的标签
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新标签", description = "更新标签信息，tagCode 不可修改")
    public Result<TagDictEntity> update(@PathVariable("id") Long id,
                                        @RequestBody @Valid TagDictDTO dto) {
        log.info("更新标签: id={}", id);
        TagDictEntity entity = tagService.updateTag(id, dto);
        return Result.success(entity);
    }

    /**
     * 启用/禁用标签
     *
     * @param id 标签ID
     * @return 操作结果
     */
    @PatchMapping("/{id}/toggle")
    @Operation(summary = "启用/禁用标签", description = "切换标签的 enabled 状态")
    public Result<Void> toggle(@PathVariable("id") Long id) {
        log.info("切换标签状态: id={}", id);
        tagService.toggleTag(id);
        return Result.success();
    }

    /**
     * 删除标签（同步清理文件标签关联）
     *
     * @param id 标签ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签", description = "删除标签及对应的文件标签关联")
    @AuditLog(action = "DELETE", resourceType = "TAG", resourceIdParam = "id")
    public Result<Void> delete(@PathVariable("id") Long id) {
        log.info("删除标签: id={}", id);
        tagService.deleteTag(id);
        return Result.success();
    }

    /**
     * 文件打标
     *
     * @param fileId 文件ID
     * @param tagIds 标签ID列表
     * @return 操作结果
     */
    @PostMapping("/files/{fileId}")
    @Operation(summary = "文件打标", description = "批量为文件打标，已有标签自动跳过")
    @AuditLog(action = "TAG", resourceType = "FILE", resourceIdParam = "fileId")
    public Result<Void> addFileTags(@PathVariable("fileId") Long fileId,
                                    @RequestBody List<Long> tagIds) {
        log.info("文件打标: fileId={}, tagIds={}", fileId, tagIds);
        tagService.addFileTags(fileId, tagIds, null);
        return Result.success();
    }

    /**
     * 取消文件标签
     *
     * @param fileId 文件ID
     * @param tagId  标签ID
     * @return 操作结果
     */
    @DeleteMapping("/files/{fileId}/{tagId}")
    @Operation(summary = "取消文件标签", description = "删除文件的某个标签关联")
    public Result<Void> removeFileTag(@PathVariable("fileId") Long fileId,
                                      @PathVariable("tagId") Long tagId) {
        log.info("取消文件标签: fileId={}, tagId={}", fileId, tagId);
        tagService.removeFileTag(fileId, tagId);
        return Result.success();
    }

    /**
     * 查询文件标签
     *
     * @param fileId 文件ID
     * @return 文件标签列表
     */
    @GetMapping("/files/{fileId}")
    @Operation(summary = "查询文件标签", description = "返回文件已打的全部标签信息")
    public Result<List<FileTagVO>> getFileTags(@PathVariable("fileId") Long fileId) {
        List<FileTagVO> list = tagService.getFileTags(fileId);
        return Result.success(list);
    }

    /**
     * 按标签检索文件
     *
     * @param tagId 标签ID
     * @return 文件ID列表
     */
    @GetMapping("/{tagId}/files")
    @Operation(summary = "按标签检索文件", description = "返回打有指定标签的文件ID列表")
    public Result<List<Long>> getFilesByTag(@PathVariable("tagId") Long tagId) {
        List<Long> fileIds = tagService.getFileIdsByTagId(tagId);
        return Result.success(fileIds);
    }
}
