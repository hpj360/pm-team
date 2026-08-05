package com.redteam.profile.neo4j.controller;

import com.redteam.common.result.Result;
import com.redteam.profile.neo4j.dto.Neo4jGraphDTO;
import com.redteam.profile.neo4j.service.Neo4jRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Neo4j 关系图谱控制器
 *
 * <p>提供基于 Neo4j 图数据库的多跳关系查询接口，供前端 ECharts 力导向图渲染。
 * 当 Neo4j 不可用时，Service 层自动降级到 Mock 数据，接口仍可正常响应。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/profile/relations")
@RequiredArgsConstructor
@Tag(name = "关系图谱接口", description = "基于 Neo4j 的多跳关系图谱查询")
public class Neo4jRelationController {

    private final Neo4jRelationService neo4jRelationService;

    /**
     * 查询目标的 Neo4j 关系图谱
     *
     * <p>以指定目标为根节点，查询 1-N 跳范围内的所有关联节点与边，
     * 返回前端 ECharts 力导向图所需的 nodes + edges 结构。</p>
     *
     * <p>Neo4j 不可用时自动降级到 Mock 数据，接口仍返回 200 + Mock 图谱。</p>
     *
     * @param targetId 根目标ID
     * @param depth    关系展开深度（默认 1，最大 3）
     * @return 关系图谱数据
     */
    @GetMapping("/{targetId}")
    @Operation(summary = "查询 Neo4j 关系图谱", description = "以指定目标为根，查询多跳关联节点与边，用于 ECharts 力导向图渲染")
    public Result<Neo4jGraphDTO> getRelationGraph(
            @Parameter(description = "根目标ID", required = true) @PathVariable("targetId") Long targetId,
            @Parameter(description = "展开深度（1-3）") @RequestParam(value = "depth", defaultValue = "1") Integer depth) {
        log.info("查询 Neo4j 关系图谱: targetId={}, depth={}", targetId, depth);
        return Result.success(neo4jRelationService.getRelationGraph(targetId, depth));
    }
}
