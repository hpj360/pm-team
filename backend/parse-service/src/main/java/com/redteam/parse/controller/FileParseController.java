package com.redteam.parse.controller;

import com.redteam.common.result.PageResult;
import com.redteam.common.result.Result;
import com.redteam.parse.dto.NerEntityVO;
import com.redteam.parse.dto.ParseQueryDTO;
import com.redteam.parse.dto.ParseResultDTO;
import com.redteam.parse.dto.YaraMatchVO;
import com.redteam.parse.dto.YaraRuleDTO;
import com.redteam.parse.entity.YaraRuleEntity;
import com.redteam.parse.service.FileParseService;
import com.redteam.parse.service.NerService;
import com.redteam.parse.service.YaraScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文件解析控制器
 *
 * <p>提供文件解析、YARA 规则管理与扫描、NER 实体识别等接口。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/parse")
@RequiredArgsConstructor
@Tag(name = "文件解析接口", description = "文件内容解析、YARA 扫描、NER 实体识别等接口")
public class FileParseController {

    private final FileParseService fileParseService;
    private final YaraScanService yaraScanService;
    private final NerService nerService;

    // ==================== 文件解析 ====================

    /**
     * 解析文件
     *
     * @param storagePath 存储路径
     * @param filename    文件名
     * @param fileType    文件类型
     * @return 解析结果
     */
    @PostMapping("/file")
    @Operation(summary = "解析文件", description = "解析指定文件并提取文本内容、YARA 匹配与 NER 实体")
    public Result<ParseResultDTO> parseFile(
            @Parameter(description = "存储路径") @RequestParam("storagePath") String storagePath,
            @Parameter(description = "文件名") @RequestParam("filename") String filename,
            @Parameter(description = "文件类型") @RequestParam("fileType") String fileType) {

        log.info("解析文件: {}", filename);
        ParseResultDTO result = fileParseService.parseFile(storagePath, filename, fileType);
        return Result.success(result);
    }

    /**
     * 异步解析文件
     *
     * @param fileId 文件ID
     * @return 是否成功
     */
    @PostMapping("/async/{fileId}")
    @Operation(summary = "异步解析文件", description = "异步解析指定文件，结果通过 Kafka 事件通知")
    public Result<Void> parseFileAsync(
            @Parameter(description = "文件ID") @PathVariable("fileId") Long fileId) {

        log.info("异步解析文件: {}", fileId);
        fileParseService.parseFileAsync(fileId);
        return Result.success();
    }

    /**
     * 获取解析结果
     *
     * @param fileId 文件ID
     * @return 解析结果
     */
    @GetMapping("/result/{fileId}")
    @Operation(summary = "获取解析结果", description = "根据文件ID获取解析结果（含 YARA/NER 增强信息）")
    public Result<ParseResultDTO> getParseResult(
            @Parameter(description = "文件ID") @PathVariable("fileId") Long fileId) {

        log.info("获取解析结果: fileId={}", fileId);
        ParseResultDTO result = fileParseService.getParseResult(fileId);
        return Result.success(result);
    }

    /**
     * 分页查询解析结果
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping("/results")
    @Operation(summary = "分页查询解析结果", description = "支持按 fileId、fileName、parseStatus 过滤")
    public Result<PageResult<ParseResultDTO>> listParseResults(ParseQueryDTO query) {

        log.info("分页查询解析结果: query={}", query);
        PageResult<ParseResultDTO> result = fileParseService.listParseResults(query);
        return Result.success(result);
    }

    // ==================== YARA 规则管理 ====================

    /**
     * 创建 YARA 规则
     *
     * @param dto 规则 DTO
     * @return 创建后的规则
     */
    @PostMapping("/yara/rule")
    @Operation(summary = "创建 YARA 规则", description = "创建 YARA 扫描规则")
    public Result<YaraRuleEntity> createYaraRule(@Valid @RequestBody YaraRuleDTO dto) {

        log.info("创建 YARA 规则: ruleName={}", dto.getRuleName());
        YaraRuleEntity entity = yaraScanService.createRule(dto);
        return Result.success(entity);
    }

