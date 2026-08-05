package com.redteam.profile.controller;

import com.redteam.common.result.PageResult;
import com.redteam.common.result.Result;
import com.redteam.profile.dto.TargetDTO;
import com.redteam.profile.dto.TargetProfileDTO;
import com.redteam.profile.dto.TargetQueryDTO;
import com.redteam.profile.dto.TargetRelationDTO;
import com.redteam.profile.dto.TargetRelationRequestDTO;
import com.redteam.profile.entity.TargetEntity;
import com.redteam.profile.service.TargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 目标画像控制器
 *
 * <p>提供目标的 CRUD、画像聚合、关系图谱管理等 RESTful 接口。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/targets")
@RequiredArgsConstructor
@Tag(name = "目标画像接口", description = "目标信息管理、画像生成、关系图谱等接口")
public class TargetController {

    private final TargetService targetService;

    /**
     * 创建目标
     *
     * @param dto 目标创建 DTO
     * @return 创建后的目标
     */
    @PostMapping
    @Operation(summary = "创建目标", description = "创建新的目标，包含基本信息、攻击面、技术资产等")
    public Result<TargetEntity> createTarget(@Valid @RequestBody TargetDTO dto) {
        log.info("创建目标: name={}", dto.getName());
        return Result.success(targetService.createTarget(dto));
    }

    /**
     * 获取目标详情
     *
     * @param id 目标ID
     * @return 目标信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取目标详情", description = "根据目标ID获取目标详细信息")
    public Result<TargetEntity> getTarget(
            @Parameter(description = "目标ID", required = true) @PathVariable("id") Long id) {
        log.info("获取目标详情: id={}", id);
        return Result.success(targetService.getTarget(id));
    }

    /**
     * 更新目标信息
     *
     * @param id  目标ID
     * @param dto 目标更新 DTO
     * @return 更新后的目标
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新目标信息", description = "更新目标的基本信息、攻击面、技术资产等")
    public Result<TargetEntity> updateTarget(
            @Parameter(description = "目标ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody TargetDTO dto) {
        log.info("更新目标: id={}", id);
        return Result.success(targetService.updateTarget(id, dto));
    }

    /**
     * 删除目标
     *
     * @param id 目标ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除目标", description = "逻辑删除指定目标及其关联关系")
    public Result<Void> deleteTarget(
            @Parameter(description = "目标ID", required = true) @PathVariable("id") Long id) {
        log.info("删除目标: id={}", id);
        targetService.deleteTarget(id);
        return Result.success();
    }

    /**
     * 分页查询目标列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询目标", description = "支持按类型、行业、风险等级、关注状态、关键词查询")
    public Result<PageResult<TargetEntity>> listTargets(TargetQueryDTO query) {
        log.info("分页查询目标");
        return Result.success(targetService.listTargets(query));
    }

    /**
     * 获取目标完整画像
     *
     * @param id 目标ID
     * @return 画像信息
     */
    @GetMapping("/{id}/profile")
    @Operation(summary = "获取目标画像", description = "聚合目标的完整画像信息")
    public Result<TargetProfileDTO> getTargetProfile(
            @Parameter(description = "目标ID", required = true) @PathVariable("id") Long id) {
        log.info("获取目标画像: id={}", id);
        return Result.success(targetService.getTargetProfile(id));
    }

    /**
     * 生成目标画像
     *
     * @param id 目标ID
     * @return 画像信息
     */
    @PostMapping("/{id}/profile/generate")
    @Operation(summary = "生成目标画像", description = "根据目标信息生成画像并缓存")
    public Result<TargetProfileDTO> generateProfile(
            @Parameter(description = "目标ID", required = true) @PathVariable("id") Long id) {
        log.info("生成目标画像: id={}", id);
        return Result.success(targetService.generateProfile(id));
    }

    /**
     * 获取目标关系图谱
     *
     * @param id    根目标ID
     * @param depth 关系展开深度（默认 1，最大 3）
     * @return 关系图谱数据
     */
    @GetMapping("/{id}/relation-graph")
    @Operation(summary = "获取目标关系图谱", description = "返回节点+边，用于前端 ECharts 关系图渲染")
    public Result<TargetRelationDTO> getRelationGraph(
            @Parameter(description = "根目标ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "展开深度") @RequestParam(value = "depth", defaultValue = "1") Integer depth) {
        log.info("获取目标关系图谱: id={}, depth={}", id, depth);
        return Result.success(targetService.getRelationGraph(id, depth));
    }

    /**
     * 添加目标关系
     *
     * @param dto 关系创建请求
     * @return 操作结果
     */
    @PostMapping("/relations")
    @Operation(summary = "添加目标关系", description = "在两个目标之间建立指定类型的关系")
    public Result<Void> addRelation(@Valid @RequestBody TargetRelationRequestDTO dto) {
        log.info("添加目标关系: sourceId={}, targetId={}", dto.getSourceId(), dto.getTargetId());
        targetService.addRelation(dto);
        return Result.success();
    }

    /**
     * 删除目标关系
     *
     * @param relationId 关系ID
     * @return 操作结果
     */
    @DeleteMapping("/relations/{relationId}")
    @Operation(summary = "删除目标关系", description = "根据关系ID删除目标关系")
    public Result<Void> removeRelation(
            @Parameter(description = "关系ID", required = true) @PathVariable("relationId") Long relationId) {
        log.info("删除目标关系: relationId={}", relationId);
        targetService.removeRelation(relationId);
        return Result.success();
    }

    /**
     * 关注/取消关注目标
     *
     * @param id         目标ID
     * @param isFollowed 是否关注
     * @return 操作结果
     */
    @PostMapping("/{id}/follow")
    @Operation(summary = "关注目标", description = "关注或取消关注目标")
    public Result<Void> followTarget(
            @Parameter(description = "目标ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "是否关注", required = true) @RequestParam("isFollowed") Boolean isFollowed) {
        log.info("关注目标: id={}, isFollowed={}", id, isFollowed);
        targetService.followTarget(id, isFollowed);
        return Result.success();
    }

    /**
     * 搜索目标
     *
     * @param keyword 关键词
     * @param type    类型
     * @return 目标列表
     */
    @GetMapping("/search")
    @Operation(summary = "搜索目标", description = "按关键词与类型搜索目标")
    public Result<List<TargetEntity>> searchTargets(
            @Parameter(description = "关键词") @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "类型") @RequestParam(value = "type", required = false) Integer type) {
        log.info("搜索目标: keyword={}", keyword);
        return Result.success(targetService.searchTargets(keyword, type));
    }
}
