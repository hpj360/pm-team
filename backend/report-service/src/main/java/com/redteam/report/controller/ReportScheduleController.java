package com.redteam.report.controller;

import com.redteam.common.result.PageResult;
import com.redteam.common.result.Result;
import com.redteam.report.dto.ReportScheduleCreateDTO;
import com.redteam.report.dto.ReportScheduleVO;
import com.redteam.report.service.ReportSchedulerService;
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
 * 定时报告调度管理 REST 接口
 *
 * <p>提供定时报告配置的创建、查询、启停切换、删除及执行历史查询等接口，
 * 统一前缀 {@code /api/report/schedules}。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/report/schedules")
@RequiredArgsConstructor
@Tag(name = "定时报告调度接口", description = "定时报告的创建、查询、启停、删除及执行历史")
public class ReportScheduleController {

    private final ReportSchedulerService schedulerService;

    /**
     * 创建定时报告配置。
     *
     * @param dto 创建请求
     * @return 创建后的配置
     */
    @PostMapping
    @Operation(summary = "创建定时报告", description = "创建定时报告配置并立即注册到调度器")
    public Result<ReportScheduleVO> createSchedule(@Valid @RequestBody ReportScheduleCreateDTO dto) {
        log.info("创建定时报告: name={}, cron={}", dto.getReportName(), dto.getCronExpression());
        ReportScheduleVO vo = schedulerService.createSchedule(dto);
        return Result.success(vo);
    }

    /**
     * 分页查询定时报告列表。
     *
     * @param current 当前页码
     * @param size    每页大小
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询定时报告", description = "分页查询所有定时报告配置")
    public Result<PageResult<ReportScheduleVO>> listSchedules(
            @Parameter(description = "当前页码") @RequestParam(value = "current", defaultValue = "1") Long current,
            @Parameter(description = "每页大小") @RequestParam(value = "size", defaultValue = "10") Long size) {
        log.info("分页查询定时报告: current={}, size={}", current, size);
        PageResult<ReportScheduleVO> page = schedulerService.listSchedules(current, size);
        return Result.success(page);
    }

    /**
     * 获取定时报告详情。
     *
     * @param id 调度ID
     * @return 配置详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取定时报告详情", description = "根据调度ID获取定时报告配置详情")
    public Result<ReportScheduleVO> getSchedule(
            @Parameter(description = "调度ID", required = true) @PathVariable("id") Long id) {
        log.info("获取定时报告详情: id={}", id);
        ReportScheduleVO vo = schedulerService.getSchedule(id);
        return Result.success(vo);
    }

    /**
     * 切换定时报告启停状态。
     *
     * @param id 调度ID
     * @return 切换后的配置
     */
    @PutMapping("/{id}/toggle")
    @Operation(summary = "切换启停状态", description = "切换定时报告的 ACTIVE/INACTIVE 状态")
    public Result<ReportScheduleVO> toggleSchedule(
            @Parameter(description = "调度ID", required = true) @PathVariable("id") Long id) {
        log.info("切换定时报告状态: id={}", id);
        ReportScheduleVO vo = schedulerService.toggleSchedule(id);
        return Result.success(vo);
    }

    /**
     * 删除定时报告配置。
     *
     * @param id 调度ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除定时报告", description = "删除定时报告配置并取消调度注册")
    public Result<Void> deleteSchedule(
            @Parameter(description = "调度ID", required = true) @PathVariable("id") Long id) {
        log.info("删除定时报告: id={}", id);
        schedulerService.deleteSchedule(id);
        return Result.success();
    }

    /**
     * 查询定时报告执行历史。
     *
     * @param id 调度ID
     * @return 执行历史列表
     */
    @GetMapping("/{id}/history")
    @Operation(summary = "查询执行历史", description = "查询指定定时报告的最近执行历史")
    public Result<List<ReportScheduleVO>> getHistory(
            @Parameter(description = "调度ID", required = true) @PathVariable("id") Long id) {
        log.info("查询定时报告执行历史: id={}", id);
        List<ReportScheduleVO> history = schedulerService.getHistory(id);
        return Result.success(history);
    }
}
