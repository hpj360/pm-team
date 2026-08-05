package com.redteam.notification.controller;

import com.redteam.common.result.PageResult;
import com.redteam.common.result.Result;
import com.redteam.notification.dto.BroadcastNotificationDTO;
import com.redteam.notification.dto.NotificationDTO;
import com.redteam.notification.dto.NotificationQueryDTO;
import com.redteam.notification.dto.NotificationStatsDTO;
import com.redteam.notification.dto.NotificationVO;
import com.redteam.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知控制器
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "通知接口", description = "通知的发送、广播、查询、已读标记、删除、统计等接口")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 发送通知
     *
     * @param dto 通知请求
     * @return 通知返回对象
     */
    @PostMapping
    @Operation(summary = "发送通知", description = "根据通道分发通知到站内信/邮件/IM")
    public Result<NotificationVO> send(@Valid @RequestBody NotificationDTO dto) {
        log.info("发送通知: userId={}, type={}, channel={}",
                dto.getUserId(), dto.getType(), dto.getChannel());
        return Result.success(notificationService.sendNotification(dto));
    }

    /**
     * 广播通知：向多个用户批量发送
     *
     * @param dto 广播请求
     * @return 已发送通知列表
     */
    @PostMapping("/broadcast")
    @Operation(summary = "广播通知", description = "向多个用户批量发送相同通知")
    public Result<List<NotificationVO>> broadcast(@Valid @RequestBody BroadcastNotificationDTO dto) {
        log.info("广播通知: userCount={}", dto.getUserIds().size());
        return Result.success(notificationService.broadcastNotification(dto));
    }

    /**
     * 查询通知详情
     *
     * @param notificationId 通知业务ID
     * @return 通知返回对象
     */
    @GetMapping("/{notificationId}")
    @Operation(summary = "查询通知详情", description = "根据通知业务ID查询通知详情")
    public Result<NotificationVO> get(
            @Parameter(description = "通知业务ID", required = true)
            @PathVariable("notificationId") String notificationId) {
        return Result.success(notificationService.getNotification(notificationId));
    }

    /**
     * 分页查询用户通知列表
     *
     * @param userId 用户ID
     * @param query  查询条件
     * @return 分页结果
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "分页查询用户通知", description = "分页查询指定用户的通知列表")
    public Result<PageResult<NotificationVO>> list(
            @Parameter(description = "用户ID", required = true)
            @PathVariable("userId") Long userId,
            NotificationQueryDTO query) {
        return Result.success(notificationService.listUserNotifications(userId, query));
    }

    /**
     * 标记单条通知为已读
     *
     * @param notificationId 通知业务ID
     * @return 是否成功
     */
    @PutMapping("/{notificationId}/read")
    @Operation(summary = "标记通知已读", description = "标记单条通知为已读")
    public Result<Void> markAsRead(
            @Parameter(description = "通知业务ID", required = true)
            @PathVariable("notificationId") String notificationId) {
        notificationService.markAsRead(notificationId);
        return Result.success();
    }

    /**
     * 标记用户全部通知为已读
     *
     * @param userId 用户ID
     * @return 是否成功
     */
    @PutMapping("/user/{userId}/read-all")
    @Operation(summary = "全部已读", description = "标记指定用户的全部通知为已读")
    public Result<Void> markAllAsRead(
            @Parameter(description = "用户ID", required = true)
            @PathVariable("userId") Long userId) {
        notificationService.markAllAsRead(userId);
        return Result.success();
    }

    /**
     * 删除通知
     *
     * @param notificationId 通知业务ID
     * @return 是否成功
     */
    @DeleteMapping("/{notificationId}")
    @Operation(summary = "删除通知", description = "逻辑删除指定通知")
    public Result<Void> delete(
            @Parameter(description = "通知业务ID", required = true)
            @PathVariable("notificationId") String notificationId) {
        notificationService.deleteNotification(notificationId);
        return Result.success();
    }

    /**
     * 获取用户未读通知数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    @GetMapping("/user/{userId}/unread-count")
    @Operation(summary = "未读数量", description = "获取指定用户的未读通知数量")
    public Result<Integer> unreadCount(
            @Parameter(description = "用户ID", required = true)
            @PathVariable("userId") Long userId) {
        return Result.success(notificationService.getUnreadCount(userId));
    }

    /**
     * 通知统计
     *
     * @param userId 用户ID（可选，不传则统计全局）
     * @return 统计结果
     */
    @GetMapping("/stats")
    @Operation(summary = "通知统计", description = "按类型/级别/通道/发送状态维度统计通知数量")
    public Result<NotificationStatsDTO> stats(
            @Parameter(description = "用户ID（可选）")
            @RequestParam(value = "userId", required = false) Long userId) {
        return Result.success(notificationService.getNotificationStats(userId));
    }

    /**
     * 清理过期通知
     *
     * @return 清理数量
     */
    @DeleteMapping("/expired")
    @Operation(summary = "清理过期通知", description = "物理删除已过期且未读的通知")
    public Result<Integer> cleanupExpired() {
        return Result.success(notificationService.cleanupExpired());
    }

    /**
     * 重试发送失败的通知
     *
     * @param notificationId 通知业务ID
     * @return 重试后的通知
     */
    @PostMapping("/{notificationId}/retry")
    @Operation(summary = "重试失败通知", description = "重新发送失败的通知，最多重试3次")
    public Result<NotificationVO> retryFailed(
            @Parameter(description = "通知业务ID", required = true)
            @PathVariable("notificationId") String notificationId) {
        return Result.success(notificationService.retryFailed(notificationId));
    }
}
