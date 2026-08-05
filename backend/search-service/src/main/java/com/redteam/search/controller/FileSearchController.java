package com.redteam.search.controller;

import com.redteam.common.annotation.AuditLog;
import com.redteam.common.annotation.RateLimit;
import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.api.dto.FileSearchDTO;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.Result;
import com.redteam.search.dto.FileIndexDTO;
import com.redteam.search.dto.SearchHistoryVO;
import com.redteam.search.dto.SearchRequestDTO;
import com.redteam.search.dto.SearchResultVO;
import com.redteam.search.service.FileSearchService;
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
 * 文件检索控制器
 *
 * <p>v2.5 增强：新增统一检索（KEYWORD / VECTOR / HYBRID）、热门词、历史、聚合、重建索引接口。
 * 保留 v2.1 既有接口（/query /semantic /highlight /suggest /index/{fileId}）。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "文件检索接口", description = "全文检索、语义搜索、混合检索等接口")
public class FileSearchController {

    private final FileSearchService fileSearchService;

    // ==================== v2.5 新增：统一检索接口 ====================

    /**
     * 统一检索（支持 KEYWORD / VECTOR / HYBRID）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @PostMapping
    @Operation(summary = "统一检索", description = "根据 searchType 路由到关键字/向量/混合检索")
    @RateLimit(qps = 10, limitType = "USER")
    @AuditLog(action = "SEARCH", resourceType = "FILE")
    public Result<SearchResultVO> search(@Valid @RequestBody SearchRequestDTO request) {
        log.info("统一检索: searchType={}, query={}", request.getSearchType(), request.getQuery());
        SearchResultVO result = fileSearchService.search(request);
        return Result.success(result);
    }

    /**
     * 仅关键字检索
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @PostMapping("/keyword")
    @Operation(summary = "关键字检索", description = "基于 ES 的全文关键字检索")
    public Result<SearchResultVO> keywordSearch(@Valid @RequestBody SearchRequestDTO request) {
        log.info("关键字检索: query={}", request.getQuery());
        request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
        SearchResultVO result = fileSearchService.keywordSearch(request);
        return Result.success(result);
    }

    /**
     * 仅向量检索
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @PostMapping("/vector")
    @Operation(summary = "向量检索", description = "基于 Milvus 的语义向量检索")
    public Result<SearchResultVO> vectorSearch(@Valid @RequestBody SearchRequestDTO request) {
        log.info("向量检索: query={}", request.getQuery());
        request.setSearchType(SearchRequestDTO.TYPE_VECTOR);
        SearchResultVO result = fileSearchService.vectorSearch(request);
        return Result.success(result);
    }

    /**
     * 仅混合检索（ES + Milvus，RRF 融合）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @PostMapping("/hybrid")
    @Operation(summary = "混合检索", description = "ES + Milvus 并行检索，RRF 融合排序")
    public Result<SearchResultVO> hybridSearch(@Valid @RequestBody SearchRequestDTO request) {
        log.info("混合检索: query={}", request.getQuery());
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        SearchResultVO result = fileSearchService.hybridSearch(request);
        return Result.success(result);
    }

    // ==================== v2.5 新增：行为分析与聚合 ====================

    /**
     * 获取热门检索词
     *
     * @param limit 返回数量
     * @return 热门检索词列表
     */
    @GetMapping("/hot-words")
    @Operation(summary = "获取热门检索词", description = "按检索次数降序返回热门词")
    public Result<List<String>> getHotWords(
            @Parameter(description = "返回数量") @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        log.info("获取热门检索词: limit={}", limit);
        List<String> words = fileSearchService.getHotWords(limit);
        return Result.success(words);
    }

    /**
     * 获取检索历史
     *
     * @param userId 用户 ID
     * @param limit  返回数量
     * @return 检索历史列表
     */
    @GetMapping("/history")
    @Operation(summary = "获取检索历史", description = "按时间降序返回用户检索历史")
    public Result<List<SearchHistoryVO>> getSearchHistory(
            @Parameter(description = "用户 ID") @RequestParam("userId") Long userId,
            @Parameter(description = "返回数量") @RequestParam(value = "limit", defaultValue = "10") Integer limit) {
        log.info("获取检索历史: userId={}, limit={}", userId, limit);
        List<SearchHistoryVO> history = fileSearchService.getSearchHistory(userId, limit);
        return Result.success(history);
    }

    /**
     * 获取聚合结果
     *
     * @param request 检索请求
     * @return 聚合结果
     */
    @PostMapping("/aggregations")
    @Operation(summary = "获取聚合结果", description = "按 fileType/targetId/sensitiveLevel/tags 聚合")
    public Result<Map<String, Object>> getAggregations(@RequestBody SearchRequestDTO request) {
        log.info("获取聚合结果: query={}", request == null ? null : request.getQuery());
        Map<String, Object> aggregations = fileSearchService.getAggregations(request);
        return Result.success(aggregations);
    }

    // ==================== v2.5 新增：索引管理 ====================

