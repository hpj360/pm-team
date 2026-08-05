package com.redteam.ai.controller;

import com.redteam.ai.dto.GenerateDraftRequest;
import com.redteam.ai.service.ReportDraftService;
import com.redteam.ai.vo.ReportDraft;
import com.redteam.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 报告草稿控制器
 *
 * <p>提供基于 LLM 的报告结论草稿生成与查询接口。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/report-draft")
@Tag(name = "AI 报告草稿", description = "基于 LLM 的报告结论草稿生成与查询")
public class ReportDraftController {

    @Autowired
    private ReportDraftService reportDraftService;

    /**
     * 生成报告草稿
     *
     * @param reportId 报告ID
     * @param request  生成请求（统计数据 / 文件列表 / 标签分布 JSON）
     * @return 草稿结果
     */
    @PostMapping("/{reportId}/generate")
    @Operation(summary = "生成报告草稿", description = "基于统计数据、文件列表、标签分布生成报告结论草稿")
    public Result<ReportDraft> generate(
            @Parameter(description = "报告ID", required = true) @PathVariable Long reportId,
            @RequestBody GenerateDraftRequest request) {
        log.info("生成报告草稿请求, reportId={}", reportId);
        ReportDraft draft = reportDraftService.generateDraft(
                reportId,
                request.getStatsJson(),
                request.getFileListJson(),
                request.getTagDistributionJson());
        return Result.success(draft);
    }

    /**
     * 获取报告草稿
     *
     * @param reportId 报告ID
     * @return 草稿结果
     */
    @GetMapping("/{reportId}")
    @Operation(summary = "获取报告草稿", description = "按报告ID查询已生成的报告草稿")
    public Result<ReportDraft> get(
            @Parameter(description = "报告ID", required = true) @PathVariable Long reportId) {
        ReportDraft draft = reportDraftService.getDraft(reportId);
        return Result.success(draft);
    }
}
