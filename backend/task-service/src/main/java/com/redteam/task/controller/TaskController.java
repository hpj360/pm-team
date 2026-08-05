package com.redteam.task.controller;

import com.redteam.common.result.PageResult;
import com.redteam.common.result.Result;
import com.redteam.task.dto.TaskDTO;
import com.redteam.task.dto.TaskQueryDTO;
import com.redteam.task.dto.TaskStatsDTO;
import com.redteam.task.dto.TaskVO;
import com.redteam.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务管理控制器
 * <p>
 * 提供任务的CRUD接口及状态流转接口，
 * 所有接口返回统一 {@link Result} 格式。
 * </p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Validated
@Tag(name = "任务管理", description = "任务创建、查询、更新、删除及状态流转接口")
public class TaskController {

    private final TaskService taskService;

    /**
     * 创建任务
     *
     * @param dto 任务信息
     * @return 创建结果
     */
    @PostMapping
    @Operation(summary = "创建任务", description = "创建新的红方任务，自动生成任务ID")
    public Result<TaskVO> createTask(@Valid @RequestBody TaskDTO dto) {
        log.info("接收到创建任务请求: taskName={}", dto.getTaskName());
        return Result.success(taskService.createTask(dto));
    }

    /**
     * 查询任务详情
     *
     * @param taskId 任务ID
     * @return 任务详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "查询任务详情", description = "根据任务ID查询任务详细信息")
    public Result<TaskVO> getTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId) {
        return Result.success(taskService.getTask(taskId));
    }

    /**
     * 更新任务
     *
     * @param taskId 任务ID
     * @param dto    任务信息
     * @return 更新结果
     */
    @PutMapping("/{taskId}")
    @Operation(summary = "更新任务", description = "更新任务的基本信息")
    public Result<TaskVO> updateTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId,
            @Valid @RequestBody TaskDTO dto) {
        return Result.success(taskService.updateTask(taskId, dto));
    }

    /**
     * 删除任务
     *
     * @param taskId 任务ID
     * @return 删除结果
     */
    @DeleteMapping("/{taskId}")
    @Operation(summary = "删除任务", description = "根据任务ID删除任务（逻辑删除）")
    public Result<Void> deleteTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId) {
        taskService.deleteTask(taskId);
        return Result.success();
    }

    /**
     * 分页查询任务列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询任务", description = "根据条件分页查询任务列表")
    public Result<PageResult<TaskVO>> listTasks(TaskQueryDTO query) {
        return Result.success(taskService.listTasks(query));
    }

    /**
     * 启动任务
     *
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/{taskId}/start")
    @Operation(summary = "启动任务", description = "将任务状态从待执行变更为执行中")
    public Result<Void> startTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId) {
        taskService.startTask(taskId);
        return Result.success();
    }

    /**
     * 暂停任务
     *
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/{taskId}/pause")
    @Operation(summary = "暂停任务", description = "将任务状态从执行中变更为已暂停")
    public Result<Void> pauseTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId) {
        taskService.pauseTask(taskId);
        return Result.success();
    }

    /**
     * 完成任务
     *
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/{taskId}/complete")
    @Operation(summary = "完成任务", description = "将任务状态变更为已完成")
    public Result<Void> completeTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId) {
        taskService.completeTask(taskId);
        return Result.success();
    }

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     * @return 操作结果
     */
    @PostMapping("/{taskId}/cancel")
    @Operation(summary = "取消任务", description = "将任务状态变更为已取消")
    public Result<Void> cancelTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId) {
        taskService.cancelTask(taskId);
        return Result.success();
    }

    /**
     * 分配任务给指定负责人
     *
     * @param taskId  任务ID
     * @param ownerId 负责人ID
     * @return 更新后的任务
     */
    @PostMapping("/{taskId}/assign")
    @Operation(summary = "分配任务", description = "将任务分配给指定负责人")
    public Result<TaskVO> assignTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId,
            @Parameter(description = "负责人ID") @RequestParam("ownerId")
            @NotNull(message = "负责人ID不能为空") Long ownerId) {
        log.info("接收到分配任务请求: taskId={}, ownerId={}", taskId, ownerId);
        return Result.success(taskService.assignTask(taskId, ownerId));
    }

    /**
     * 更新任务状态
     *
     * @param taskId 任务ID
     * @param status 新状态（PENDING/RUNNING/PAUSED/COMPLETED/CANCELLED）
     * @return 更新后的任务
     */
    @PostMapping("/{taskId}/status")
    @Operation(summary = "更新任务状态", description = "更新任务状态，需符合状态流转规则")
    public Result<TaskVO> updateStatus(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId,
            @Parameter(description = "新状态") @RequestParam("status") String status) {
        log.info("接收到更新任务状态请求: taskId={}, status={}", taskId, status);
        return Result.success(taskService.updateStatus(taskId, status));
    }

    /**
     * 更新任务进度
     *
     * @param taskId   任务ID
     * @param progress 进度（0-100）
     * @return 更新后的任务
     */
    @PostMapping("/{taskId}/progress")
    @Operation(summary = "更新任务进度", description = "更新任务进度（0-100），进度到100自动转为已完成")
    public Result<TaskVO> updateProgress(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId,
            @Parameter(description = "进度(0-100)") @RequestParam("progress")
            @Min(value = 0, message = "进度不能小于0") @Max(value = 100, message = "进度不能大于100") Integer progress) {
        log.info("接收到更新任务进度请求: taskId={}, progress={}", taskId, progress);
        return Result.success(taskService.updateProgress(taskId, progress));
    }

    /**
     * 任务统计
     *
     * @return 统计结果
     */
    @GetMapping("/stats")
    @Operation(summary = "任务统计", description = "按状态、优先级、负责人维度统计任务数量")
    public Result<TaskStatsDTO> getTaskStats() {
        return Result.success(taskService.getTaskStats());
    }
}
