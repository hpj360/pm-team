package com.redteam.search.controller;

import com.redteam.common.result.Result;
import com.redteam.common.service.AccessControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 分级访问控制控制器
 *
 * <p>提供文件密级管理、用户许可等级管理以及访问权限校验接口。</p>
 *
 * <p>校验规则：用户许可等级 ≥ 文件密级等级时允许访问；
 * 管理员（许可等级 99）绕过所有密级校验。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/access-control")
@RequiredArgsConstructor
@Tag(name = "分级访问控制", description = "文件密级管理、用户许可等级管理及访问权限校验")
public class AccessControlController {

    private final AccessControlService accessControlService;

    /**
     * 设置文件密级
     *
     * @param fileId         文件ID
     * @param classification 密级编码（PUBLIC/INTERNAL/CONFIDENTIAL/SECRET）
     * @return 操作结果
     */
    @PutMapping("/files/{fileId}/classification")
    @Operation(summary = "设置文件密级", description = "更新指定文件的密级编码")
    public Result<Void> setClassification(
            @PathVariable Long fileId,
            @Parameter(description = "密级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET")
            @RequestParam String classification) {
        log.info("设置文件密级: fileId={}, classification={}", fileId, classification);
        accessControlService.setFileClassification(fileId, classification);
        return Result.success();
    }

    /**
     * 设置用户许可等级
     *
     * @param userId         用户ID
     * @param clearanceLevel 许可等级（1-4 或 99-管理员）
     * @return 操作结果
     */
    @PutMapping("/users/{userId}/clearance")
    @Operation(summary = "设置用户许可等级", description = "更新指定用户的许可等级")
    public Result<Void> setClearance(
            @PathVariable Long userId,
            @Parameter(description = "许可等级：1-PUBLIC 2-INTERNAL 3-CONFIDENTIAL 4-SECRET 99-管理员")
            @RequestParam Integer clearanceLevel) {
        log.info("设置用户许可等级: userId={}, clearanceLevel={}", userId, clearanceLevel);
        accessControlService.setUserClearance(userId, clearanceLevel);
        return Result.success();
    }

    /**
     * 检查访问权限
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 校验结果，包含 allowed、fileClassification、userClearance、reason 字段
     */
    @GetMapping("/check")
    @Operation(summary = "检查访问权限", description = "校验指定用户对指定文件的访问权限")
    public Result<Map<String, Object>> checkAccess(
            @Parameter(description = "文件ID")
            @RequestParam Long fileId,
            @Parameter(description = "用户ID")
            @RequestParam Long userId) {
        log.info("检查访问权限: fileId={}, userId={}", fileId, userId);
        Map<String, Object> result = accessControlService.checkAccess(fileId, userId);
        return Result.success(result);
    }

    /**
     * 查询文件密级
     *
     * @param fileId 文件ID
     * @return 密级编码
     */
    @GetMapping("/files/{fileId}/classification")
    @Operation(summary = "查询文件密级", description = "查询指定文件的密级编码")
    public Result<String> getFileClassification(@PathVariable Long fileId) {
        return Result.success(accessControlService.getFileClassification(fileId));
    }

    /**
     * 查询用户许可等级
     *
     * @param userId 用户ID
     * @return 许可等级
     */
    @GetMapping("/users/{userId}/clearance")
    @Operation(summary = "查询用户许可等级", description = "查询指定用户的许可等级")
    public Result<Integer> getUserClearance(@PathVariable Long userId) {
        return Result.success(accessControlService.getUserClearance(userId));
    }
}
