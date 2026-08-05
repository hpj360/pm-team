package com.redteam.analyze.hunting;

import com.redteam.analyze.hunting.entity.AttackTechniqueEntity;
import com.redteam.analyze.hunting.entity.HuntingHypothesisEntity;
import com.redteam.analyze.hunting.entity.HuntingRuleEntity;
import com.redteam.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 威胁狩猎控制器（V5.3）
 *
 * <p>提供狩猎假设、ATT&CK 矩阵、狩猎规则（Sigma/YARA）的接口。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/hunting")
@RequiredArgsConstructor
@Tag(name = "威胁狩猎接口", description = "狩猎假设、ATT&CK 矩阵、Sigma/YARA 规则管理")
public class HuntingController {

    private final ThreatHuntingService threatHuntingService;

    private final AttackMatrixService attackMatrixService;

    private final HuntingRuleService huntingRuleService;

    // ==================== 狩猎假设 ====================

    /**
     * 创建狩猎假设
     *
     * @param body 请求体（description / techniqueId / userId）
     * @return 假设ID
     */
    @PostMapping("/hypothesis")
    @Operation(summary = "创建狩猎假设", description = "创建威胁狩猎假设，关联 ATT&CK 技术")
    public Result<String> createHypothesis(@RequestBody CreateHypothesisRequest body) {
        log.info("创建狩猎假设: techniqueId={}", body.getTechniqueId());
        String id = threatHuntingService.createHypothesis(
                body.getDescription(), body.getTechniqueId(), body.getUserId());
        return Result.success(id);
    }

    /**
     * 获取假设列表
     *
     * @return 假设 VO 列表
     */
    @GetMapping("/hypothesis")
    @Operation(summary = "获取狩猎假设列表", description = "列出全部狩猎假设")
    public Result<List<HypothesisVO>> listHypotheses() {
        return Result.success(threatHuntingService.listHypotheses());
    }

    /**
     * 获取假设详情（含验证结果）
     *
     * @param id 假设ID
     * @return 假设 VO
     */
    @GetMapping("/hypothesis/{id}")
    @Operation(summary = "获取狩猎假设详情", description = "根据假设ID获取假设详情与验证结果")
    public Result<HypothesisVO> getHypothesis(
            @Parameter(description = "假设ID") @PathVariable("id") String id) {
        return Result.success(threatHuntingService.getHypothesis(id));
    }

    /**
     * 触发假设验证
     *
     * @param id 假设ID
     * @return 验证后的假设实体
     */
    @PostMapping("/hypothesis/{id}/validate")
    @Operation(summary = "触发狩猎假设验证", description = "对假设进行自动检索验证，产出命中清单与置信度")
    public Result<HuntingHypothesisEntity> validateHypothesis(
            @Parameter(description = "假设ID") @PathVariable("id") String id) {
        log.info("触发狩猎假设验证: id={}", id);
        return Result.success(threatHuntingService.validateHypothesis(id));
    }

    // ==================== ATT&CK 矩阵 ====================

    /**
     * 获取 ATT&CK 矩阵
     *
     * @return 矩阵数据（含战术列表 + 技术列表 + 统计）
     */
    @GetMapping("/attack-matrix")
    @Operation(summary = "获取 ATT&CK 矩阵", description = "获取 ATT&CK 战术与技术矩阵数据")
    public Result<Map<String, Object>> getAttackMatrix() {
        Map<String, Object> matrix = new java.util.LinkedHashMap<>();
        matrix.put("tactics", attackMatrixService.getAllTactics());
        matrix.put("techniques", attackMatrixService.getAllTechniques());
        matrix.put("tacticCount", attackMatrixService.tacticCount());
        matrix.put("techniqueCount", attackMatrixService.techniqueCount());
        return Result.success(matrix);
    }

    /**
     * 按战术查询技术
     *
     * @param tactic 战术 ID
     * @return 技术列表
     */
    @GetMapping("/attack-matrix/tactic/{tactic}")
    @Operation(summary = "按战术查询 ATT&CK 技术", description = "根据战术 ID 查询该战术下的所有技术")
    public Result<List<AttackTechniqueEntity>> getTechniquesByTactic(
            @Parameter(description = "战术 ID") @PathVariable("tactic") String tactic) {
        return Result.success(attackMatrixService.getTechniquesByTactic(tactic));
    }

