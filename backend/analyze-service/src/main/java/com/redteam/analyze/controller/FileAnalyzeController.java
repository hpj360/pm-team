package com.redteam.analyze.controller;

import com.redteam.common.api.dto.AnalyzeResultDTO;
import com.redteam.common.api.dto.FileAnalyzeDTO;
import com.redteam.common.result.Result;
import com.redteam.analyze.service.FileAnalyzeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件分析控制器
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/analyze")
@RequiredArgsConstructor
@Tag(name = "文件分析接口", description = "文件内容分析、敏感信息提取等接口")
public class FileAnalyzeController {

    private final FileAnalyzeService fileAnalyzeService;

    /**
     * 分析文件
     *
     * @param analyzeDTO 分析请求
     * @return 分析结果
     */
    @PostMapping("/file")
    @Operation(summary = "分析文件", description = "对文件进行内容分析")
    public Result<AnalyzeResultDTO> analyze(@RequestBody FileAnalyzeDTO analyzeDTO) {
        log.info("分析文件: fileId={}", analyzeDTO.getFileId());
        AnalyzeResultDTO result = fileAnalyzeService.analyze(analyzeDTO);
        return Result.success(result);
    }

    /**
     * 异步分析文件
     *
     * @param analyzeDTO 分析请求
     * @return 任务ID
     */
    @PostMapping("/async")
    @Operation(summary = "异步分析文件", description = "异步对文件进行内容分析")
    public Result<Long> analyzeAsync(@RequestBody FileAnalyzeDTO analyzeDTO) {
        log.info("异步分析文件: fileId={}", analyzeDTO.getFileId());
        Long taskId = fileAnalyzeService.analyzeAsync(analyzeDTO);
        return Result.success(taskId);
    }

    /**
     * 获取分析结果
     *
     * @param taskId 任务ID
     * @return 分析结果
     */
    @GetMapping("/result/{taskId}")
    @Operation(summary = "获取分析结果", description = "根据任务ID获取分析结果")
    public Result<AnalyzeResultDTO> getAnalyzeResult(
            @Parameter(description = "任务ID") @PathVariable("taskId") Long taskId) {

        log.info("获取分析结果: taskId={}", taskId);
        AnalyzeResultDTO result = fileAnalyzeService.getAnalyzeResult(taskId);
        return Result.success(result);
    }

    /**
     * 提取敏感信息
     *
     * @param text 文本内容
     * @return 敏感信息列表
     */
    @PostMapping("/sensitive")
    @Operation(summary = "提取敏感信息", description = "从文本中提取敏感信息")
    public Result<List<AnalyzeResultDTO.SensitiveInfo>> extractSensitiveInfo(
            @Parameter(description = "文本内容") @RequestParam("text") String text) {

        log.info("提取敏感信息");
        List<AnalyzeResultDTO.SensitiveInfo> result = fileAnalyzeService.extractSensitiveInfo(text);
        return Result.success(result);
    }

    /**
     * 提取关键词
     *
     * @param text 文本内容
     * @param topN 返回数量
     * @return 关键词列表
     */
    @PostMapping("/keywords")
    @Operation(summary = "提取关键词", description = "从文本中提取关键词")
    public Result<List<AnalyzeResultDTO.KeywordInfo>> extractKeywords(
            @Parameter(description = "文本内容") @RequestParam("text") String text,
            @Parameter(description = "返回数量") @RequestParam(value = "topN", defaultValue = "10") Integer topN) {

        log.info("提取关键词: topN={}", topN);
        List<AnalyzeResultDTO.KeywordInfo> result = fileAnalyzeService.extractKeywords(text, topN);
        return Result.success(result);
    }

    /**
     * 生成向量嵌入
     *
     * @param text 文本内容
     * @return 向量ID
     */
    @PostMapping("/embedding")
    @Operation(summary = "生成向量嵌入", description = "为文本生成向量嵌入")
    public Result<String> generateEmbedding(
            @Parameter(description = "文本内容") @RequestParam("text") String text) {

        log.info("生成向量嵌入");
        String embeddingId = fileAnalyzeService.generateEmbedding(text);
        return Result.success(embeddingId);
    }
}
