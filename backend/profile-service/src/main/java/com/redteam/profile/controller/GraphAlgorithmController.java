package com.redteam.profile.controller;

import com.redteam.common.result.Result;
import com.redteam.profile.service.GraphAlgorithmService;
import com.redteam.profile.service.GraphAlgorithmService.AlgorithmInfo;
import com.redteam.profile.service.GraphAlgorithmService.AlgorithmResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 图算法控制器
 *
 * <p>提供 Neo4j GDS 图算法的查询与执行接口。GDS 未启用或调用异常时，
 * Service 层自动降级返回 success=false 的结果，接口仍可正常响应。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/profile/graph/algorithms")
@RequiredArgsConstructor
@Tag(name = "图算法服务", description = "基于 Neo4j GDS 的图算法查询与执行")
public class GraphAlgorithmController {

    private final GraphAlgorithmService graphAlgorithmService;

    /**
     * 列出可用图算法
     *
     * @return 算法信息列表
     */
    @GetMapping
    @Operation(summary = "列出可用图算法", description = "返回系统支持的 GDS 图算法清单（PageRank / 社区发现 / 最短路径 / 度中心性）")
    public Result<List<AlgorithmInfo>> listAlgorithms() {
        log.info("列出可用图算法");
        return Result.success(graphAlgorithmService.listAvailableAlgorithms());
    }

    /**
     * 执行图算法
     *
     * @param algorithm 算法名称：pagerank / community / shortestpath / centrality
     * @param params    算法参数（nodeLabel, relationshipType, limit, sourceId, targetId 等）
     * @return 算法执行结果
     */
    @PostMapping("/{algorithm}")
    @Operation(summary = "执行图算法", description = "按算法名称执行 GDS 图算法，GDS 不可用时返回降级结果")
    public Result<AlgorithmResult> executeAlgorithm(
            @Parameter(description = "算法名称：pagerank / community / shortestpath / centrality", required = true)
            @PathVariable String algorithm,
            @RequestBody(required = false) Map<String, Object> params) {
        log.info("执行图算法: algorithm={}", algorithm);
        return Result.success(graphAlgorithmService.executeAlgorithm(algorithm, params));
    }
}
