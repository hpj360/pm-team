package com.redteam.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.api.dto.FileSearchDTO;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.ResultCode;
import com.redteam.search.config.SearchCacheConfig;
import com.redteam.search.config.SearchProperties;
import com.redteam.search.dto.FileIndexDTO;
import com.redteam.search.dto.SearchHistoryVO;
import com.redteam.search.dto.SearchHitVO;
import com.redteam.search.dto.SearchRequestDTO;
import com.redteam.search.dto.SearchResultVO;
import com.redteam.search.dto.VectorSearchResultDTO;
import com.redteam.search.entity.SearchHistoryEntity;
import com.redteam.search.entity.SearchIndexTaskEntity;
import com.redteam.search.mapper.SearchHistoryMapper;
import com.redteam.search.mapper.SearchHotWordMapper;
import com.redteam.search.mapper.SearchIndexTaskMapper;
import com.redteam.search.service.FileSearchService;
import com.redteam.search.service.VectorEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件检索服务实现
 *
 * <p>统一检索入口（KEYWORD / VECTOR / HYBRID），编排 ES、Milvus、向量化服务。
 * 提供索引管理、检索历史、热门词、聚合统计等能力。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSearchServiceImpl implements FileSearchService {

    private final ElasticsearchService elasticsearchService;
    private final MilvusService milvusService;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final HybridSearchService hybridSearchService;
    private final SearchProperties searchProperties;
    private final SearchCacheConfig.SearchCache searchCache;

    private final SearchIndexTaskMapper searchIndexTaskMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final SearchHotWordMapper searchHotWordMapper;

    /**
     * 统一检索入口（根据 searchType 路由）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @Override
    public SearchResultVO search(SearchRequestDTO request) {
        validateRequest(request);
        normalizeSearchType(request);

        // 二次检索：在已有结果集（refineFileIds）中按 refineQuery 进一步筛选
        // 二次检索本质是关键字检索 + 文件ID范围过滤，忽略缓存（不同 refineFileIds 命中不同）
        if (StrUtil.isNotBlank(request.getRefineQuery())
                && request.getRefineFileIds() != null
                && !request.getRefineFileIds().isEmpty()) {
            log.info("二次检索: refineQuery={}, refineFileIds.size={}",
                    request.getRefineQuery(), request.getRefineFileIds().size());
            request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
            SearchResultVO refineResult = keywordSearch(request);
            recordSearchBehavior(request, refineResult);
            return refineResult;
        }

        // 缓存命中检查
        String cacheKey = buildCacheKey(request);
        SearchResultVO cached = searchCache.get(cacheKey);
        if (cached != null) {
            log.debug("检索缓存命中: key={}", cacheKey);
            return cached;
        }

        SearchResultVO result;
        switch (request.getSearchType()) {
            case SearchRequestDTO.TYPE_KEYWORD:
                result = keywordSearch(request);
                break;
            case SearchRequestDTO.TYPE_VECTOR:
                result = vectorSearch(request);
                break;
            case SearchRequestDTO.TYPE_HYBRID:
                result = hybridSearch(request);
                break;
            default:
                throw BusinessException.of(ResultCode.PARAM_ERROR,
                        "不支持的检索类型: " + request.getSearchType());
        }

        // 异步记录行为
        recordSearchBehavior(request, result);

        // 写入缓存
        searchCache.put(cacheKey, result);
        return result;
    }

    /**
     * 关键字检索（ES）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @Override
    public SearchResultVO keywordSearch(SearchRequestDTO request) {
        validateRequest(request);
        normalizeSearchType(request);
        return elasticsearchService.keywordSearch(request);
    }

    /**
     * 向量检索（Milvus）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @Override
    public SearchResultVO vectorSearch(SearchRequestDTO request) {
        validateRequest(request);
        normalizeSearchType(request);
        long start = System.currentTimeMillis();

        if (StrUtil.isBlank(request.getQuery())) {
            SearchResultVO empty = SearchResultVO.empty(request);
            empty.setResponseTimeMs(System.currentTimeMillis() - start);
            return empty;
        }

        List<Float> queryVector = vectorEmbeddingService.embed(request.getQuery());
        int topK = request.getPageSize() * request.getPageNum();
        String filter = MilvusService.buildFilter(request.getTargetId());
        List<VectorSearchResultDTO> vectorResults = milvusService.vectorSearch(queryVector, topK, filter);

        // 过滤最小相关度
        Float minScore = request.getMinScore();
        if (minScore != null) {
            vectorResults = vectorResults.stream()
                    .filter(r -> r.getScore() == null || r.getScore() >= minScore)
                    .collect(Collectors.toList());
        }

        // 分页
        int from = Math.max(0, (request.getPageNum() - 1) * request.getPageSize());
        int to = Math.min(vectorResults.size(), from + request.getPageSize());
        List<VectorSearchResultDTO> page = from < to ? vectorResults.subList(from, to) : List.of();

        List<SearchHitVO> hits = page.stream()
                .map(vr -> convertVectorResultToHit(vr))
                .collect(Collectors.toList());

        SearchResultVO vo = new SearchResultVO();
        vo.setTotal((long) vectorResults.size());
        vo.setPageNum(request.getPageNum());
        vo.setPageSize(request.getPageSize());
        vo.setHits(hits);
        vo.setResponseTimeMs(System.currentTimeMillis() - start);
        return vo;
    }

    /**
     * 混合检索（ES + Milvus，RRF 融合）
     *
     * @param request 检索请求
     * @return 检索结果
     */
    @Override
    public SearchResultVO hybridSearch(SearchRequestDTO request) {
        validateRequest(request);
        normalizeSearchType(request);
        return hybridSearchService.hybridSearch(request);
    }

    // ==================== 索引管理 ====================

    /**
     * 索引文件（写入 ES + Milvus），并更新索引任务状态
     *
     * @param dto 文件索引数据
     */
    @Override
    public void indexFile(FileIndexDTO dto) {
        if (dto == null || dto.getFileId() == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件索引数据 fileId 不能为空");
        }
        log.info("开始索引文件: fileId={}, fileName={}", dto.getFileId(), dto.getFileName());

        // 上锁/标记 INDEXING
        SearchIndexTaskEntity task = upsertTask(dto);

        boolean esOk = false;
        boolean milvusOk = false;
        String errorMsg = null;

        // 1. ES 索引
        try {
            elasticsearchService.indexFile(dto);
            esOk = true;
        } catch (Exception e) {
            errorMsg = "ES 索引失败: " + e.getMessage();
            log.error(errorMsg, e);
        }

        // 2. Milvus 向量索引
        try {
            String text = StrUtil.blankToDefault(dto.getTextContent(), dto.getFileName());
            List<Float> embedding = vectorEmbeddingService.embed(text);
            Map<String, Object> meta = buildMilvusMetadata(dto);
            milvusService.insertVector(dto.getFileId(), embedding, meta);
            milvusOk = true;
        } catch (Exception e) {
            errorMsg = StrUtil.blankToDefault(errorMsg, "") + "; Milvus 索引失败: " + e.getMessage();
            log.error("Milvus 索引失败: fileId={}", dto.getFileId(), e);
        }

        // 3. 更新任务状态
        updateTaskStatus(task, esOk, milvusOk, errorMsg);

        // 4. 清除相关检索缓存
        invalidateCache();

        if (!esOk && !milvusOk) {
            throw BusinessException.of(ResultCode.INDEX_CREATE_ERROR,
                    StrUtil.blankToDefault(errorMsg, "索引创建失败"));
        }
    }

    /**
     * 删除索引（ES + Milvus）
     *
     * @param fileId 文件 ID
     */
    @Override
    public void deleteIndex(Long fileId) {
        if (fileId == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "fileId 不能为空");
        }
        log.info("删除索引: fileId={}", fileId);
        boolean esOk = false;
        boolean milvusOk = false;
        try {
            elasticsearchService.deleteDocument(fileId);
            esOk = true;
        } catch (Exception e) {
            log.error("ES 删除索引失败: fileId={}", fileId, e);
        }
        try {
            milvusService.deleteVector(fileId);
            milvusOk = true;
        } catch (Exception e) {
            log.error("Milvus 删除索引失败: fileId={}", fileId, e);
        }
        // 更新任务状态为已删除
        try {
            searchIndexTaskMapper.delete(new LambdaQueryWrapper<SearchIndexTaskEntity>()
                    .eq(SearchIndexTaskEntity::getFileId, fileId));
        } catch (Exception e) {
            log.warn("删除索引任务记录失败: fileId={}", fileId, e);
        }
        invalidateCache();
        if (!esOk && !milvusOk) {
            throw BusinessException.of(ResultCode.INDEX_DELETE_ERROR, "删除索引失败");
        }
    }

    /**
     * 全量重建索引（标记所有任务为 PENDING）
     *
     * <p>实际重建由定时任务或手动触发 {@link #reindexPending()} 完成，
     * 此方法仅重置任务状态以触发重新处理。</p>
     */
    @Override
    public void reindexAll() {
        log.info("触发全量重建索引");
        try {
            SearchIndexTaskEntity update = new SearchIndexTaskEntity();
            update.setIndexStatus(SearchIndexTaskEntity.STATUS_PENDING);
            update.setEsIndexed(false);
            update.setMilvusIndexed(false);
            update.setErrorMsg(null);
            update.setUpdatedAt(LocalDateTime.now());
            searchIndexTaskMapper.update(update, new LambdaUpdateWrapper<SearchIndexTaskEntity>()
                    .ne(SearchIndexTaskEntity::getIndexStatus, SearchIndexTaskEntity.STATUS_INDEXING));
            invalidateCache();
        } catch (Exception e) {
            log.error("全量重建索引触发失败", e);
            throw BusinessException.of(ResultCode.SEARCH_ERROR, "全量重建索引触发失败: " + e.getMessage());
        }
    }

    /**
     * 重新索引所有 PENDING/FAILED 状态的任务（供定时任务调用）
     */
    public void reindexPending() {
        List<SearchIndexTaskEntity> pending = searchIndexTaskMapper.selectList(
                new LambdaQueryWrapper<SearchIndexTaskEntity>()
                        .in(SearchIndexTaskEntity::getIndexStatus,
                                SearchIndexTaskEntity.STATUS_PENDING,
                                SearchIndexTaskEntity.STATUS_FAILED));
        log.info("待重建索引任务数: {}", pending.size());
        for (SearchIndexTaskEntity task : pending) {
            try {
                FileIndexDTO dto = new FileIndexDTO();
                dto.setFileId(task.getFileId());
                dto.setFileName(task.getFileName());
                dto.setFileSm3(task.getFileSm3());
                indexFile(dto);
            } catch (Exception e) {
                log.error("重建索引失败: fileId={}", task.getFileId(), e);
            }
        }
    }

    // ==================== 行为分析 ====================

    /**
     * 获取热门检索词
     *
     * @param limit 返回数量
     * @return 热门检索词列表
     */
    @Override
    public List<String> getHotWords(int limit) {
        if (limit <= 0) {
            limit = 10;
        }
        try {
            Page<com.redteam.search.entity.SearchHotWordEntity> page = new Page<>(1, limit);
            Page<com.redteam.search.entity.SearchHotWordEntity> result = searchHotWordMapper.selectPage(page,
                    new LambdaQueryWrapper<com.redteam.search.entity.SearchHotWordEntity>()
                            .orderByDesc(com.redteam.search.entity.SearchHotWordEntity::getSearchCount));
            return result.getRecords().stream()
                    .map(com.redteam.search.entity.SearchHotWordEntity::getWord)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取热门检索词失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取检索历史
     *
     * @param userId 用户 ID
     * @param limit  返回数量
     * @return 检索历史列表
     */
    @Override
    public List<SearchHistoryVO> getSearchHistory(Long userId, int limit) {
        if (userId == null) {
            return Collections.emptyList();
        }
        if (limit <= 0) {
            limit = 10;
        }
        try {
            List<SearchHistoryEntity> list = searchHistoryMapper.selectList(
                    new LambdaQueryWrapper<SearchHistoryEntity>()
                            .eq(SearchHistoryEntity::getUserId, userId)
                            .orderByDesc(SearchHistoryEntity::getCreatedAt)
                            .last("LIMIT " + limit));
            return list.stream().map(e -> {
                SearchHistoryVO vo = new SearchHistoryVO();
                BeanUtil.copyProperties(e, vo);
                vo.setCreatedAt(e.getCreatedAt());
                return vo;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取检索历史失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取聚合结果
     *
     * @param request 检索请求
     * @return 聚合结果
     */
    @Override
    public Map<String, Object> getAggregations(SearchRequestDTO request) {
        if (request == null) {
            return Map.of();
        }
        return elasticsearchService.getAggregations(request);
    }

    // ==================== v2.1 既有接口实现 ====================

    /**
     * 全文检索（v2.1 既有，适配为关键字检索）
     *
     * @param searchDTO 检索条件
     * @return 检索结果
     */
    @Override
    public PageResult<FileInfoDTO> search(FileSearchDTO searchDTO) {
        if (searchDTO == null) {
            return PageResult.empty();
        }
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery(searchDTO.getKeyword());
        request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
        if (searchDTO.getFileTypes() != null && !searchDTO.getFileTypes().isEmpty()) {
            request.setFileType(searchDTO.getFileTypes().get(0));
        }
        if (searchDTO.getTargetIds() != null && !searchDTO.getTargetIds().isEmpty()) {
            request.setTargetId(searchDTO.getTargetIds().get(0));
        }
        request.setTags(searchDTO.getTags());
        if (searchDTO.getSensitiveLevels() != null && !searchDTO.getSensitiveLevels().isEmpty()) {
            request.setSensitiveLevel(searchDTO.getSensitiveLevels().get(0));
        }
        request.setPageNum(searchDTO.getCurrent());
        request.setPageSize(searchDTO.getSize());
        SearchResultVO result = keywordSearch(request);
        return PageResult.of((long) result.getPageNum(), (long) result.getPageSize(),
                result.getTotal(), convertToFileInfoList(result.getHits()));
    }

    /**
     * 语义搜索（v2.1 既有，适配为向量检索）
     *
     * @param query              查询文本
     * @param similarityThreshold 相似度阈值
     * @param size               返回数量
     * @return 检索结果
     */
    @Override
    public List<FileInfoDTO> semanticSearch(String query, Double similarityThreshold, Integer size) {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery(query);
        request.setSearchType(SearchRequestDTO.TYPE_VECTOR);
        request.setMinScore(similarityThreshold == null ? null : similarityThreshold.floatValue());
        request.setPageNum(1);
        request.setPageSize(size == null ? 10 : size);
        SearchResultVO result = vectorSearch(request);
        return convertToFileInfoList(result.getHits());
    }

    /**
     * 高亮检索（v2.1 既有，适配为关键字检索）
     *
     * @param keyword 关键词
     * @param current 当前页
     * @param size    每页大小
     * @return 检索结果
     */
    @Override
    public PageResult<FileInfoDTO> searchWithHighlight(String keyword, Integer current, Integer size) {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery(keyword);
        request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
        request.setPageNum(current == null ? 1 : current);
        request.setPageSize(size == null ? 10 : size);
        SearchResultVO result = keywordSearch(request);
        return PageResult.of((long) result.getPageNum(), (long) result.getPageSize(),
                result.getTotal(), convertToFileInfoList(result.getHits()));
    }

    /**
     * 索引文件（v2.1 既有，按 fileId）
     *
     * @param fileId 文件 ID
     * @return 是否成功
     */
    @Override
    public boolean indexFile(Long fileId) {
        if (fileId == null) {
            return false;
        }
        // v2.1 既有接口仅按 fileId 索引，无法获取完整元数据，
        // 此处构造最小 FileIndexDTO 触发索引流程，实际生产应通过 RPC 获取文件详情
        FileIndexDTO dto = new FileIndexDTO();
        dto.setFileId(fileId);
        try {
            indexFile(dto);
            return true;
        } catch (Exception e) {
            log.error("按 fileId 索引文件失败: fileId={}", fileId, e);
            return false;
        }
    }

    /**
     * 批量索引文件（v2.1 既有）
     *
     * @param fileIds 文件 ID 列表
     * @return 是否成功
     */
    @Override
    public boolean batchIndexFiles(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return false;
        }
        boolean allOk = true;
        for (Long fileId : fileIds) {
            try {
                indexFile(fileId);
            } catch (Exception e) {
                log.error("批量索引失败: fileId={}", fileId, e);
                allOk = false;
            }
        }
        return allOk;
    }

    /**
     * 删除索引（v2.1 既有）
     *
     * @param fileId 文件 ID
     * @return 是否成功
     */
    @Override
    public boolean deleteIndex(Long fileId, boolean legacy) {
        try {
            deleteIndex(fileId);
            return true;
        } catch (Exception e) {
            log.error("删除索引失败: fileId={}", fileId, e);
            return false;
        }
    }

    /**
     * 更新索引（v2.1 既有，先删后插）
     *
     * @param fileId 文件 ID
     * @return 是否成功
     */
    @Override
    public boolean updateIndex(Long fileId) {
        if (fileId == null) {
            return false;
        }
        try {
            deleteIndex(fileId);
        } catch (Exception e) {
            log.warn("更新索引时删除旧索引失败（可忽略）: fileId={}", fileId, e);
        }
        return indexFile(fileId);
    }

    /**
     * 获取搜索建议（v2.1 既有）
     *
     * @param prefix 前缀
     * @param size   返回数量
     * @return 建议列表
     */
    @Override
    public List<String> getSuggestions(String prefix, Integer size) {
        return elasticsearchService.getSuggestions(prefix, size);
    }

    /**
     * 聚合统计（v2.1 既有）
     *
     * @param field 聚合字段
     * @return 聚合结果
     */
    @Override
    public Object aggregate(String field) {
        SearchRequestDTO request = new SearchRequestDTO();
        return elasticsearchService.getAggregations(request).get(field);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验检索请求
     *
     * @param request 检索请求
     */
    private void validateRequest(SearchRequestDTO request) {
        if (request == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "检索请求不能为空");
        }
        if (request.getPageNum() == null || request.getPageNum() < 1) {
            request.setPageNum(1);
        }
        if (request.getPageSize() == null || request.getPageSize() < 1) {
            request.setPageSize(10);
        }
        if (request.getPageSize() > 100) {
            request.setPageSize(100);
        }
    }

    /**
     * 规范化检索类型
     *
     * @param request 检索请求
     */
    private void normalizeSearchType(SearchRequestDTO request) {
        if (StrUtil.isBlank(request.getSearchType())) {
            request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
            return;
        }
        String upper = request.getSearchType().toUpperCase();
        if (!SearchRequestDTO.TYPE_KEYWORD.equals(upper)
                && !SearchRequestDTO.TYPE_VECTOR.equals(upper)
                && !SearchRequestDTO.TYPE_HYBRID.equals(upper)) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "不支持的检索类型: " + request.getSearchType());
        }
        request.setSearchType(upper);
    }

    /**
     * 构建缓存键
     *
     * @param request 检索请求
     * @return 缓存键
     */
    private String buildCacheKey(SearchRequestDTO request) {
        String json = JSONUtil.toJsonStr(request);
        String md5 = DigestUtil.sha256Hex(json).substring(0, 16);
        return "search:cache:" + request.getSearchType() + ":" + md5;
    }

    /**
     * 失效检索缓存
     */
    private void invalidateCache() {
        // 简化实现：缓存有过期时间，索引变更后等待自然过期即可
        // 生产环境可基于 Redis 模式匹配删除 search:cache:* 键
        log.debug("索引变更，检索缓存将自然过期");
    }

    /**
     * 异步记录检索行为（历史 + 热词）
     *
     * @param request 检索请求
     * @param result  检索结果
     */
    private void recordSearchBehavior(SearchRequestDTO request, SearchResultVO result) {
        try {
            // 记录检索历史
            SearchHistoryEntity history = new SearchHistoryEntity();
            history.setUserId(request.getUserId());
            history.setSearchType(request.getSearchType());
            history.setQueryText(request.getQuery());
            history.setFilters(buildFiltersJson(request));
            history.setResultCount(result.getHits() == null ? 0 : result.getHits().size());
            history.setResponseTimeMs(result.getResponseTimeMs());
            history.setCreatedAt(LocalDateTime.now());
            searchHistoryMapper.insert(history);

            // 累加热词
            if (StrUtil.isNotBlank(request.getQuery())) {
                searchHotWordMapper.incrementCount(request.getQuery().trim());
            }
        } catch (Exception e) {
            log.warn("记录检索行为失败（不影响主流程）: {}", e.getMessage());
        }
    }

    /**
     * 构建过滤条件 JSON
     *
     * @param request 检索请求
     * @return JSON 字符串
     */
    private String buildFiltersJson(SearchRequestDTO request) {
        Map<String, Object> filters = new HashMap<>();
        if (request.getFileType() != null) {
            filters.put("fileType", request.getFileType());
        }
        if (request.getTargetId() != null) {
            filters.put("targetId", request.getTargetId());
        }
        if (request.getSensitiveLevel() != null) {
            filters.put("sensitiveLevel", request.getSensitiveLevel());
        }
        if (request.getTags() != null) {
            filters.put("tags", request.getTags());
        }
        if (request.getDateFrom() != null) {
            filters.put("dateFrom", request.getDateFrom().toString());
        }
        if (request.getDateTo() != null) {
            filters.put("dateTo", request.getDateTo().toString());
        }
        return filters.isEmpty() ? null : JSONUtil.toJsonStr(filters);
    }

    /**
     * 向量检索结果转 SearchHitVO
     *
     * @param vr 向量检索结果
     * @return SearchHitVO
     */
    private SearchHitVO convertVectorResultToHit(VectorSearchResultDTO vr) {
        SearchHitVO vo = new SearchHitVO();
        vo.setFileId(vr.getFileId());
        vo.setScore(vr.getScore());
        vo.setSearchType(SearchRequestDTO.TYPE_VECTOR);
        if (vr.getMetadata() != null) {
            Object name = vr.getMetadata().get(MilvusService.FIELD_FILE_NAME);
            vo.setFileName(name == null ? null : name.toString());
            Object sm3 = vr.getMetadata().get(MilvusService.FIELD_FILE_SM3);
            vo.setFileSm3(sm3 == null ? null : sm3.toString());
            Object targetId = vr.getMetadata().get(MilvusService.FIELD_TARGET_ID);
            if (targetId instanceof Number) {
                vo.setTargetId(((Number) targetId).longValue());
            }
            Object uploadTime = vr.getMetadata().get(MilvusService.FIELD_UPLOAD_TIME);
            if (uploadTime instanceof Number) {
                long ts = ((Number) uploadTime).longValue();
                if (ts > 0) {
                    vo.setUploadTime(LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(ts), java.time.ZoneId.systemDefault()));
                }
            }
        }
        return vo;
    }

    /**
     * SearchHitVO 列表转 FileInfoDTO 列表（v2.1 兼容）
     *
     * @param hits 命中列表
     * @return FileInfoDTO 列表
     */
    private List<FileInfoDTO> convertToFileInfoList(List<SearchHitVO> hits) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        List<FileInfoDTO> list = new ArrayList<>(hits.size());
        for (SearchHitVO hit : hits) {
            FileInfoDTO dto = new FileInfoDTO();
            dto.setId(hit.getFileId());
            dto.setFilename(hit.getFileName());
            dto.setFileType(hit.getFileType());
            dto.setFileSize(hit.getFileSize());
            dto.setTargetId(hit.getTargetId());
            dto.setCreateTime(hit.getUploadTime());
            list.add(dto);
        }
        return list;
    }

    /**
     * 构造 Milvus 元数据
     *
     * @param dto 文件索引数据
     * @return 元数据 Map
     */
    private Map<String, Object> buildMilvusMetadata(FileIndexDTO dto) {
        Map<String, Object> meta = new HashMap<>(4);
        meta.put(MilvusService.FIELD_FILE_NAME, dto.getFileName());
        meta.put(MilvusService.FIELD_FILE_SM3, dto.getFileSm3());
        meta.put(MilvusService.FIELD_TARGET_ID, dto.getTargetId());
        long uploadTs = dto.getUploadTime() == null ? 0L
                : dto.getUploadTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        meta.put(MilvusService.FIELD_UPLOAD_TIME, uploadTs);
        return meta;
    }

    /**
     * 创建或更新索引任务记录
     *
     * @param dto 文件索引数据
     * @return 索引任务实体
     */
    private SearchIndexTaskEntity upsertTask(FileIndexDTO dto) {
        SearchIndexTaskEntity task = searchIndexTaskMapper.selectOne(
                new LambdaQueryWrapper<SearchIndexTaskEntity>()
                        .eq(SearchIndexTaskEntity::getFileId, dto.getFileId()));
        LocalDateTime now = LocalDateTime.now();
        if (task == null) {
            task = new SearchIndexTaskEntity();
            task.setFileId(dto.getFileId());
            task.setFileName(dto.getFileName());
            task.setFileSm3(dto.getFileSm3());
            task.setEsIndexed(false);
            task.setMilvusIndexed(false);
            task.setIndexStatus(SearchIndexTaskEntity.STATUS_INDEXING);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            try {
                searchIndexTaskMapper.insert(task);
            } catch (Exception e) {
                // 并发插入冲突，重新查询
                task = searchIndexTaskMapper.selectOne(new LambdaQueryWrapper<SearchIndexTaskEntity>()
                        .eq(SearchIndexTaskEntity::getFileId, dto.getFileId()));
            }
        }
        if (task != null) {
            SearchIndexTaskEntity update = new SearchIndexTaskEntity();
            update.setId(task.getId());
            update.setIndexStatus(SearchIndexTaskEntity.STATUS_INDEXING);
            update.setUpdatedAt(now);
            searchIndexTaskMapper.updateById(update);
            task.setIndexStatus(SearchIndexTaskEntity.STATUS_INDEXING);
        }
        return task;
    }

    /**
     * 更新索引任务状态
     *
     * @param task     索引任务
     * @param esOk     ES 是否成功
     * @param milvusOk Milvus 是否成功
     * @param errorMsg 错误信息
     */
    private void updateTaskStatus(SearchIndexTaskEntity task, boolean esOk, boolean milvusOk, String errorMsg) {
        if (task == null) {
            return;
        }
        SearchIndexTaskEntity update = new SearchIndexTaskEntity();
        update.setId(task.getId());
        update.setEsIndexed(esOk);
        update.setMilvusIndexed(milvusOk);
        update.setUpdatedAt(LocalDateTime.now());
        if (esOk && milvusOk) {
            update.setIndexStatus(SearchIndexTaskEntity.STATUS_SUCCESS);
            update.setErrorMsg(null);
        } else if (esOk || milvusOk) {
            // 部分成功，标记为 SUCCESS 但记录错误信息
            update.setIndexStatus(SearchIndexTaskEntity.STATUS_SUCCESS);
            update.setErrorMsg(errorMsg);
        } else {
            update.setIndexStatus(SearchIndexTaskEntity.STATUS_FAILED);
            update.setErrorMsg(errorMsg);
        }
        searchIndexTaskMapper.updateById(update);
    }
}
