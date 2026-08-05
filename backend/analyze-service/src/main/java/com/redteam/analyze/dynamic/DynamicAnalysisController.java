package com.redteam.analyze.dynamic;

import com.redteam.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 动态分析控制器（V5.2）
 *
 * <p>提供 Cuckoo 沙箱动态分析任务的提交、状态查询、报告获取接口。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/analyze/dynamic")
@RequiredArgsConstructor
@Tag(name = "动态分析接口", description = "Cuckoo 沙箱动态分析任务提交、状态查询、行为指标报告")
public class DynamicAnalysisController {

    private final DynamicAnalysisService dynamicAnalysisService;

    /**
     * 提交动态分析任务
     *
     * @param fileId 文件ID
     * @return 平台侧动态分析任务ID
     */
    @PostMapping("/submit")
    @Operation(summary = "提交动态分析任务", description = "提交文件到 Cuckoo 沙箱进行动态分析，沙箱不可用时返回降级任务")
    public Result<String> submit(
            @Parameter(description = "文件ID") @RequestParam("fileId")
            @NotNull(message = "文件ID不能为空") Long fileId) {
        log.info("提交动态分析任务: fileId={}", fileId);
        String taskId = dynamicAnalysisService.submitDynamicAnalysis(fileId);
        return Result.success(taskId);
    }

    /**
     * 获取动态分析任务状态
     *
     * @param taskId 任务ID
     * @return 任务对象
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取动态分析任务", description = "根据任务ID获取动态分析任务详情与状态")
    public Result<DynamicAnalysisTask> getTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId) {
        log.info("获取动态分析任务: taskId={}", taskId);
        return Result.success(dynamicAnalysisService.getTask(taskId));
    }

    /**
     * 获取动态分析报告
     *
     * @param taskId 任务ID
     * @return 报告 VO
     */
    @GetMapping("/{taskId}/report")
    @Operation(summary = "获取动态分析报告", description = "根据任务ID获取动态分析报告（含行为指标、ATT&CK 映射、IOC、STIX 对象）")
    public Result<DynamicReportVO> getReport(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId) {
        log.info("获取动态分析报告: taskId={}", taskId);
        return Result.success(dynamicAnalysisService.getReport(taskId));
    }

    /**
     * 触发任务轮询（手动触发状态推进）
     *
     * @param taskId 任务ID
     * @return 当前状态
     */
    @PostMapping("/{taskId}/poll")
    @Operation(summary = "触发任务轮询", description = "手动触发动态分析任务状态轮询，推进状态机")
    public Result<String> pollTask(
            @Parameter(description = "任务ID") @PathVariable("taskId") String taskId) {
        log.info("触发动态分析任务轮询: taskId={}", taskId);
        return Result.success(dynamicAnalysisService.pollTask(taskId));
    }

    /**
     * 列出全部动态分析任务
     *
     * @return 任务列表
     */
    @GetMapping
    @Operation(summary = "列出动态分析任务", description = "列出全部动态分析任务")
    public Result<List<DynamicAnalysisTask>> listTasks() {
        return Result.success(dynamicAnalysisService.listTasks());
    }
}