    /**
     * 手动索引指定文件
     *
     * @param fileId 文件 ID
     * @return 是否成功
     */
    @PostMapping("/index/{fileId}")
    @Operation(summary = "索引文件", description = "将文件添加到 ES + Milvus 索引")
    public Result<Void> indexFile(
            @Parameter(description = "文件 ID") @PathVariable("fileId") Long fileId) {
        log.info("手动索引文件: fileId={}", fileId);
        fileSearchService.indexFile(fileId);
        return Result.success();
    }

    /**
     * 删除索引
     *
     * @param fileId 文件 ID
     * @return 是否成功
     */
    @DeleteMapping("/index/{fileId}")
    @Operation(summary = "删除索引", description = "从 ES + Milvus 索引中删除文件")
    public Result<Void> deleteIndex(
            @Parameter(description = "文件 ID") @PathVariable("fileId") Long fileId) {
        log.info("删除索引: fileId={}", fileId);
        fileSearchService.deleteIndex(fileId);
        return Result.success();
    }

    /**
     * 全量重建索引
     *
     * @return 是否成功
     */
    @PostMapping("/reindex")
    @Operation(summary = "全量重建索引", description = "重置所有索引任务状态为 PENDING，触发重新索引")
    public Result<Void> reindex() {
        log.info("触发全量重建索引");
        fileSearchService.reindexAll();
        return Result.success();
    }

    /**
     * 索引文件（携带完整元数据，供内部服务调用）
     *
     * @param dto 文件索引数据
     * @return 是否成功
     */
    @PostMapping("/index")
    @Operation(summary = "索引文件（完整数据）", description = "写入 ES + Milvus，携带完整文件元数据")
    public Result<Void> indexFileWithMeta(@RequestBody FileIndexDTO dto) {
        log.info("索引文件（完整数据）: fileId={}, fileName={}",
                dto == null ? null : dto.getFileId(), dto == null ? null : dto.getFileName());
        fileSearchService.indexFile(dto);
        return Result.success();
    }

    // ==================== v2.1 既有接口（保留向后兼容） ====================

    /**
     * 全文检索（v2.1 既有）
     *
     * @param searchDTO 检索条件
     * @return 检索结果
     */
    @PostMapping("/query")
    @Operation(summary = "全文检索", description = "根据条件检索文件")
    public Result<PageResult<FileInfoDTO>> search(@RequestBody FileSearchDTO searchDTO) {
        log.info("全文检索: keyword={}", searchDTO.getKeyword());
        PageResult<FileInfoDTO> result = fileSearchService.search(searchDTO);
        return Result.success(result);
    }

    /**
     * 语义搜索（v2.1 既有）
     *
     * @param query              查询文本
     * @param similarityThreshold 相似度阈值
     * @param size               返回数量
     * @return 检索结果
     */
    @GetMapping("/semantic")
    @Operation(summary = "语义搜索", description = "基于向量相似度的语义搜索")
    public Result<List<FileInfoDTO>> semanticSearch(
            @Parameter(description = "查询文本") @RequestParam("query") String query,
            @Parameter(description = "相似度阈值") @RequestParam(value = "similarityThreshold", defaultValue = "0.7") Double similarityThreshold,
            @Parameter(description = "返回数量") @RequestParam(value = "size", defaultValue = "10") Integer size) {

        log.info("语义搜索: query={}", query);
        List<FileInfoDTO> result = fileSearchService.semanticSearch(query, similarityThreshold, size);
        return Result.success(result);
    }

    /**
     * 高亮检索（v2.1 既有）
     *
     * @param keyword 关键词
     * @param current 当前页
     * @param size    每页大小
     * @return 检索结果
     */
    @GetMapping("/highlight")
    @Operation(summary = "高亮检索", description = "检索并高亮显示匹配内容")
    public Result<PageResult<FileInfoDTO>> searchWithHighlight(
            @Parameter(description = "关键词") @RequestParam("keyword") String keyword,
            @Parameter(description = "当前页") @RequestParam(value = "current", defaultValue = "1") Integer current,
            @Parameter(description = "每页大小") @RequestParam(value = "size", defaultValue = "10") Integer size) {

        log.info("高亮检索: keyword={}", keyword);
        PageResult<FileInfoDTO> result = fileSearchService.searchWithHighlight(keyword, current, size);
        return Result.success(result);
    }

    /**
     * 获取搜索建议（v2.1 既有）
     *
     * @param prefix 前缀
     * @param size   返回数量
     * @return 建议列表
     */
    @GetMapping("/suggest")
    @Operation(summary = "获取搜索建议", description = "根据前缀获取搜索建议")
    public Result<List<String>> getSuggestions(
            @Parameter(description = "前缀") @RequestParam("prefix") String prefix,
            @Parameter(description = "返回数量") @RequestParam(value = "size", defaultValue = "10") Integer size) {

        log.info("获取搜索建议: prefix={}", prefix);
        List<String> suggestions = fileSearchService.getSuggestions(prefix, size);
        return Result.success(suggestions);
    }
}
