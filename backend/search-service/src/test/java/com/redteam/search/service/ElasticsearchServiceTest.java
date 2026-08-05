package com.redteam.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import com.redteam.search.config.SearchProperties;
import com.redteam.search.dto.FileIndexDTO;
import com.redteam.search.dto.SearchRequestDTO;
import com.redteam.search.dto.SearchResultVO;
import com.redteam.search.service.impl.ElasticsearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Elasticsearch 检索服务单元测试
 *
 * <p>使用 mock 的 ElasticsearchClient 验证关键字检索、聚合、建议与异常降级。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ElasticsearchServiceTest {

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @Mock
    private SearchProperties searchProperties;

    @InjectMocks
    private ElasticsearchService elasticsearchService;

    /**
     * 初始化配置
     */
    @BeforeEach
    void setUp() {
        SearchProperties.Es es = new SearchProperties.Es();
        es.setIndexName("redhead-files");
        es.setShards(3);
        es.setReplicas(1);
        when(searchProperties.getEs()).thenReturn(es);
    }

    @Test
    @DisplayName("keywordSearch: 返回命中结果与分页信息")
    void keywordSearch_returnsHits() throws Exception {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
        request.setPageNum(1);
        request.setPageSize(10);

        // mock ES 检索响应
        Map<String, Object> source = new HashMap<>();
        source.put("fileId", 1L);
        source.put("fileName", "测试文件.pdf");
        source.put("fileType", "pdf");
        source.put("fileSize", 1024L);

        Hit<Map> hit = new Hit.Builder<Map>()
                .id("1").index("redhead-files").source(source).score(2.5).build();
        HitsMetadata<Map> hitsMetadata = new HitsMetadata.Builder<Map>()
                .hits(List.of(hit)).total(t -> t.value(1L).relation(TotalHitsRelation.Eq)).build();

        @SuppressWarnings("unchecked")
        SearchResponse<Map> response = (SearchResponse<Map>) mock(SearchResponse.class);
        when(response.hits()).thenReturn(hitsMetadata);
        when(response.aggregations()).thenReturn(Map.of());
        when(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .thenReturn(response);

        SearchResultVO result = elasticsearchService.keywordSearch(request);

        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getHits().size());
        assertEquals(1L, result.getHits().get(0).getFileId());
        assertEquals("测试文件.pdf", result.getHits().get(0).getFileName());
        assertEquals("pdf", result.getHits().get(0).getFileType());
        assertEquals(SearchRequestDTO.TYPE_KEYWORD, result.getHits().get(0).getSearchType());
        assertTrue(result.getResponseTimeMs() >= 0);
    }

    @Test
    @DisplayName("keywordSearch: 异常时降级返回空结果")
    void keywordSearch_exceptionReturnsEmpty() throws Exception {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
        request.setPageNum(1);
        request.setPageSize(10);

        when(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .thenThrow(new RuntimeException("ES 连接失败"));

        SearchResultVO result = elasticsearchService.keywordSearch(request);

        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getHits().isEmpty());
    }

    @Test
    @DisplayName("keywordSearch: request 为 null 返回空结果")
    void keywordSearch_nullRequestReturnsEmpty() {
        SearchResultVO result = elasticsearchService.keywordSearch(null);
        assertNotNull(result);
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("keywordSearch: 带过滤器构造 bool query")
    void keywordSearch_withFilters() throws Exception {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("关键字");
        request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
        request.setFileType("pdf");
        request.setTargetId(100L);
        request.setSensitiveLevel(2);
        request.setTags(List.of("标签1"));
        request.setPageNum(1);
        request.setPageSize(5);

        @SuppressWarnings("unchecked")
        SearchResponse<Map> response = (SearchResponse<Map>) mock(SearchResponse.class);
        HitsMetadata<Map> emptyHits = new HitsMetadata.Builder<Map>()
                .hits(List.of()).total(t -> t.value(0L).relation(TotalHitsRelation.Eq)).build();
        when(response.hits()).thenReturn(emptyHits);
        when(response.aggregations()).thenReturn(Map.of());
        when(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .thenReturn(response);

        SearchResultVO result = elasticsearchService.keywordSearch(request);

        assertNotNull(result);
        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("getSuggestions: 空前缀返回空列表")
    void getSuggestions_blankPrefixReturnsEmpty() {
        List<String> result = elasticsearchService.getSuggestions("", 10);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getSuggestions: 异常时返回空列表")
    void getSuggestions_exceptionReturnsEmpty() throws Exception {
        when(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .thenThrow(new RuntimeException("ES 错误"));
        List<String> result = elasticsearchService.getSuggestions("测", 10);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAggregations: 异常时返回空 Map")
    void getAggregations_exceptionReturnsEmpty() throws Exception {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setQuery("测试");
        request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);

        when(elasticsearchClient.search(any(co.elastic.clients.elasticsearch.core.SearchRequest.class), eq(Map.class)))
                .thenThrow(new RuntimeException("ES 错误"));

        Map<String, Object> result = elasticsearchService.getAggregations(request);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAggregations: request 为 null 返回空 Map")
    void getAggregations_nullRequestReturnsEmpty() {
        Map<String, Object> result = elasticsearchService.getAggregations(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("indexFile: fileId 为 null 抛业务异常")
    void indexFile_nullFileIdThrows() {
        FileIndexDTO dto = new FileIndexDTO();
        assertThrows(com.redteam.common.exception.BusinessException.class,
                () -> elasticsearchService.indexFile(dto));
    }

    @Test
    @DisplayName("deleteDocument: fileId 为 null 直接返回")
    void deleteDocument_nullFileIdReturns() {
        assertDoesNotThrow(() -> elasticsearchService.deleteDocument(null));
    }

    @Test
    @DisplayName("createIndexIfNotExists: 索引已存在时跳过创建")
    void createIndexIfNotExists_indexExistsSkips() throws Exception {
        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        BooleanResponse existsResponse = mock(BooleanResponse.class);
        when(elasticsearchClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(java.util.function.Function.class))).thenReturn(existsResponse);
        when(existsResponse.value()).thenReturn(true);

        elasticsearchService.createIndexIfNotExists();

        verify(indicesClient, never()).create(any(java.util.function.Function.class));
    }
}
