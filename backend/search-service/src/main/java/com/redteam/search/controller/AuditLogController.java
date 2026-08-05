package com.redteam.search.controller;

import com.redteam.common.entity.AuditLogEntity;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.Result;
import com.redteam.common.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志控制器
 *
 * <p>提供审计日志的查询、CSV 导出与统计接口，
 * 数据来源为 {@link com.redteam.common.annotation.AuditLog} 注解自动采集
 * 与 {@link AuditLogService#record} 手动记录。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "审计日志", description = "审计日志查询、导出与统计接口")
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 查询审计日志（筛选 + 分页）
     *
     * @param userId       用户ID（可空）
     * @param action       操作类型（可空）
     * @param resourceType 资源类型（可空）
     * @param startTime    开始时间（可空）
     * @param endTime      结束时间（可空）
     * @param page         页码（默认 1）
     * @param size         每页大小（默认 20）
     * @return 分页审计日志
     */
    @GetMapping("/logs")
    @Operation(summary = "查询审计日志", description = "按用户/操作类型/资源类型/时间范围筛选并分页查询")
    public Result<PageResult<AuditLogEntity>> query(
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String action,
            @Parameter(description = "资源类型") @RequestParam(required = false) String resourceType,
            @Parameter(description = "开始时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        log.info("查询审计日志: userId={}, action={}, resourceType={}, page={}, size={}",
                userId, action, resourceType, page, size);
        PageResult<AuditLogEntity> result = auditLogService.query(
                userId, action, resourceType, startTime, endTime, page, size);
        return Result.success(result);
    }

    /**
     * 导出审计日志 CSV
     *
     * @param userId       用户ID（可空）
     * @param action       操作类型（可空）
     * @param resourceType 资源类型（可空）
     * @param startTime    开始时间（可空）
     * @param endTime      结束时间（可空）
     * @return CSV 文件响应
     */
    @GetMapping("/export")
    @Operation(summary = "导出审计日志CSV", description = "按筛选条件导出全部匹配记录为 CSV 字符串")
    public ResponseEntity<String> export(
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String action,
            @Parameter(description = "资源类型") @RequestParam(required = false) String resourceType,
            @Parameter(description = "开始时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("导出审计日志CSV: userId={}, action={}, resourceType={}", userId, action, resourceType);
        String csv = auditLogService.exportCsv(userId, action, resourceType, startTime, endTime);

        // 文件名带时间戳，避免重复
        String fileName = "audit_log_" + System.currentTimeMillis() + ".csv";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        // 带 BOM 头让 Excel 正确识别 UTF-8
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName);

        // CSV 内容前置 BOM，保证 Excel 打开中文不乱码
        String csvWithBom = "\uFEFF" + csv;
        return ResponseEntity.ok().headers(headers).body(csvWithBom);
    }

    /**
     * 审计统计（按操作类型分组）
     *
     * @param startTime 开始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 操作类型 -> 数量 映射
     */
    @GetMapping("/stats")
    @Operation(summary = "审计统计", description = "按操作类型分组统计数量")
    public Result<Map<String, Long>> stats(
            @Parameter(description = "开始时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("审计统计: startTime={}, endTime={}", startTime, endTime);
        Map<String, Long> stats = auditLogService.stats(startTime, endTime);
        return Result.success(stats);
    }
}
