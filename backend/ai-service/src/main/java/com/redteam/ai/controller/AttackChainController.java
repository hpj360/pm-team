package com.redteam.ai.controller;

import com.redteam.ai.service.AttackChainInferenceService;
import com.redteam.common.entity.AttackChainEntity;
import com.redteam.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
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
 * AI 攻击链推理控制器
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/attack-chain")
@Tag(name = "AI 攻击链推理", description = "基于 NER 实体与关系图谱的攻击链自动推理")
public class AttackChainController {

    @Autowired
    private AttackChainInferenceService service;

    /**
     * 获取文件攻击链推理结果
     *
     * @param fileId 文件ID
     * @return 推理结果
     */
    @GetMapping("/{fileId}")
    @Operation(summary = "获取文件攻击链推理", description = "根据文件ID查询已有的攻击链推理结果")
    public Result<AttackChainEntity> get(@PathVariable Long fileId) {
        AttackChainEntity entity = service.getByFileId(fileId);
        return Result.success(entity);
    }

    /**
     * 手动触发攻击链推理
     *
     * @param fileId  文件ID
     * @param request 推理请求参数（NER 实体、标签、文件上下文）
     * @return 推理结果
     */
    @PostMapping("/{fileId}/infer")
    @Operation(summary = "手动触发推理", description = "基于文件上下文、NER 实体、标签手动触发攻击链推理")
    public Result<AttackChainEntity> infer(@PathVariable Long fileId,
                                           @RequestBody InferRequest request) {
        log.info("手动触发攻击链推理, fileId={}", fileId);
        AttackChainEntity entity = service.inferAttackChain(
                fileId,
                request.getNerEntities(),
                request.getTags(),
                request.getFileContext());
        return Result.success(entity);
    }
}
