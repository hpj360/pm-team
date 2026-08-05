package com.redteam.ai.controller;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 攻击链推理请求体
 *
 * @author 红方团队
 */
@Data
@Schema(description = "攻击链推理请求")
public class InferRequest {

    /**
     * NER 实体列表
     */
    @Schema(description = "NER 实体列表")
    private List<Map<String, Object>> nerEntities;

    /**
     * 标签列表
     */
    @Schema(description = "标签列表")
    private List<String> tags;

    /**
     * 文件上下文（摘要或关键段落）
     */
    @Schema(description = "文件上下文（摘要或关键段落）")
    private String fileContext;
}
