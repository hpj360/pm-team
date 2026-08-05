package com.redteam.report.controller;

import com.redteam.common.result.PageResult;
import com.redteam.common.result.Result;
import com.redteam.report.dto.ReportGenerateDTO;
import com.redteam.report.dto.ReportQueryDTO;
import com.redteam.report.dto.ReportShareDTO;
import com.redteam.report.dto.ReportStatsDTO;
import com.redteam.report.dto.ReportTemplateVO;
import com.redteam.report.dto.ReportVO;
import com.redteam.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 报告管理 REST 接口
 *
 * <p>提供报告生成、查询、删除、下载、共享、版本管理、重试、统计及模板列表等接口，
 * 统一前缀 {@code /api/v1/reports}。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "报告管理接口", description = "报告生成、查询、下载、共享、版本、统计、模板管理等接口")
public class ReportController {

    private final ReportService reportService;

    /**
     * 生成报告（异步）
     *
     * <p>立即返回 {@code reportId}（状态为 PENDING），实际生成在线程池中异步执行。</p>
     *
     * @param dto 报告生成请求
     * @return 报告基础信息
     */
    @PostMapping
    @Operation(summary = "生成报告", description = "异步生成报告，返回报告ID及PENDING状态")
    public Result<ReportVO> generateReport(@Valid @RequestBody ReportGenerateDTO dto) {
        log.info("生成报告请求: reportName={}, type={}", dto.getReportName(), dto.getReportType());
        ReportVO vo = reportService.generateReport(dto);
        return Result.success(vo);
    }

    /**
     * 获取报告详情
     *
     * @param reportId 报告ID
     * @return 报告信息
     */
    @GetMapping("/{reportId}")
    @Operation(summary = "获取报告详情", description = "根据报告ID获取报告详细信息")
    public Result<ReportVO> getReport(
            @Parameter(description = "报告ID", required = true) @PathVariable("reportId") String reportId) {
        log.info("获取报告详情: reportId={}", reportId);
        ReportVO vo = reportService.getReport(reportId);
        return Result.success(vo);
    }

    /**
     * 分页查询报告列表
     *
     * @param current   当前页码
     * @param size      每页大小
     * @param reportType 报告类型
     * @param status    报告状态
     * @param taskId    关联任务ID
     * @param targetId  关联目标ID
     * @param keyword   名称关键词
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询报告", description = "支持按类型、状态、任务ID、目标ID、关键词多条件查询")
    public Result<PageResult<ReportVO>> listReports(
            @Parameter(description = "当前页码") @RequestParam(value = "current", defaultValue = "1") Long current,
            @Parameter(description = "每页大小") @RequestParam(value = "size", defaultValue = "10") Long size,
            @Parameter(description = "报告类型") @RequestParam(value = "reportType", required = false) String reportType,
            @Parameter(description = "报告状态") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "关联任务ID") @RequestParam(value = "taskId", required = false) String taskId,
            @Parameter(description = "关联目标ID") @RequestParam(value = "targetId", required = false) String targetId,
            @Parameter(description = "名称关键词") @RequestParam(value = "keyword", required = false) String keyword) {

        ReportQueryDTO query = new ReportQueryDTO();
        query.setCurrent(current);
        query.setSize(size);
        query.setReportType(reportType);
        query.setStatus(status);
        query.setTaskId(taskId);
        query.setTargetId(targetId);
        query.setKeyword(keyword);

        log.info("分页查询报告: current={}, size={}, type={}", current, size, reportType);
        PageResult<ReportVO> page = reportService.listReports(query);
        return Result.success(page);
    }

    /**
     * 删除报告
     *
     * @param reportId 报告ID
     * @return 操作结果
     */
    @DeleteMapping("/{reportId}")
    @Operation(summary = "删除报告", description = "删除报告记录及对应的物理文件")
    public Result<Void> deleteReport(
            @Parameter(description = "报告ID", required = true) @PathVariable("reportId") String reportId) {
        log.info("删除报告: reportId={}", reportId);
        reportService.deleteReport(reportId);
        return Result.success();
    }

