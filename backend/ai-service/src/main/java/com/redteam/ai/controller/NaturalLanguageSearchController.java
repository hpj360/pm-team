package com.redteam.ai.controller;

import com.redteam.ai.dto.NlSearchRequest;
import com.redteam.ai.service.NaturalLanguageSearchService;
import com.redteam.ai.vo.NlSearchResult;
import com.redteam.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 自然语言搜索控制器
 *
 * <p>提供两个接口：
 * <ul>
 *   <li>POST /api/ai/nlsearch —— 自然语言搜索（解析 + 检索）</li>
 *   <li>POST /api/ai/nlparse —— 仅解析自然语言为搜索条件（不执行搜索）</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 搜索", description = "自然语言搜索相关接口")
public class NaturalLanguageSearchController {

    @Autowired
    private NaturalLanguageSearchService nlSearchService;

    /**
     * 自然语言搜索
     *
     * <p>将自然语言解析为结构化搜索条件，并调用 search-service 执行检索。</p>
     *
     * @param request 自然语言搜索请求（包含 query 字段）
     * @return 搜索结果
     */
    @PostMapping("/nlsearch")
    @Operation(summary = "自然语言搜索", description = "利用 LLM 将自然语言解析为结构化条件并执行搜索")
    public Result<NlSearchResult> naturalLanguageSearch(@RequestBody NlSearchRequest request) {
        log.info("自然语言搜索: query={}", request == null ? null : request.getQuery());
        NlSearchResult result = nlSearchService.search(request == null ? null : request.getQuery());
        return Result.success(result);
    }

    /**
     * 仅解析自然语言为搜索条件（不执行搜索）
     *
     * @param request 自然语言搜索请求（包含 query 字段）
     * @return 解析后的结构化搜索条件
     */
    @PostMapping("/nlparse")
    @Operation(summary = "仅解析自然语言为搜索条件", description = "将自然语言转换为结构化搜索条件 JSON，不执行搜索")
    public Result<Map<String, Object>> parseOnly(@RequestBody NlSearchRequest request) {
        log.info("自然语言解析: query={}", request == null ? null : request.getQuery());
        Map<String, Object> conditions = nlSearchService.parseToSearchConditions(
                request == null ? null : request.getQuery());
        return Result.success(conditions);
    }
}
