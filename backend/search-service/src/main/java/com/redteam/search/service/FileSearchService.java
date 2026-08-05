package com.redteam.search.service;

import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.api.dto.FileSearchDTO;
import com.redteam.common.result.PageResult;
import com.redteam.search.dto.FileIndexDTO;
import com.redteam.search.dto.SearchHistoryVO;
import com.redteam.search.dto.SearchRequestDTO;
import com.redteam.search.dto.SearchResultVO;

import java.util.List;
import java.util.Map;

/**
 * 文件检索服务接口
 *
 * <p>v2.5 增强：统一检索入口（KEYWORD / VECTOR / HYBRID）、索引管理、检索历史与聚合统计。</p>
 *
 * @author 红方团队
 */
public interface FileSearchService {

    // ==================== v2.5 新增：统一检索接口 ====================

    /**
     * 统一检索入口（根据 searchType 路由到关键字 / 向量 / 混合检索）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    SearchResultVO search(SearchRequestDTO request);

    /**
     * 关键字检索（ES）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    SearchResultVO keywordSearch(SearchRequestDTO request);

    /**
     * 向量检索（Milvus）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    SearchResultVO vectorSearch(SearchRequestDTO request);

    /**
     * 混合检索（ES + Milvus，RRF 融合）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    SearchResultVO hybridSearch(SearchRequestDTO request);

    // ==================== v2.5 新增：索引管理接口 ====================

    /**
     * 索引文件（写入 ES + Milvus）
     *
     * @param dto 文件索引数据
     */
    void indexFile(FileIndexDTO dto);

    /**
     * 删除索引（ES + Milvus）
     *
     * @param fileId 文件 ID
     */
    void deleteIndex(Long fileId);

    /**
     * 全量重建索引
     */
    void reindexAll();

    // ==================== v2.5 新增：行为分析接口 ====================

    /**
     * 获取热门检索词
     *
     * @param limit 返回数量
     * @return 热门检索词列表
     */
    List<String> getHotWords(int limit);

    /**
     * 获取检索历史
     *
     * @param userId 用户 ID
     * @param limit  返回数量
     * @return 检索历史列表
     */
    List<SearchHistoryVO> getSearchHistory(Long userId, int limit);

    /**
     * 获取聚合结果
     *
     * @param request 检索请求
     * @return 聚合结果（key=聚合名称，value=聚合内容）
     */
    Map<String, Object> getAggregations(SearchRequestDTO request);

    // ==================== v2.1 既有接口（保留向后兼容） ====================

    /**
     * 全文检索（v2.1 既有）
     *
     * @param searchDTO 检索条件
     * @return 检索结果
     */
    PageResult<FileInfoDTO> search(FileSearchDTO searchDTO);

    /**
     * 语义搜索（向量检索，v2.1 既有）
     *
     * @param query              查询文本
     * @param similarityThreshold 相似度阈值
     * @param size               返回数量
     * @return 检索结果
     */
    List<FileInfoDTO> semanticSearch(String query, Double similarityThreshold, Integer size);

    /**
     * 高亮检索（v2.1 既有）
     *
     * @param keyword 关键词
     * @param current 当前页
     * @param size    每页大小
     * @return 检索结果
     */
    PageResult<FileInfoDTO> searchWithHighlight(String keyword, Integer current, Integer size);

    /**
     * 索引文件（v2.1 既有，按 fileId）
     *
     * @param fileId 文件 ID
     * @return 是否成功
     */
    boolean indexFile(Long fileId);

    /**
     * 批量索引文件（v2.1 既有）
     *
     * @param fileIds 文件 ID 列表
     * @return 是否成功
     */
    boolean batchIndexFiles(List<Long> fileIds);

    /**
     * 删除索引（v2.1 既有）
     *
     * @param fileId 文件 ID
     * @return 是否成功
     */
    boolean deleteIndex(Long fileId, boolean legacy);

    /**
     * 更新索引（v2.1 既有）
     *
     * @param fileId 文件 ID
     * @return 是否成功
     */
    boolean updateIndex(Long fileId);

    /**
     * 获取搜索建议（v2.1 既有）
     *
     * @param prefix 前缀
     * @param size   返回数量
     * @return 建议列表
     */
    List<String> getSuggestions(String prefix, Integer size);

    /**
     * 聚合统计（v2.1 既有）
     *
     * @param field 聚合字段
     * @return 聚合结果
     */
    Object aggregate(String field);
}