    /**
     * 下载报告文件
     *
     * @param reportId 报告ID
     * @return 文件流响应
     */
    @GetMapping("/{reportId}/download")
    @Operation(summary = "下载报告", description = "下载已生成完成的报告文件（PDF/Word/HTML）")
    public ResponseEntity<Resource> downloadReport(
            @Parameter(description = "报告ID", required = true) @PathVariable("reportId") String reportId) {
        log.info("下载报告: reportId={}", reportId);
        Resource resource = reportService.downloadReport(reportId);
        String fileName = URLEncoder.encode(reportId, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    /**
     * 获取报告模板列表
     *
     * @return 模板列表
     */
    @GetMapping("/templates")
    @Operation(summary = "获取报告模板列表", description = "获取所有可用的报告模板")
    public Result<List<ReportTemplateVO>> listTemplates() {
        log.info("获取报告模板列表");
        List<ReportTemplateVO> templates = reportService.listTemplates();
        return Result.success(templates);
    }

    /**
     * 获取报告统计信息
     *
     * @return 统计结果
     */
    @GetMapping("/stats")
    @Operation(summary = "获取报告统计", description = "按状态、类型、格式维度统计报告数量")
    public Result<ReportStatsDTO> getReportStats() {
        log.info("获取报告统计");
        ReportStatsDTO stats = reportService.getReportStats();
        return Result.success(stats);
    }

    /**
     * 共享报告
     *
     * @param reportId 报告ID
     * @param dto      共享请求
     * @return 更新后的报告信息
     */
    @PostMapping("/{reportId}/share")
    @Operation(summary = "共享报告", description = "将报告共享给指定用户列表")
    public Result<ReportVO> shareReport(
            @Parameter(description = "报告ID", required = true) @PathVariable("reportId") String reportId,
            @Valid @RequestBody ReportShareDTO dto) {
        log.info("共享报告: reportId={}, userCount={}", reportId, dto.getUserIds().size());
        ReportVO vo = reportService.shareReport(reportId, dto);
        return Result.success(vo);
    }

    /**
     * 取消报告共享
     *
     * @param reportId 报告ID
     * @return 更新后的报告信息
     */
    @DeleteMapping("/{reportId}/share")
    @Operation(summary = "取消报告共享", description = "取消指定报告的共享状态")
    public Result<ReportVO> unshareReport(
            @Parameter(description = "报告ID", required = true) @PathVariable("reportId") String reportId) {
        log.info("取消报告共享: reportId={}", reportId);
        ReportVO vo = reportService.unshareReport(reportId);
        return Result.success(vo);
    }

    /**
     * 重新生成报告（版本号递增）
     *
     * @param reportId 报告ID
     * @return 报告基础信息（状态为 PENDING）
     */
    @PostMapping("/{reportId}/regenerate")
    @Operation(summary = "重新生成报告", description = "对已存在报告重新生成，版本号自增")
    public Result<ReportVO> regenerateReport(
            @Parameter(description = "报告ID", required = true) @PathVariable("reportId") String reportId) {
        log.info("重新生成报告: reportId={}", reportId);
        ReportVO vo = reportService.regenerateReport(reportId);
        return Result.success(vo);
    }

    /**
     * 重试生成失败的报告
     *
     * @param reportId 报告ID
     * @return 报告基础信息（状态为 PENDING）
     */
    @PostMapping("/{reportId}/retry")
    @Operation(summary = "重试失败报告", description = "仅当报告状态为 FAILED 时允许重试")
    public Result<ReportVO> retryFailed(
            @Parameter(description = "报告ID", required = true) @PathVariable("reportId") String reportId) {
        log.info("重试失败报告: reportId={}", reportId);
        ReportVO vo = reportService.retryFailed(reportId);
        return Result.success(vo);
    }
}
