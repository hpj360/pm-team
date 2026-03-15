package com.redteam.profile.controller;

import com.redteam.common.result.Result;
import com.redteam.profile.dto.TargetProfileDTO;
import com.redteam.profile.entity.TargetEntity;
import com.redteam.profile.service.TargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 目标画像控制器
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/target")
@RequiredArgsConstructor
@Tag(name = "目标画像接口", description = "目标信息管理、画像生成等接口")
public class TargetController {

    private final TargetService targetService;

    /**
     * 创建目标
     *
     * @param name        目标名称
     * @param type        目标类型
     * @param description 描述
     * @param tags        标签
     * @return 目标信息
     */
    @PostMapping("/create")
    @Operation(summary = "创建目标", description = "创建新的目标")
    public Result<TargetEntity> createTarget(
            @Parameter(description = "目标名称") @RequestParam("name") String name,
            @Parameter(description = "目标类型") @RequestParam("type") Integer type,
            @Parameter(description = "描述") @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "标签") @RequestParam(value = "tags", required = false) String tags) {

        log.info("创建目标: name={}", name);
        TargetEntity target = targetService.createTarget(name, type, description, tags);
        return Result.success(target);
    }

    /**
     * 获取目标详情
     *
     * @param id 目标ID
     * @return 目标信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取目标详情", description = "获取目标详细信息")
    public Result<TargetEntity> getTarget(
            @Parameter(description = "目标ID") @PathVariable("id") Long id) {

        log.info("获取目标详情: id={}", id);
        TargetEntity target = targetService.getTargetById(id);
        return Result.success(target);
    }

    /**
     * 获取目标画像
     *
     * @param id 目标ID
     * @return 画像信息
     */
    @GetMapping("/profile/{id}")
    @Operation(summary = "获取目标画像", description = "获取目标的完整画像信息")
    public Result<TargetProfileDTO> getTargetProfile(
            @Parameter(description = "目标ID") @PathVariable("id") Long id) {

        log.info("获取目标画像: id={}", id);
        TargetProfileDTO profile = targetService.getTargetProfile(id);
        return Result.success(profile);
    }

    /**
     * 生成目标画像
     *
     * @param id 目标ID
     * @return 画像信息
     */
    @PostMapping("/profile/generate/{id}")
    @Operation(summary = "生成目标画像", description = "根据关联文件生成目标画像")
    public Result<TargetProfileDTO> generateProfile(
            @Parameter(description = "目标ID") @PathVariable("id") Long id) {

        log.info("生成目标画像: id={}", id);
        TargetProfileDTO profile = targetService.generateProfile(id);
        return Result.success(profile);
    }

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
    @PutMapping("/{id}")
    @Operation(summary = "更新目标信息", description = "更新目标的基本信息")
    public Result<TargetEntity> updateTarget(
            @Parameter(description = "目标ID") @PathVariable("id") Long id,
            @Parameter(description = "名称") @RequestParam(value = "name", required = false) String name,
            @Parameter(description = "描述") @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "标签") @RequestParam(value = "tags", required = false) String tags,
            @Parameter(description = "风险等级") @RequestParam(value = "riskLevel", required = false) Integer riskLevel) {

        log.info("更新目标: id={}", id);
        TargetEntity target = targetService.updateTarget(id, name, description, tags, riskLevel);
        return Result.success(target);
    }

    /**
     * 删除目标
     *
     * @param id 目标ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除目标", description = "删除指定目标")
    public Result<Void> deleteTarget(
            @Parameter(description = "目标ID") @PathVariable("id") Long id) {

        log.info("删除目标: id={}", id);
        targetService.deleteTarget(id);
        return Result.success();
    }

    /**
     * 关注/取消关注目标
     *
     * @param id        目标ID
     * @param isFollowed 是否关注
     * @return 是否成功
     */
    @PostMapping("/follow/{id}")
    @Operation(summary = "关注目标", description = "关注或取消关注目标")
    public Result<Void> followTarget(
            @Parameter(description = "目标ID") @PathVariable("id") Long id,
            @Parameter(description = "是否关注") @RequestParam("isFollowed") Boolean isFollowed) {

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
    @Operation(summary = "搜索目标", description = "根据关键词搜索目标")
    public Result<List<TargetEntity>> searchTargets(
            @Parameter(description = "关键词") @RequestParam(value = "keyword", required = false) String keyword,
            @Parameter(description = "类型") @RequestParam(value = "type", required = false) Integer type) {

        log.info("搜索目标: keyword={}", keyword);
        List<TargetEntity> targets = targetService.searchTargets(keyword, type);
        return Result.success(targets);
    }
}
