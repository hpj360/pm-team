package com.redteam.search.service;

import com.redteam.search.config.SearchProperties;
import com.redteam.search.dto.SearchHitVO;
import com.redteam.search.dto.SearchRequestDTO;
import com.redteam.search.dto.SearchResultVO;
import com.redteam.search.dto.VectorSearchResultDTO;
import com.redteam.search.service.impl.ElasticsearchService;
import com.redteam.search.service.impl.HybridSearchService;
import com.redteam.search.service.impl.MilvusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 混合检索服务单元测试
 *
 * <p>重点验证 RRF 融合逻辑、并行执行降级、分页正确性。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HybridSearchServiceTest {

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private MilvusService milvusService;

    @Mock
    private VectorEmbeddingService vectorEmbeddingService;

    @Mock
    private SearchProperties searchProperties;

    @Mock
    private Executor searchIndexExecutor;

    @InjectMocks
    private HybridSearchService hybridSearchService;

    /**
     * 初始化配置与同步执行器
     */
    @BeforeEach
    void setUp() {
        SearchProperties.Hybrid hybrid = new SearchProperties.Hybrid();
        hybrid.setRrfK(60);
        hybrid.setTopKPerSource(50);
        SearchProperties.Milvus milvus = new SearchProperties.Milvus();
        milvus.setVectorDim(768);
        when(searchProperties.getHybrid()).thenReturn(hybrid);
        when(searchProperties.getMilvus()).thenReturn(milvus);

        // 同步执行 CompletableFuture
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(searchIndexExecutor).execute(any(Runnable.class));

        when(vectorEmbeddingService.embed(anyString()))
                .thenReturn(new ArrayList<>(768));
    }

    /**
     * 构造 ES 检索结果
     *
     * @param fileIds 文件 ID 列表
     * @return SearchResultVO
     */
    private SearchResultVO buildEsResult(Long... fileIds) {
        SearchResultVO vo = new SearchResultVO();
        vo.setTotal((long) fileIds.length);
        vo.setPageNum(1);
        vo.setPageSize(50);
        List<SearchHitVO> hits = new ArrayList<>();
        for (Long id : fileIds) {
            SearchHitVO h = new SearchHitVO();
            h.setFileId(id);
            h.setFileName("file-" + id);
            h.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
            h.setScore((float) (10 - hits.size()));
            hits.add(h);
        }
        vo.setHits(hits);
        vo.setResponseTimeMs(20L);
        return vo;
    }

    /**
     * 构造 Milvus 检索结果
     *
     * @param fileIds 文件 ID 列表
     * @return 结果列表
     */
    private List<VectorSearchResultDTO> buildMilvusResult(Long... fileIds) {
        List<VectorSearchResultDTO> list = new ArrayList<>();
        for (int i = 0; i < fileIds.length; i++) {
            Map<String, Object> meta = new HashMap<>();
            meta.put(MilvusService.FIELD_FILE_NAME, "file-" + fileIds[i]);
            meta.put(MilvusService.FIELD_FILE_SM3, "sm3-" + fileIds[i]);
            meta.put(MilvusService.FIELD_TARGET_ID, 100L);
            meta.put(MilvusService.FIELD_UPLOAD_TIME, 1700000000000L);
            VectorSearchResultDTO dto = new VectorSearchResultDTO(fileIds[i], 0.9f - i * 0.1f, meta);
            dto.setRank(i + 1);
            list.add(dto);
        }
        return list;
    }

    @Test
    @DisplayName("hybridSearch: 两路结果交集 fileId 应获得更高 RRF 分数")
    void hybridSearch_overlapFileIdGetsHigherScore() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        request.setPageNum(1);
        request.setPageSize(10);

        // ES 返回 [1,2,3]，Milvus 返回 [2,3,4]；fileId=2 在 ES rank=2，Milvus rank=1
        when(elasticsearchService.keywordSearch(any())).thenReturn(buildEsResult(1L, 2L, 3L));
        when(milvusService.vectorSearch(anyList(), anyInt(), any()))
                .thenReturn(buildMilvusResult(2L, 3L, 4L));

        SearchResultVO result = hybridSearchService.hybridSearch(request);

        assertNotNull(result);
        assertEquals(5L, result.getTotal());
        assertFalse(result.getHits().isEmpty());
        // fileId=2 同时命中两路，RRF 分数应最高
        SearchHitVO top = result.getHits().get(0);
        assertEquals(2L, top.getFileId());
        assertEquals(SearchRequestDTO.TYPE_HYBRID, top.getSearchType());
        // 验证 RRF 公式: 1/(60+2) + 1/(60+1) ≈ 0.03252
        float expected = 1.0f / (60 + 2) + 1.0f / (60 + 1);
        assertEquals(expected, top.getScore(), 1e-4);
    }

    @Test
    @DisplayName("hybridSearch: 仅 ES 有结果时返回 ES 结果")
    void hybridSearch_onlyEsResults_returnsEsHits() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        request.setPageNum(1);
        request.setPageSize(10);

        when(elasticsearchService.keywordSearch(any())).thenReturn(buildEsResult(1L, 2L));
        when(milvusService.vectorSearch(anyList(), anyInt(), any()))
                .thenReturn(List.of());

        SearchResultVO result = hybridSearchService.hybridSearch(request);

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getHits().size());
        assertEquals(1L, result.getHits().get(0).getFileId());
    }

    @Test
    @DisplayName("hybridSearch: 仅 Milvus 有结果时从元数据构造 VO")
    void hybridSearch_onlyMilvusResults_buildsHitFromMeta() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        request.setPageNum(1);
        request.setPageSize(10);

        when(elasticsearchService.keywordSearch(any())).thenReturn(buildEsResult());
        when(milvusService.vectorSearch(anyList(), anyInt(), any()))
                .thenReturn(buildMilvusResult(5L, 6L));

        SearchResultVO result = hybridSearchService.hybridSearch(request);

        assertEquals(2L, result.getTotal());
        SearchHitVO hit = result.getHits().get(0);
        assertEquals(5L, hit.getFileId());
        assertEquals("file-5", hit.getFileName());
        assertEquals(100L, hit.getTargetId());
        assertNotNull(hit.getUploadTime());
    }

    @Test
    @DisplayName("hybridSearch: request 为 null 返回空结果")
    void hybridSearch_nullRequest_returnsEmpty() {
        SearchResultVO result = hybridSearchService.hybridSearch(null);
        assertNotNull(result);
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("hybridSearch: query 为空时跳过 Milvus 子任务")
    void hybridSearch_blankQuery_skipsMilvus() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("");
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        request.setPageNum(1);
        request.setPageSize(10);

        when(elasticsearchService.keywordSearch(any())).thenReturn(buildEsResult(1L));

        SearchResultVO result = hybridSearchService.hybridSearch(request);

        assertEquals(1L, result.getTotal());
        verify(milvusService, never()).vectorSearch(anyList(), anyInt(), any());
    }

    @Test
    @DisplayName("hybridSearch: ES 子任务异常时降级为空，不抛异常")
    void hybridSearch_esThrows_degradesGracefully() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        request.setPageNum(1);
        request.setPageSize(10);

        when(elasticsearchService.keywordSearch(any())).thenThrow(new RuntimeException("ES down"));
        when(milvusService.vectorSearch(anyList(), anyInt(), any()))
                .thenReturn(buildMilvusResult(7L));

        SearchResultVO result = hybridSearchService.hybridSearch(request);

        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(7L, result.getHits().get(0).getFileId());
    }

    @Test
    @DisplayName("hybridSearch: 分页正确性，第二页返回正确子集")
    void hybridSearch_pagination_returnsCorrectSubset() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        request.setPageNum(2);
        request.setPageSize(2);

        when(elasticsearchService.keywordSearch(any())).thenReturn(buildEsResult(1L, 2L, 3L, 4L, 5L));
        when(milvusService.vectorSearch(anyList(), anyInt(), any()))
                .thenReturn(List.of());

        SearchResultVO result = hybridSearchService.hybridSearch(request);

        assertEquals(5L, result.getTotal());
        assertEquals(2, result.getHits().size());
        // 第二页应返回第 3、4 条
        assertEquals(3L, result.getHits().get(0).getFileId());
        assertEquals(4L, result.getHits().get(1).getFileId());
    }

    @Test
    @DisplayName("hybridSearch: 响应时间被记录且非负")
    void hybridSearch_responseTimeRecorded() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        request.setPageNum(1);
        request.setPageSize(10);

        when(elasticsearchService.keywordSearch(any())).thenReturn(buildEsResult(1L));
        when(milvusService.vectorSearch(anyList(), anyInt(), any()))
                .thenReturn(buildMilvusResult(1L));

        SearchResultVO result = hybridSearchService.hybridSearch(request);

        assertNotNull(result.getResponseTimeMs());
        assertTrue(result.getResponseTimeMs() >= 0);
    }

    @Test
    @DisplayName("hybridSearch: 保留 ES 聚合结果")
    void hybridSearch_preservesAggregations() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        request.setPageNum(1);
        request.setPageSize(10);

        SearchResultVO esResult = buildEsResult(1L);
        Map<String, Object> aggs = new HashMap<>();
        aggs.put("fileType", "聚合值");
        esResult.setAggregations(aggs);
        when(elasticsearchService.keywordSearch(any())).thenReturn(esResult);
        when(milvusService.vectorSearch(anyList(), anyInt(), any()))
                .thenReturn(List.of());

        SearchResultVO result = hybridSearchService.hybridSearch(request);

        assertNotNull(result.getAggregations());
        assertEquals("聚合值", result.getAggregations().get("fileType"));
    }

    @Test
    @DisplayName("hybridSearch: Milvus 子任务异常时降级为空，不抛异常")
    void hybridSearch_milvusThrows_degradesGracefully() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_HYBRID);
        request.setPageNum(1);
        request.setPageSize(10);

        when(elasticsearchService.keywordSearch(any())).thenReturn(buildEsResult(1L));
        when(milvusService.vectorSearch(anyList(), anyInt(), any()))
                .thenThrow(new RuntimeException("Milvus down"));

        SearchResultVO result = hybridSearchService.hybridSearch(request);

        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1L, result.getHits().get(0).getFileId());
    }
}
