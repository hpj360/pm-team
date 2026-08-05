package com.redteam.search.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.api.dto.FileSearchDTO;
import com.redteam.common.exception.GlobalExceptionHandler;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.ResultCode;
import com.redteam.search.dto.FileIndexDTO;
import com.redteam.search.dto.SearchHitVO;
import com.redteam.search.dto.SearchHistoryVO;
import com.redteam.search.dto.SearchRequestDTO;
import com.redteam.search.dto.SearchResultVO;
import com.redteam.search.service.FileSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件检索控制器单元测试
 *
 * <p>使用 MockMvc（standalone 模式）对 {@link FileSearchController} 进行测试，
 * 覆盖 v2.5 新增接口（统一检索 / 关键字 / 向量 / 混合 / 热词 / 历史 / 聚合 / 索引管理）
 * 与 v2.1 既有接口（query / semantic / highlight / suggest），
 * 验证路由绑定、请求参数解析、响应序列化、searchType 自动注入及与 Service 的交互。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("文件检索控制器测试")
class FileSearchControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private FileSearchService fileSearchService;

    @InjectMocks
    private FileSearchController fileSearchController;

    @BeforeEach
    void setUp() {
        // 注册 GlobalExceptionHandler 以模拟生产环境的异常处理行为
        // （standalone 模式默认不加载 @RestControllerAdvice）
        mockMvc = MockMvcBuilders.standaloneSetup(fileSearchController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ==================== 测试数据构造 ====================

    /**
     * 构造检索请求
     */
    private SearchRequestDTO buildRequest(String searchType, String query) {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setSearchType(searchType);
        request.setQuery(query);
        request.setPageNum(1);
        request.setPageSize(10);
        return request;
    }

    /**
     * 构造检索命中 VO
     */
    private SearchHitVO buildHit() {
        SearchHitVO hit = new SearchHitVO();
        hit.setFileId(1001L);
        hit.setFileName("渗透测试报告.pdf");
        hit.setFileType("pdf");
        hit.setFileSize(2048L);
        hit.setFileSm3("sm3-hash-xxxx");
        hit.setScore(0.9876f);
        hit.setSearchType("HYBRID");
        hit.setUploadTime(LocalDateTime.now());
        hit.setTargetId(2001L);
        hit.setTags(List.of("web", "内网"));
        return hit;
    }

    /**
     * 构造检索结果 VO
     */
    private SearchResultVO buildResult() {
        SearchResultVO vo = new SearchResultVO();
        vo.setTotal(1L);
        vo.setPageNum(1);
        vo.setPageSize(10);
        vo.setHits(List.of(buildHit()));
        vo.setResponseTimeMs(45L);
        return vo;
    }

    /**
     * 构造检索历史 VO
     */
    private SearchHistoryVO buildHistory() {
        SearchHistoryVO vo = new SearchHistoryVO();
        vo.setId(1L);
        vo.setUserId(1001L);
        vo.setSearchType("HYBRID");
        vo.setQueryText("渗透测试");
        vo.setResultCount(5);
        vo.setResponseTimeMs(38L);
        vo.setCreatedAt(LocalDateTime.now());
        return vo;
    }

    /**
     * 构造文件信息 DTO（v2.1 既有接口用）
     */
    private FileInfoDTO buildFileInfo() {
        FileInfoDTO info = new FileInfoDTO();
        info.setId(1001L);
        info.setFilename("渗透测试报告.pdf");
        info.setFileType("pdf");
        info.setFileSize(2048L);
        info.setSensitiveLevel(2);
        info.setIsPublic(0);
        info.setParseStatus(2);
        info.setIndexStatus(2);
        info.setCreateTime(LocalDateTime.now());
        return info;
    }

    /**
     * 构造文件索引 DTO
     */
    private FileIndexDTO buildIndexDTO() {
        FileIndexDTO dto = new FileIndexDTO();
        dto.setFileId(1001L);
        dto.setFileName("渗透测试报告.pdf");
        dto.setFileType("pdf");
        dto.setFileSize(2048L);
        dto.setFileSm3("sm3-hash-xxxx");
        dto.setTextContent("这是一份渗透测试报告的文本内容");
        dto.setTargetId(2001L);
        dto.setTags(List.of("web", "内网"));
        dto.setSensitiveLevel(2);
        dto.setIsPublic(0);
        dto.setUploadTime(LocalDateTime.now());
        return dto;
    }

    // ==================== v2.5 新增：统一检索接口 ====================

    @Test
    @DisplayName("POST /search - 统一检索（HYBRID）成功")
    void search_unifiedHybridSuccess() throws Exception {
        when(fileSearchService.search(any(SearchRequestDTO.class))).thenReturn(buildResult());

        mockMvc.perform(post("/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("HYBRID", "渗透测试"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.hits[0].fileId").value(1001))
                .andExpect(jsonPath("$.data.hits[0].fileName").value("渗透测试报告.pdf"))
                .andExpect(jsonPath("$.data.hits[0].score").value(0.9876))
                .andExpect(jsonPath("$.data.responseTimeMs").value(45));

        verify(fileSearchService, times(1)).search(any(SearchRequestDTO.class));
    }

    @Test
    @DisplayName("POST /search - 参数校验失败（pageNum < 1）返回 400")
    void search_invalidPageNum() throws Exception {
        SearchRequestDTO request = buildRequest("KEYWORD", "测试");
        request.setPageNum(0);

        mockMvc.perform(post("/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(fileSearchService, never()).search(any(SearchRequestDTO.class));
    }

    @Test
    @DisplayName("POST /search - 参数校验失败（pageSize > 100）返回 400")
    void search_invalidPageSize() throws Exception {
        SearchRequestDTO request = buildRequest("KEYWORD", "测试");
        request.setPageSize(200);

        mockMvc.perform(post("/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(fileSearchService, never()).search(any(SearchRequestDTO.class));
    }

    @Test
    @DisplayName("POST /search/keyword - 关键字检索自动注入 searchType=KEYWORD")
    void keywordSearch_injectsSearchType() throws Exception {
        when(fileSearchService.keywordSearch(any(SearchRequestDTO.class))).thenReturn(buildResult());

        // 入参不显式指定 searchType，由 Controller 注入
        SearchRequestDTO request = buildRequest("VECTOR", "渗透测试");

        mockMvc.perform(post("/search/keyword")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1));

        ArgumentCaptor<SearchRequestDTO> captor = ArgumentCaptor.forClass(SearchRequestDTO.class);
        verify(fileSearchService, times(1)).keywordSearch(captor.capture());
        assertEquals(SearchRequestDTO.TYPE_KEYWORD, captor.getValue().getSearchType(),
                "Controller 应将 searchType 覆盖为 KEYWORD");
    }

    @Test
    @DisplayName("POST /search/vector - 向量检索自动注入 searchType=VECTOR")
    void vectorSearch_injectsSearchType() throws Exception {
        when(fileSearchService.vectorSearch(any(SearchRequestDTO.class))).thenReturn(buildResult());

        SearchRequestDTO request = buildRequest("KEYWORD", "渗透测试");

        mockMvc.perform(post("/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.hits[0].fileId").value(1001));

        ArgumentCaptor<SearchRequestDTO> captor = ArgumentCaptor.forClass(SearchRequestDTO.class);
        verify(fileSearchService, times(1)).vectorSearch(captor.capture());
        assertEquals(SearchRequestDTO.TYPE_VECTOR, captor.getValue().getSearchType(),
                "Controller 应将 searchType 覆盖为 VECTOR");
    }

    @Test
    @DisplayName("POST /search/hybrid - 混合检索（RRF 融合）自动注入 searchType=HYBRID")
    void hybridSearch_injectsSearchType() throws Exception {
        SearchResultVO result = buildResult();
        result.getHits().get(0).setScore(0.0328f); // RRF 融合分数
        when(fileSearchService.hybridSearch(any(SearchRequestDTO.class))).thenReturn(result);

        SearchRequestDTO request = buildRequest("KEYWORD", "渗透测试");

        mockMvc.perform(post("/search/hybrid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.hits[0].score").value(0.0328));

        ArgumentCaptor<SearchRequestDTO> captor = ArgumentCaptor.forClass(SearchRequestDTO.class);
        verify(fileSearchService, times(1)).hybridSearch(captor.capture());
        assertEquals(SearchRequestDTO.TYPE_HYBRID, captor.getValue().getSearchType(),
                "Controller 应将 searchType 覆盖为 HYBRID");
    }

    @Test
    @DisplayName("POST /search/hybrid - 混合检索空结果返回")
    void hybridSearch_emptyResult() throws Exception {
        when(fileSearchService.hybridSearch(any(SearchRequestDTO.class)))
                .thenReturn(SearchResultVO.empty(buildRequest("HYBRID", "空")));

        mockMvc.perform(post("/search/hybrid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("HYBRID", "不存在的内容"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.hits").isArray());

        verify(fileSearchService, times(1)).hybridSearch(any(SearchRequestDTO.class));
    }

    // ==================== v2.5 新增：行为分析与聚合 ====================

    @Test
    @DisplayName("GET /search/hot-words - 获取热门检索词（默认 limit=10）")
    void getHotWords_defaultLimit() throws Exception {
        when(fileSearchService.getHotWords(10)).thenReturn(List.of("渗透测试", "内网扫描", "提权"));

        mockMvc.perform(get("/search/hot-words"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0]").value("渗透测试"))
                .andExpect(jsonPath("$.data[1]").value("内网扫描"))
                .andExpect(jsonPath("$.data[2]").value("提权"));

        verify(fileSearchService, times(1)).getHotWords(10);
    }

    @Test
    @DisplayName("GET /search/hot-words?limit=5 - 自定义 limit")
    void getHotWords_customLimit() throws Exception {
        when(fileSearchService.getHotWords(5)).thenReturn(List.of("渗透测试", "内网扫描"));

        mockMvc.perform(get("/search/hot-words").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.length()").value(2));

        verify(fileSearchService, times(1)).getHotWords(5);
    }

    @Test
    @DisplayName("GET /search/history?userId=1001 - 获取检索历史")
    void getSearchHistory_success() throws Exception {
        when(fileSearchService.getSearchHistory(1001L, 10)).thenReturn(List.of(buildHistory()));

        mockMvc.perform(get("/search/history").param("userId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(1001))
                .andExpect(jsonPath("$.data[0].searchType").value("HYBRID"))
                .andExpect(jsonPath("$.data[0].queryText").value("渗透测试"))
                .andExpect(jsonPath("$.data[0].resultCount").value(5));

        verify(fileSearchService, times(1)).getSearchHistory(1001L, 10);
    }

    @Test
    @DisplayName("GET /search/history?userId=1001&limit=20 - 自定义 limit")
    void getSearchHistory_customLimit() throws Exception {
        when(fileSearchService.getSearchHistory(eq(1001L), eq(20))).thenReturn(List.of());

        mockMvc.perform(get("/search/history")
                        .param("userId", "1001")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(fileSearchService, times(1)).getSearchHistory(1001L, 20);
    }

    @Test
    @DisplayName("POST /search/aggregations - 获取聚合结果")
    void getAggregations_success() throws Exception {
        Map<String, Object> aggregations = new HashMap<>();
        aggregations.put("fileType", List.of(Map.of("key", "pdf", "count", 5)));
        aggregations.put("sensitiveLevel", List.of(Map.of("key", 2, "count", 3)));
        when(fileSearchService.getAggregations(any(SearchRequestDTO.class))).thenReturn(aggregations);

        mockMvc.perform(post("/search/aggregations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("KEYWORD", "渗透测试"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.fileType[0].key").value("pdf"))
                .andExpect(jsonPath("$.data.fileType[0].count").value(5))
                .andExpect(jsonPath("$.data.sensitiveLevel[0].key").value(2))
                .andExpect(jsonPath("$.data.sensitiveLevel[0].count").value(3));

        verify(fileSearchService, times(1)).getAggregations(any(SearchRequestDTO.class));
    }

    // ==================== v2.5 新增：索引管理 ====================

    @Test
    @DisplayName("POST /search/index/{fileId} - 手动索引文件成功")
    void indexFile_byFileId() throws Exception {
        when(fileSearchService.indexFile(anyLong())).thenReturn(true);

        mockMvc.perform(post("/search/index/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));

        verify(fileSearchService, times(1)).indexFile(1001L);
    }

    @Test
    @DisplayName("DELETE /search/index/{fileId} - 删除索引成功")
    void deleteIndex_success() throws Exception {
        doNothing().when(fileSearchService).deleteIndex(anyLong());

        mockMvc.perform(delete("/search/index/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));

        verify(fileSearchService, times(1)).deleteIndex(1001L);
    }

    @Test
    @DisplayName("POST /search/reindex - 触发全量重建索引")
    void reindex_success() throws Exception {
        doNothing().when(fileSearchService).reindexAll();

        mockMvc.perform(post("/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));

        verify(fileSearchService, times(1)).reindexAll();
    }

    @Test
    @DisplayName("POST /search/index - 索引文件（完整元数据）成功")
    void indexFileWithMeta_success() throws Exception {
        doNothing().when(fileSearchService).indexFile(any(FileIndexDTO.class));

        mockMvc.perform(post("/search/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildIndexDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));

        ArgumentCaptor<FileIndexDTO> captor = ArgumentCaptor.forClass(FileIndexDTO.class);
        verify(fileSearchService, times(1)).indexFile(captor.capture());
        assertEquals(1001L, captor.getValue().getFileId());
        assertEquals("渗透测试报告.pdf", captor.getValue().getFileName());
        assertEquals("pdf", captor.getValue().getFileType());
        assertNotNull(captor.getValue().getTextContent());
    }

    @Test
    @DisplayName("POST /search/index - 索引文件 DTO 透传完整性（tags / sensitiveLevel / targetId）")
    void indexFileWithMeta_payloadIntegrity() throws Exception {
        doNothing().when(fileSearchService).indexFile(any(FileIndexDTO.class));

        FileIndexDTO dto = buildIndexDTO();

        mockMvc.perform(post("/search/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        ArgumentCaptor<FileIndexDTO> captor = ArgumentCaptor.forClass(FileIndexDTO.class);
        verify(fileSearchService, times(1)).indexFile(captor.capture());
        FileIndexDTO captured = captor.getValue();
        assertEquals(2001L, captured.getTargetId());
        assertEquals(2, captured.getTags().size());
        assertEquals(2, captured.getSensitiveLevel());
        assertEquals(0, captured.getIsPublic());
    }

    // ==================== v2.1 既有接口（向后兼容） ====================

    @Test
    @DisplayName("POST /search/query - 全文检索（v2.1）成功")
    void search_v21QuerySuccess() throws Exception {
        PageResult<FileInfoDTO> pageResult = PageResult.of(1L, 10L, 1L, List.of(buildFileInfo()));
        when(fileSearchService.search(any(FileSearchDTO.class))).thenReturn(pageResult);

        FileSearchDTO searchDTO = new FileSearchDTO();
        searchDTO.setKeyword("渗透测试");
        searchDTO.setCurrent(1);
        searchDTO.setSize(10);

        mockMvc.perform(post("/search/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(searchDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(1001))
                .andExpect(jsonPath("$.data.records[0].filename").value("渗透测试报告.pdf"));

        verify(fileSearchService, times(1)).search(any(FileSearchDTO.class));
    }

    @Test
    @DisplayName("GET /search/semantic - 语义搜索（v2.1）成功")
    void semanticSearch_success() throws Exception {
        when(fileSearchService.semanticSearch(eq("渗透测试"), eq(0.7), eq(10)))
                .thenReturn(List.of(buildFileInfo()));

        mockMvc.perform(get("/search/semantic")
                        .param("query", "渗透测试"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0].id").value(1001))
                .andExpect(jsonPath("$.data[0].filename").value("渗透测试报告.pdf"));

        verify(fileSearchService, times(1)).semanticSearch("渗透测试", 0.7, 10);
    }

    @Test
    @DisplayName("GET /search/semantic?similarityThreshold=0.8&size=20 - 自定义参数")
    void semanticSearch_customParams() throws Exception {
        when(fileSearchService.semanticSearch(eq("提权"), eq(0.8), eq(20)))
                .thenReturn(List.of());

        mockMvc.perform(get("/search/semantic")
                        .param("query", "提权")
                        .param("similarityThreshold", "0.8")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.length()").value(0));

        verify(fileSearchService, times(1)).semanticSearch("提权", 0.8, 20);
    }

    @Test
    @DisplayName("GET /search/highlight - 高亮检索（v2.1）成功")
    void searchWithHighlight_success() throws Exception {
        PageResult<FileInfoDTO> pageResult = PageResult.of(1L, 10L, 1L, List.of(buildFileInfo()));
        when(fileSearchService.searchWithHighlight(eq("渗透"), eq(1), eq(10))).thenReturn(pageResult);

        mockMvc.perform(get("/search/highlight")
                        .param("keyword", "渗透")
                        .param("current", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(1001));

        verify(fileSearchService, times(1)).searchWithHighlight("渗透", 1, 10);
    }

    @Test
    @DisplayName("GET /search/highlight - 使用默认分页参数")
    void searchWithHighlight_defaultPaging() throws Exception {
        when(fileSearchService.searchWithHighlight(eq("渗透"), eq(1), eq(10)))
                .thenReturn(PageResult.empty());

        mockMvc.perform(get("/search/highlight").param("keyword", "渗透"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.total").value(0));

        verify(fileSearchService, times(1)).searchWithHighlight("渗透", 1, 10);
    }

    @Test
    @DisplayName("GET /search/suggest - 获取搜索建议（v2.1）成功")
    void getSuggestions_success() throws Exception {
        when(fileSearchService.getSuggestions(eq("渗"), eq(10)))
                .thenReturn(List.of("渗透测试", "渗透攻击", "内网渗透"));

        mockMvc.perform(get("/search/suggest").param("prefix", "渗"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[0]").value("渗透测试"))
                .andExpect(jsonPath("$.data[1]").value("渗透攻击"))
                .andExpect(jsonPath("$.data[2]").value("内网渗透"));

        verify(fileSearchService, times(1)).getSuggestions("渗", 10);
    }

    @Test
    @DisplayName("GET /search/suggest?size=5 - 自定义返回数量")
    void getSuggestions_customSize() throws Exception {
        when(fileSearchService.getSuggestions(eq("渗"), eq(5)))
                .thenReturn(List.of("渗透测试"));

        mockMvc.perform(get("/search/suggest")
                        .param("prefix", "渗")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(fileSearchService, times(1)).getSuggestions("渗", 5);
    }

    // ==================== 异常路径与边界 ====================

    @Test
    @DisplayName("POST /search - Service 抛出异常时由 GlobalExceptionHandler 兜底返回 500")
    void search_serviceThrowsException() throws Exception {
        when(fileSearchService.search(any(SearchRequestDTO.class)))
                .thenThrow(new RuntimeException("ES 不可用"));

        mockMvc.perform(post("/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest("KEYWORD", "测试"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ResultCode.INTERNAL_SERVER_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后再试"));

        verify(fileSearchService, times(1)).search(any(SearchRequestDTO.class));
    }

    @Test
    @DisplayName("POST /search/index - 空请求体时 Controller 仍调用 Service（无 @Valid）")
    void indexFileWithMeta_emptyBody() throws Exception {
        doNothing().when(fileSearchService).indexFile(any(FileIndexDTO.class));

        mockMvc.perform(post("/search/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));

        verify(fileSearchService, times(1)).indexFile(any(FileIndexDTO.class));
    }

    @Test
    @DisplayName("GET /search/hot-words?limit=0 - limit 为 0 时仍透传给 Service")
    void getHotWords_zeroLimit() throws Exception {
        when(fileSearchService.getHotWords(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/search/hot-words").param("limit", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));

        verify(fileSearchService, times(1)).getHotWords(0);
    }
}
