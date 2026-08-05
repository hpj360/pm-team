package com.redteam.ai.controller;

import com.redteam.ai.dto.GenerateRequest;
import com.redteam.ai.service.ThreatSummaryService;
import com.redteam.common.entity.ThreatSummaryEntity;
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
 * AI 威胁摘要控制器
 *
 * <p>提供威胁摘要的查询与手动触发生成接口。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/threat-summary")
@Tag(name = "AI 威胁摘要", description = "基于 LLM 的文件威胁摘要生成与查询")
public class ThreatSummaryController {

    @Autowired
    private ThreatSummaryService threatSummaryService;

    /**
     * 获取文件威胁摘要
     *
     * @param fileId 文件ID
     * @return 威胁摘要
     */
    @GetMapping("/{fileId}")
    @Operation(summary = "获取文件威胁摘要", description = "按文件ID查询最新一条威胁摘要记录")
    public Result<ThreatSummaryEntity> getSummary(
            @Parameter(description = "文件ID", required = true) @PathVariable Long fileId) {
        ThreatSummaryEntity entity = threatSummaryService.getByFileId(fileId);
        return Result.success(entity);
    }

    /**
     * 手动触发摘要生成
     *
     * @param fileId  文件ID
     * @param request 生成请求（含文本内容、文件名、文件类型、NER 实体、标签）
     * @return 生成的威胁摘要
     */
    @PostMapping("/{fileId}/generate")
    @Operation(summary = "手动触发摘要生成", description = "基于文件内容、NER 实体、标签等信息生成威胁摘要")
    public Result<ThreatSummaryEntity> generateSummary(
            @Parameter(description = "文件ID", required = true) @PathVariable Long fileId,
            @RequestBody GenerateRequest request) {
        log.info("手动触发威胁摘要生成，fileId={}", fileId);
        ThreatSummaryEntity entity = threatSummaryService.generateSummary(
                fileId,
                request.getTextContent(),
                request.getFileName(),
                request.getFileType(),
                request.getNerEntities(),
                request.getTags());
        return Result.success(entity);
    }
}