    /**
     * 更新 YARA 规则
     *
     * @param id  规则ID
     * @param dto 规则 DTO
     * @return 更新后的规则
     */
    @PutMapping("/yara/rule/{id}")
    @Operation(summary = "更新 YARA 规则", description = "更新指定 YARA 扫描规则")
    public Result<YaraRuleEntity> updateYaraRule(
            @Parameter(description = "规则ID") @PathVariable("id") Long id,
            @Valid @RequestBody YaraRuleDTO dto) {

        log.info("更新 YARA 规则: ruleId={}", id);
        YaraRuleEntity entity = yaraScanService.updateRule(id, dto);
        return Result.success(entity);
    }

    /**
     * 删除 YARA 规则
     *
     * @param id 规则ID
     * @return 操作结果
     */
    @DeleteMapping("/yara/rule/{id}")
    @Operation(summary = "删除 YARA 规则", description = "逻辑删除指定 YARA 扫描规则")
    public Result<Void> deleteYaraRule(
            @Parameter(description = "规则ID") @PathVariable("id") Long id) {

        log.info("删除 YARA 规则: ruleId={}", id);
        yaraScanService.deleteRule(id);
        return Result.success();
    }

    /**
     * 查询 YARA 规则列表
     *
     * @return 规则列表
     */
    @GetMapping("/yara/rules")
    @Operation(summary = "查询 YARA 规则列表", description = "查询所有启用的 YARA 规则")
    public Result<List<YaraRuleEntity>> listYaraRules() {

        log.info("查询 YARA 规则列表");
        List<YaraRuleEntity> rules = yaraScanService.listEnabledRules();
        return Result.success(rules);
    }

    /**
     * 手动扫描指定文件
     *
     * @param fileId 文件ID
     * @return YARA 匹配结果
     */
    @PostMapping("/yara/scan/{fileId}")
    @Operation(summary = "手动扫描指定文件", description = "对指定文件执行 YARA 规则扫描")
    public Result<List<YaraMatchVO>> scanFile(
            @Parameter(description = "文件ID") @PathVariable("fileId") Long fileId,
            @Parameter(description = "文件路径（可选，默认从 MinIO 解析）")
            @RequestParam(value = "filePath", required = false) String filePath) {

        log.info("手动 YARA 扫描: fileId={}, filePath={}", fileId, filePath);
        // filePath 为空时使用空文本兜底，实际场景应从持久化结果取文本
        List<YaraMatchVO> matches = yaraScanService.scanFile(fileId, filePath);
        return Result.success(matches);
    }

    // ==================== NER 实体识别 ====================

    /**
     * 获取 NER 实体识别结果
     *
     * @param fileId 文件ID
     * @return NER 实体列表
     */
    @GetMapping("/ner/{fileId}")
    @Operation(summary = "获取 NER 实体识别结果", description = "根据文件ID获取 NER 实体识别结果")
    public Result<List<NerEntityVO>> getNerEntities(
            @Parameter(description = "文件ID") @PathVariable("fileId") Long fileId) {

        log.info("获取 NER 实体: fileId={}", fileId);
        ParseResultDTO parseResult = fileParseService.getParseResult(fileId);
        List<NerEntityVO> entities = parseResult.getNerEntities();
        if (entities == null) {
            // 兜底：从文本重新识别
            entities = nerService.extractEntities(parseResult.getTextContent());
        }
        return Result.success(entities);
    }

    /**
     * 获取 NER 模型状态（健康检查）
     *
     * @return 模型状态信息，包含 status（READY/FALLBACK/FAILED）、modelPath、lastError
     */
    @GetMapping("/ner/model-status")
    @Operation(summary = "获取 NER 模型状态", description = "返回 security-BERT 模型的加载状态、路径与最近错误信息")
    public Result<Map<String, Object>> getNerModelStatus() {
        return Result.success(nerService.getModelStatus());
    }
}
