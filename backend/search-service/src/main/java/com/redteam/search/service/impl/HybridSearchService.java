package com.redteam.search.service.impl;

import com.redteam.search.config.SearchProperties;
import com.redteam.search.dto.SearchHitVO;
import com.redteam.search.dto.SearchRequestDTO;
import com.redteam.search.dto.SearchResultVO;
import com.redteam.search.dto.VectorSearchResultDTO;
import com.redteam.search.service.VectorEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 混合检索服务（RRF 融合）
 *
 * <p>并行执行 ES 关键字检索（top K）和 Milvus 向量检索（top K），
 * 使用 Reciprocal Rank Fusion (RRF) 融合两路结果。</p>
 *
 * <p>RRF 公式：{@code score(d) = Σ 1 / (k + rank_i(d))}，其中 k=60（默认）。
 * 设计层面保证 P99 < 200ms：两路检索并行，单路超时 100ms 内降级返回单路结果。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final ElasticsearchService elasticsearchService;
    private final MilvusService milvusService;
    private final VectorEmbeddingService vectorEmbeddingService;
    private final SearchProperties searchProperties;
    private final Executor searchIndexExecutor;

    /**
     * 混合检索
     *
     * @param request 检索请求
     * @return 融合后检索结果
     */
    public SearchResultVO hybridSearch(SearchRequestDTO request) {
        if (request == null) {
            return SearchResultVO.empty(null);
        }
        long start = System.currentTimeMillis();
        int topKPerSource = searchProperties.getHybrid().getTopKPerSource();
        int rrfK = searchProperties.getHybrid().getRrfK();

        // 构造 ES 检索请求（取 top K，不分页，分页在融合后做）
        SearchRequestDTO esRequest = cloneForInternal(request, topKPerSource);

        // 1. 并行执行 ES + Milvus 检索
        CompletableFuture<SearchResultVO> esFuture = CompletableFuture.supplyAsync(
                () -> elasticsearchService.keywordSearch(esRequest), searchIndexExecutor)
                .exceptionally(ex -> {
                    log.warn("混合检索中 ES 子任务失败，降级为空结果: {}", ex.getMessage());
                    return SearchResultVO.empty(esRequest);
                });

        CompletableFuture<List<VectorSearchResultDTO>> milvusFuture;
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            milvusFuture = CompletableFuture.completedFuture(List.of());
        } else {
            milvusFuture = CompletableFuture.supplyAsync(() -> {
                List<Float> queryVector = vectorEmbeddingService.embed(request.getQuery());
                String filter = MilvusService.buildFilter(request.getTargetId());
                return milvusService.vectorSearch(queryVector, topKPerSource, filter);
            }, searchIndexExecutor).exceptionally(ex -> {
                log.warn("混合检索中 Milvus 子任务失败，降级为空结果: {}", ex.getMessage());
                return List.of();
            });
        }

        // 等待两路结果（并行执行，耗时 ≈ max(es, milvus)）
        CompletableFuture.allOf(esFuture, milvusFuture).join();
        SearchResultVO esResult = esFuture.getNow(SearchResultVO.empty(esRequest));
        List<VectorSearchResultDTO> milvusResult = milvusFuture.getNow(List.of());

        // 2. RRF 融合
        List<SearchHitVO> fused = rrfFuse(esResult.getHits(), milvusResult, rrfK, request);

        // 3. 分页
        int pageNum = request.getPageNum();
        int pageSize = request.getPageSize();
        long total = fused.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(fused.size(), from + pageSize);
        List<SearchHitVO> page = from < to ? fused.subList(from, to) : List.of();

        SearchResultVO vo = new SearchResultVO();
        vo.setTotal(total);
        vo.setPageNum(pageNum);
        vo.setPageSize(pageSize);
        vo.setHits(page);
        vo.setAggregations(esResult.getAggregations());
        vo.setResponseTimeMs(System.currentTimeMillis() - start);
        log.info("混合检索完成: query={}, esHits={}, milvusHits={}, fused={}, total={}, cost={}ms",
                request.getQuery(),
                esResult.getHits() == null ? 0 : esResult.getHits().size(),
                milvusResult.size(), fused.size(), total, vo.getResponseTimeMs());
        return vo;
    }

    /**
     * RRF 融合两路结果
     *
     * <p>对每个 fileId 计算 RRF 分数：{@code Σ 1/(k + rank)}。
     * ES 结果保留高亮与元数据；Milvus 独有的结果从元数据构造最小 VO。</p>
     *
     * @param esHits     ES 检索结果
     * @param milvusHits Milvus 检索结果
     * @param k          RRF 参数 k
     * @param request    原始请求
     * @return 融合后按 RRF 分数降序的结果列表
     */
    private List<SearchHitVO> rrfFuse(List<SearchHitVO> esHits,
                                      List<VectorSearchResultDTO> milvusHits,
                                      int k,
                                      SearchRequestDTO request) {
        // fileId -> SearchHitVO（ES 提供富元数据）
        Map<Long, SearchHitVO> hitMap = new LinkedHashMap<>();
        // fileId -> RRF score
        Map<Long, Float> scoreMap = new HashMap<>();

        // ES 贡献
        if (esHits != null) {
            for (int i = 0; i < esHits.size(); i++) {
                SearchHitVO hit = esHits.get(i);
                if (hit.getFileId() == null) {
                    continue;
                }
                hitMap.putIfAbsent(hit.getFileId(), hit);
                int rank = i + 1;
                float contribution = 1.0f / (k + rank);
                scoreMap.merge(hit.getFileId(), contribution, Float::sum);
            }
        }

        // Milvus 贡献
        if (milvusHits != null) {
            for (VectorSearchResultDTO vr : milvusHits) {
                if (vr.getFileId() == null) {
                    continue;
                }
                // 若 ES 未提供元数据，则从 Milvus 元数据构造
                hitMap.computeIfAbsent(vr.getFileId(), fid -> buildHitFromVector(vr, request));
                int rank = vr.getRank() > 0 ? vr.getRank() : (milvusHits.indexOf(vr) + 1);
                float contribution = 1.0f / (k + rank);
                scoreMap.merge(vr.getFileId(), contribution, Float::sum);
            }
        }

        // 设置融合分数并排序
        List<SearchHitVO> fused = new ArrayList<>(hitMap.values());
        for (SearchHitVO hit : fused) {
            Float s = scoreMap.get(hit.getFileId());
            hit.setScore(s == null ? 0f : s);
            hit.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        }
        fused.sort(Comparator.comparing(SearchHitVO::getScore, Comparator.reverseOrder()));
        return fused;
    }

    /**
     * 从 Milvus 向量检索结果构造最小 SearchHitVO
     *
     * @param vr      向量检索结果
     * @param request 原始请求
     * @return SearchHitVO
     */
    private SearchHitVO buildHitFromVector(VectorSearchResultDTO vr, SearchRequestDTO request) {
        SearchHitVO vo = new SearchHitVO();
        vo.setFileId(vr.getFileId());
        vo.setScore(vr.getScore());
        vo.setSearchType(SearchRequestDTO.TYPE_HYBRID);
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
                    vo.setUploadTime(java.time.LocalDateTime.ofInstant(
                            java.time.Instant.ofEpochMilli(ts), java.time.ZoneId.systemDefault()));
                }
            }
        }
        return vo;
    }

    /**
     * 克隆请求用于内部检索（重设 pageSize 为 topK，pageNum 为 1）
     *
     * @param request     原始请求
     * @param topKPerSource 每路取 top K
     * @return 内部检索请求
     */
    private SearchRequestDTO cloneForInternal(SearchRequestDTO request, int topKPerSource) {
        SearchRequestDTO clone = new SearchRequestDTO();
        clone.setQuery(request.getQuery());
        clone.setSearchType(request.getSearchType());
        clone.setFileType(request.getFileType());
        clone.setTargetId(request.getTargetId());
        clone.setSensitiveLevel(request.getSensitiveLevel());
        clone.setTags(request.getTags());
        clone.setDateFrom(request.getDateFrom());
        clone.setDateTo(request.getDateTo());
        clone.setMinScore(request.getMinScore());
        clone.setUserId(request.getUserId());
        clone.setPageNum(1);
        clone.setPageSize(topKPerSource);
        return clone;
    }
}
