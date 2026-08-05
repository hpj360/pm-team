package com.redteam.search.service;

import com.redteam.common.api.dto.SearchTemplateDTO;
import com.redteam.common.api.dto.SearchTemplateVO;

import java.util.List;

/**
 * 搜索模板服务接口
 *
 * <p>提供搜索模板的保存、查询、删除能力，userId 从用户上下文获取。</p>
 *
 * @author 红方团队
 */
public interface SearchTemplateService {

    /**
     * 保存搜索模板
     *
     * @param dto 模板数据
     * @return 保存后的模板视图
     */
    SearchTemplateVO saveTemplate(SearchTemplateDTO dto);

    /**
     * 查询当前用户的搜索模板列表
     *
     * @return 模板列表
     */
    List<SearchTemplateVO> listTemplates();

    /**
     * 删除搜索模板（校验所有权）
     *
     * @param id 模板ID
     */
    void deleteTemplate(Long id);
}
