package com.redteam.search.controller;

import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.api.dto.FileSearchDTO;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.Result;
import com.redteam.search.service.FileSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 文件检索控制器
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "文件检索接口", description = "全文检索、语义搜索等接口")
public class FileSearchController {

    private final FileSearchService fileSearchService;

    /**
     * 全文检索
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
     * 语义搜索
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
     * 高亮检索
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
     * 获取搜索建议
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

    /**
     * 索引文件
     *
     * @param fileId 文件ID
     * @return 是否成功
     */
    @PostMapping("/index/{fileId}")
    @Operation(summary = "索引文件", description = "将文件添加到检索索引")
    public Result<Void> indexFile(
            @Parameter(description = "文件ID") @PathVariable("fileId") Long fileId) {

        log.info("索引文件: {}", fileId);
        fileSearchService.indexFile(fileId);
        return Result.success();
    }

    /**
     * 删除索引
     *
     * @param fileId 文件ID
     * @return 是否成功
     */
    @DeleteMapping("/index/{fileId}")
    @Operation(summary = "删除索引", description = "从检索索引中删除文件")
    public Result<Void> deleteIndex(
            @Parameter(description = "文件ID") @PathVariable("fileId") Long fileId) {

        log.info("删除索引: {}", fileId);
        fileSearchService.deleteIndex(fileId);
        return Result.success();
    }
}
