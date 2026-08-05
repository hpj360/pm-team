package com.redteam.analyze.controller;

import com.redteam.analyze.dto.SandboxReportVO;
import com.redteam.analyze.service.FileAnalyzeService;
import com.redteam.analyze.service.SandboxService;
import com.redteam.common.api.dto.AnalyzeResultDTO;
import com.redteam.common.api.dto.FileAnalyzeDTO;
import com.redteam.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件分析控制器
 *
 * <p>提供文件内容分析、敏感信息提取、向量嵌入、沙箱分析等接口。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/analyze")
@RequiredArgsConstructor
@Tag(name = "文件分析接口", description = "文件内容分析、敏感信息提取、沙箱分析等接口")
public class FileAnalyzeController {

    private final FileAnalyzeService fileAnalyzeService;

    private final SandboxService sandboxService;

    /**
     * 分析文件（同步）
     *
     * @param analyzeDTO 分析请求
     * @return 分析结果
     */
    @PostMapping("/file")
    @Operation(summary = "分析文件", description = "对文件进行内容分析（同步）")
    public Result<AnalyzeResultDTO> analyze(@Valid @RequestBody FileAnalyzeDTO analyzeDTO) {
        log.info("分析文件: fileId={}", analyzeDTO.getFileId());
        AnalyzeResultDTO result = fileAnalyzeService.analyze(analyzeDTO);
        return Result.success(result);
    }

    /**
     * 提交分析任务（异步）
     *
     * @param analyzeDTO 分析请求
     * @return 任务ID
     */
    @PostMapping("/submit")
    @Operation(summary = "提交分析任务", description = "异步提交文件分析任务，返回任务ID")
    public Result<Long> submit(@Valid @RequestBody FileAnalyzeDTO analyzeDTO) {
        log.info("提交分析任务: fileId={}", analyzeDTO.getFileId());
        Long taskId = fileAnalyzeService.analyzeAsync(analyzeDTO);
        return Result.success(taskId);
    }

    /**
     * 异步分析文件（兼容旧接口）
     *
     * @param analyzeDTO 分析请求
     * @return 任务ID
     */
    @PostMapping("/async")
    @Operation(summary = "异步分析文件", description = "异步对文件进行内容分析")
    public Result<Long> analyzeAsync(@Valid @RequestBody FileAnalyzeDTO analyzeDTO) {
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
     * 实体识别
     *
     * @param text 文本内容
     * @return 实体列表
     */
    @PostMapping("/entities")
    @Operation(summary = "实体识别", description = "从文本中识别安全实体（IP/域名/URL/邮箱/哈希/CVE）")
    public Result<List<AnalyzeResultDTO.EntityInfo>> recognizeEntities(
            @Parameter(description = "文本内容") @RequestParam("text") String text) {

        log.info("实体识别");
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities(text);
        return Result.success(result);
    }

    /**
     * 情感分析
     *
     * @param text 文本内容
     * @return 情感分析结果
     */
    @PostMapping("/sentiment")
    @Operation(summary = "情感分析", description = "对文本进行情感分析")
    public Result<AnalyzeResultDTO.SentimentInfo> analyzeSentiment(
            @Parameter(description = "文本内容") @RequestParam("text") String text) {

        log.info("情感分析");
        AnalyzeResultDTO.SentimentInfo result = fileAnalyzeService.analyzeSentiment(text);
        return Result.success(result);
    }

    /**
     * 生成文本摘要
     *
     * @param text   文本内容
     * @param length 摘要长度
     * @return 摘要
     */
    @PostMapping("/summary")
    @Operation(summary = "生成文本摘要", description = "对文本生成抽取式摘要")
    public Result<String> generateSummary(
            @Parameter(description = "文本内容") @RequestParam("text") String text,
            @Parameter(description = "摘要长度") @RequestParam(value = "length", defaultValue = "200") Integer length) {

        log.info("生成摘要: length={}", length);
        String summary = fileAnalyzeService.generateSummary(text, length);
        return Result.success(summary);
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

    // ==================== 沙箱分析接口 ====================

    /**
     * 提交沙箱分析
     *
     * @param fileId 文件ID
     * @return 沙箱任务ID
     */
    @PostMapping("/sandbox/submit")
    @Operation(summary = "提交沙箱分析", description = "提交文件到沙箱进行分析，沙箱不可用时返回降级结果")
    public Result<String> submitSandbox(
            @Parameter(description = "文件ID") @RequestParam("fileId")
            @NotNull(message = "文件ID不能为空") Long fileId) {

        log.info("提交沙箱分析: fileId={}", fileId);
        String taskId = sandboxService.submitToSandbox(fileId);
        return Result.success(taskId);
    }

    /**
     * 获取沙箱报告
     *
     * @param taskId 沙箱任务ID
     * @return 沙箱报告
     */
    @GetMapping("/sandbox/report/{taskId}")
    @Operation(summary = "获取沙箱报告", description = "根据沙箱任务ID获取分析报告")
    public Result<SandboxReportVO> getSandboxReport(
            @Parameter(description = "沙箱任务ID") @PathVariable("taskId") String taskId) {

        log.info("获取沙箱报告: taskId={}", taskId);
        SandboxReportVO report = sandboxService.getSandboxReport(taskId);
        return Result.success(report);
    }

    /**
     * 获取沙箱状态
     *
     * @param taskId 沙箱任务ID
     * @return 沙箱状态
     */
    @GetMapping("/sandbox/status/{taskId}")
    @Operation(summary = "获取沙箱状态", description = "根据沙箱任务ID获取分析状态")
    public Result<String> getSandboxStatus(
            @Parameter(description = "沙箱任务ID") @PathVariable("taskId") String taskId) {

        log.info("获取沙箱状态: taskId={}", taskId);
        String status = sandboxService.getSandboxStatus(taskId);
        return Result.success(status);
    }
}