    /**
     * 关键词搜索 ATT&CK 技术
     *
     * @param keyword 关键词
     * @return 技术列表
     */
    @GetMapping("/attack-matrix/search")
    @Operation(summary = "搜索 ATT&CK 技术", description = "根据关键词搜索技术（匹配 techniqueId / name / description）")
    public Result<List<AttackTechniqueEntity>> searchTechniques(
            @Parameter(description = "关键词") @RequestParam("keyword")
            @NotBlank(message = "关键词不能为空") String keyword) {
        return Result.success(attackMatrixService.searchTechniques(keyword));
    }

    // ==================== 狩猎规则 ====================

    /**
     * 获取规则列表
     *
     * @return 规则列表
     */
    @GetMapping("/rules")
    @Operation(summary = "获取狩猎规则列表", description = "列出全部 Sigma/YARA 狩猎规则")
    public Result<List<HuntingRuleEntity>> listRules() {
        return Result.success(huntingRuleService.listRules());
    }

    /**
     * 获取规则详情
     *
     * @param id 规则ID
     * @return 规则实体
     */
    @GetMapping("/rules/{id}")
    @Operation(summary = "获取狩猎规则详情", description = "根据规则ID获取规则详情")
    public Result<HuntingRuleEntity> getRule(
            @Parameter(description = "规则ID") @PathVariable("id") String id) {
        return Result.success(huntingRuleService.getRule(id));
    }

    /**
     * 导入 Sigma 规则
     *
     * @param body 请求体（content）
     * @return 规则ID
     */
    @PostMapping("/rules/sigma/import")
    @Operation(summary = "导入 Sigma 规则", description = "解析 Sigma YAML 并入库，自动提取 title/description/tags/attack 技术")
    public Result<String> importSigmaRule(@RequestBody ImportRuleRequest body) {
        log.info("导入 Sigma 规则");
        String id = huntingRuleService.importSigmaRule(body.getContent());
        return Result.success(id);
    }

    /**
     * 导入 YARA 规则
     *
     * @param body 请求体（content）
     * @return 规则ID
     */
    @PostMapping("/rules/yara/import")
    @Operation(summary = "导入 YARA 规则", description = "导入 YARA 规则，支持版本管理")
    public Result<String> importYaraRule(@RequestBody ImportRuleRequest body) {
        log.info("导入 YARA 规则");
        String id = huntingRuleService.importYaraRule(body.getContent());
        return Result.success(id);
    }

    /**
     * 测试规则命中
     *
     * @param id     规则ID
     * @param fileId 文件ID
     * @return 测试结果
     */
    @PostMapping("/rules/{id}/test")
    @Operation(summary = "测试规则命中", description = "对指定文件测试规则命中情况")
    public Result<Map<String, Object>> testRule(
            @Parameter(description = "规则ID") @PathVariable("id") String id,
            @Parameter(description = "文件ID") @RequestParam("fileId")
            @NotBlank(message = "文件ID不能为空") String fileId) {
        log.info("测试规则命中: ruleId={}, fileId={}", id, fileId);
        return Result.success(huntingRuleService.testRule(id, fileId));
    }

    /**
     * 获取规则统计
     *
     * @param id 规则ID
     * @return 统计信息
     */
    @GetMapping("/rules/{id}/stats")
    @Operation(summary = "获取规则命中统计", description = "获取规则命中次数、测试次数、版本等统计信息")
    public Result<Map<String, Object>> getRuleStats(
            @Parameter(description = "规则ID") @PathVariable("id") String id) {
        return Result.success(huntingRuleService.getRuleStats(id));
    }

    /**
     * 按 ATT&CK 技术反向查询规则
     *
     * @param techniqueId 技术 ID
     * @return 关联规则列表
     */
    @GetMapping("/rules/by-technique/{techniqueId}")
    @Operation(summary = "按技术查询关联规则", description = "根据 ATT&CK 技术 ID 反向查询关联的狩猎规则")
    public Result<List<HuntingRuleEntity>> findRulesByTechnique(
            @Parameter(description = "技术 ID") @PathVariable("techniqueId") String techniqueId) {
        return Result.success(huntingRuleService.findRulesByTechnique(techniqueId));
    }

    // ==================== 请求体 DTO ====================

    /**
     * 创建假设请求体
     */
    @lombok.Data
    public static class CreateHypothesisRequest {
        /**
         * 假设描述
         */
        private String description;

        /**
         * ATT&CK 技术 ID
         */
        private String techniqueId;

        /**
         * 创建人ID
         */
        private Long userId;
    }

    /**
     * 导入规则请求体
     */
    @lombok.Data
    public static class ImportRuleRequest {
        /**
         * 规则内容
         */
        private String content;
    }
}
