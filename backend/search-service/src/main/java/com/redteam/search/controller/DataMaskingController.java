package com.redteam.search.controller;

import com.redteam.common.entity.DataMaskingRuleEntity;
import com.redteam.common.result.Result;
import com.redteam.common.service.DataMaskingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据脱敏管理控制器
 *
 * <p>提供脱敏规则的增删改查、启用/禁用切换及规则效果预览接口。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/masking")
@RequiredArgsConstructor
@Tag(name = "数据脱敏管理", description = "数据脱敏规则的增删改查及效果预览")
public class DataMaskingController {

    private final DataMaskingService dataMaskingService;

    /**
     * 脱敏规则列表
     *
     * @param ruleType            规则类型（可空）
     * @param classificationLevel 适用密级（可空）
     * @param enabled             启用状态（可空）
     * @return 规则列表
     */
    @GetMapping("/rules")
    @Operation(summary = "脱敏规则列表", description = "按规则类型、密级、启用状态筛选，参数为空时不筛选")
    public Result<List<DataMaskingRuleEntity>> listRules(
            @Parameter(description = "规则类型：PHONE/IDCARD/IP/EMAIL/CUSTOM")
            @RequestParam(required = false) String ruleType,
            @Parameter(description = "适用密级：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET")
            @RequestParam(required = false) String classificationLevel,
            @Parameter(description = "启用状态：0禁用 1启用")
            @RequestParam(required = false) Integer enabled) {
        log.info("查询脱敏规则列表: ruleType={}, classificationLevel={}, enabled={}", ruleType, classificationLevel, enabled);
        List<DataMaskingRuleEntity> list = dataMaskingService.listRules(ruleType, classificationLevel, enabled);
        return Result.success(list);
    }

    /**
     * 创建脱敏规则
     *
     * @param rule 规则实体
     * @return 创建后的规则
     */
    @PostMapping("/rules")
    @Operation(summary = "创建脱敏规则", description = "新增一条脱敏规则，需提供规则名称、正则表达式、替换模板")
    public Result<DataMaskingRuleEntity> createRule(@RequestBody DataMaskingRuleEntity rule) {
        log.info("创建脱敏规则: ruleName={}", rule.getRuleName());
        DataMaskingRuleEntity created = dataMaskingService.createRule(rule);
        return Result.success(created);
    }

    /**
     * 更新脱敏规则
     *
     * @param id   规则ID
     * @param rule 规则实体
     * @return 更新后的规则
     */
    @PutMapping("/rules/{id}")
    @Operation(summary = "更新脱敏规则", description = "更新指定ID的脱敏规则字段")
    public Result<DataMaskingRuleEntity> updateRule(@PathVariable Long id,
                                                    @RequestBody DataMaskingRuleEntity rule) {
        log.info("更新脱敏规则: id={}", id);
        DataMaskingRuleEntity updated = dataMaskingService.updateRule(id, rule);
        return Result.success(updated);
    }

    /**
     * 删除脱敏规则
     *
     * @param id 规则ID
     * @return 操作结果
     */
    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除脱敏规则", description = "删除指定ID的脱敏规则")
    public Result<Void> deleteRule(@PathVariable Long id) {
        log.info("删除脱敏规则: id={}", id);
        dataMaskingService.deleteRule(id);
        return Result.success();
    }

    /**
     * 启用/禁用脱敏规则
     *
     * @param id 规则ID
     * @return 操作结果
     */
    @PatchMapping("/rules/{id}/toggle")
    @Operation(summary = "启用/禁用", description = "切换指定ID脱敏规则的启用状态")
    public Result<Void> toggleRule(@PathVariable Long id) {
        log.info("切换脱敏规则状态: id={}", id);
        dataMaskingService.toggleRule(id);
        return Result.success();
    }

    /**
     * 测试规则效果
     *
     * @param pattern     正则表达式
     * @param replacement 替换模板
     * @param testText    测试文本
     * @return 脱敏后的文本
     */
    @PostMapping("/rules/test")
    @Operation(summary = "测试规则效果", description = "用给定的正则表达式和替换模板对测试文本执行脱敏预览")
    public Result<String> testRule(@RequestParam String pattern,
                                   @RequestParam String replacement,
                                   @RequestParam String testText) {
        log.info("测试脱敏规则: pattern={}, testText={}", pattern, testText);
        String result = dataMaskingService.testRule(pattern, replacement, testText);
        return Result.success(result);
    }
}
