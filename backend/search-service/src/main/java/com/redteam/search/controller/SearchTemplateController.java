package com.redteam.search.controller;

import com.redteam.common.api.dto.SearchTemplateDTO;
import com.redteam.common.api.dto.SearchTemplateVO;
import com.redteam.common.result.Result;
import com.redteam.search.service.SearchTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 搜索模板管理控制器
 *
 * <p>提供搜索模板的保存、查询、删除接口，支持用户保存常用的搜索条件组合。</p>
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/api/search/templates")
@RequiredArgsConstructor
@Tag(name = "搜索模板管理", description = "搜索模板的保存、查询、删除接口")
public class SearchTemplateController {

    private final SearchTemplateService searchTemplateService;

    /**
     * 保存搜索模板
     *
     * @param dto 模板数据
     * @return 保存后的模板视图
     */
    @PostMapping
    @Operation(summary = "保存搜索模板", description = "保存当前用户的搜索条件为模板")
    public Result<SearchTemplateVO> saveTemplate(@Valid @RequestBody SearchTemplateDTO dto) {
        log.info("保存搜索模板: name={}", dto.getName());
        SearchTemplateVO vo = searchTemplateService.saveTemplate(dto);
        return Result.success(vo);
    }

    /**
     * 获取当前用户的搜索模板列表
     *
     * @return 模板列表
     */
    @GetMapping
    @Operation(summary = "获取当前用户的搜索模板列表", description = "按创建时间倒序返回模板列表")
    public Result<List<SearchTemplateVO>> listTemplates() {
        log.info("获取搜索模板列表");
        List<SearchTemplateVO> list = searchTemplateService.listTemplates();
        return Result.success(list);
    }

    /**
     * 删除搜索模板
     *
     * @param id 模板ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除搜索模板", description = "删除指定ID的搜索模板（需校验所有权）")
    public Result<Void> deleteTemplate(@PathVariable("id") Long id) {
        log.info("删除搜索模板: id={}", id);
        searchTemplateService.deleteTemplate(id);
        return Result.success();
    }
}
