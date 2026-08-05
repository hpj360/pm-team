package com.redteam.search.service.impl;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import com.redteam.search.config.SearchProperties;
import com.redteam.search.dto.SearchRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 布尔组合检索（AND/OR/NOT）与二次检索单元测试
 *
 * <p>直接调用 {@link ElasticsearchService#buildKeywordQuery(SearchRequestDTO)}
 * （package-private）验证返回 Query 对象的子句结构，覆盖：
 * <ul>
 *   <li>AND/OR/NOT 单一与组合场景</li>
 *   <li>不同字段映射（fileName/textContent/tags/fileType）</li>
 *   <li>二次检索 refineFileIds / refineQuery 行为</li>
 *   <li>无布尔条件时的向后兼容</li>
 * </ul>
 * </p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BooleanSearchTest {

    @Mock
    private co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient;

    @Mock
    private SearchProperties searchProperties;

    @InjectMocks
    private ElasticsearchService elasticsearchService;

    /**
     * 初始化 ES 索引配置
     */
    @BeforeEach
    void setUp() {
        SearchProperties.Es es = new SearchProperties.Es();
        es.setIndexName("redhead-files");
        es.setShards(3);
        es.setReplicas(1);
        when(searchProperties.getEs()).thenReturn(es);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 AND/OR/NOT 条件
     */
    private SearchRequestDTO.BooleanCondition cond(String logic, String field, String value) {
        SearchRequestDTO.BooleanCondition c = new SearchRequestDTO.BooleanCondition();
        c.setLogic(logic);
        c.setField(field);
        c.setValue(value);
        return c;
    }

    /**
     * 基础请求（仅 pageNum/pageSize，不设置 query/过滤条件）
     */
    private SearchRequestDTO baseRequest() {
        SearchRequestDTO request = new SearchRequestDTO();
        request.setSearchType(SearchRequestDTO.TYPE_KEYWORD);
        request.setPageNum(1);
        request.setPageSize(10);
        return request;
    }

    /**
     * 从 Query 提取 BoolQuery（断言非 null 且为 bool 类型）
     */
    private BoolQuery asBool(Query query) {
        assertNotNull(query, "Query 不能为空");
        assertTrue(query.isBool(), "Query 应为 bool 类型，实际: " + query._kind());
        return query.bool();
    }

    /**
     * 找到列表中第一个 multi_match 查询
     */
    private MultiMatchQuery firstMultiMatch(List<Query> queries) {
        return queries.stream()
                .filter(Query::isMultiMatch)
                .map(Query::multiMatch)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 multi_match 子句"));
    }

    /**
     * 找到列表中第一个 match 查询
     */
    private MatchQuery firstMatch(List<Query> queries) {
        return queries.stream()
                .filter(Query::isMatch)
                .map(Query::match)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 match 子句"));
    }

    /**
     * 找到列表中第一个 term 查询
     */
    private TermQuery firstTerm(List<Query> queries) {
        return queries.stream()
                .filter(Query::isTerm)
                .map(Query::term)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 term 子句"));
    }

    /**
     * 找到列表中第一个 terms 查询
     */
    private TermsQuery firstTerms(List<Query> queries) {
        return queries.stream()
                .filter(Query::isTerms)
                .map(Query::terms)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到 terms 子句"));
    }

    // ==================== AND/OR/NOT 子句构建 ====================

    @Test
    @DisplayName("AND 条件: 构建正确的 must 子句")
    void testBooleanCondition_And() {
        SearchRequestDTO request = baseRequest();
        request.setBooleanConditions(List.of(
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "fileName", "报告")
        ));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        assertEquals(1, boolQuery.must().size(), "AND 应产生 1 个 must 子句");
        assertTrue(boolQuery.must().get(0).isMatch(), "fileName 字段应为 match 查询");
        MatchQuery matchQuery = firstMatch(boolQuery.must());
        assertEquals("fileName", matchQuery.field());
        assertEquals("报告", matchQuery.query().stringValue());
        assertTrue(boolQuery.should().isEmpty(), "AND 不应产生 should 子句");
        assertTrue(boolQuery.mustNot().isEmpty(), "AND 不应产生 mustNot 子句");
    }

    @Test
    @DisplayName("OR 条件: 构建正确的 should 子句并设置 minimumShouldMatch")
    void testBooleanCondition_Or() {
        SearchRequestDTO request = baseRequest();
        request.setBooleanConditions(List.of(
                cond(SearchRequestDTO.BooleanCondition.LOGIC_OR, "textContent", "摘要")
        ));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        assertEquals(1, boolQuery.should().size(), "OR 应产生 1 个 should 子句");
        assertTrue(boolQuery.should().get(0).isMatch(), "textContent 字段应为 match 查询");
        MatchQuery matchQuery = firstMatch(boolQuery.should());
        assertEquals("textContent", matchQuery.field());
        assertEquals("摘要", matchQuery.query().stringValue());
        assertEquals("1", boolQuery.minimumShouldMatch(), "OR 应设置 minimumShouldMatch=1");
        assertTrue(boolQuery.must().isEmpty(), "OR 不应产生 must 子句（无 query 字段）");
    }

    @Test
    @DisplayName("NOT 条件: 构建正确的 mustNot 子句")
    void testBooleanCondition_Not() {
        SearchRequestDTO request = baseRequest();
        request.setBooleanConditions(List.of(
                cond(SearchRequestDTO.BooleanCondition.LOGIC_NOT, "fileType", "tmp")
        ));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        assertEquals(1, boolQuery.mustNot().size(), "NOT 应产生 1 个 mustNot 子句");
        assertTrue(boolQuery.mustNot().get(0).isTerm(), "fileType 字段应为 term 查询");
        TermQuery termQuery = firstTerm(boolQuery.mustNot());
        assertEquals("fileType", termQuery.field());
        assertEquals("tmp", termQuery.value().stringValue());
        assertTrue(boolQuery.should().isEmpty(), "NOT 不应产生 should 子句");
        assertNull(boolQuery.minimumShouldMatch(), "NOT 不应设置 minimumShouldMatch");
    }

    @Test
    @DisplayName("多个 AND 条件: 全部进入 must 子句")
    void testBooleanCondition_MultipleAnd() {
        SearchRequestDTO request = baseRequest();
        request.setBooleanConditions(List.of(
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "fileName", "报告"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "textContent", "总结"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "fileType", "pdf")
        ));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        assertEquals(3, boolQuery.must().size(), "3 个 AND 条件应产生 3 个 must 子句");
        assertTrue(boolQuery.should().isEmpty());
        assertTrue(boolQuery.mustNot().isEmpty());
    }

    @Test
    @DisplayName("AND + OR 混合: must 与 should 共存，minimumShouldMatch=1")
    void testBooleanCondition_AndOrMix() {
        SearchRequestDTO request = baseRequest();
        request.setBooleanConditions(List.of(
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "fileName", "报告"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_OR, "textContent", "草稿")
        ));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        assertEquals(1, boolQuery.must().size(), "AND 应产生 1 个 must 子句");
        assertEquals(1, boolQuery.should().size(), "OR 应产生 1 个 should 子句");
        assertEquals("1", boolQuery.minimumShouldMatch(), "应设置 minimumShouldMatch=1");
        assertTrue(boolQuery.mustNot().isEmpty());
    }

    @Test
    @DisplayName("AND + NOT 混合: must 与 mustNot 共存")
    void testBooleanCondition_AndNotMix() {
        SearchRequestDTO request = baseRequest();
        request.setBooleanConditions(List.of(
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "fileName", "报告"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_NOT, "tags", "已归档")
        ));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        assertEquals(1, boolQuery.must().size(), "AND 应产生 1 个 must 子句");
        assertEquals(1, boolQuery.mustNot().size(), "NOT 应产生 1 个 mustNot 子句");
        assertTrue(boolQuery.should().isEmpty(), "无 OR 条件不应产生 should 子句");
        assertNull(boolQuery.minimumShouldMatch(), "无 should 不应设置 minimumShouldMatch");

        TermQuery termQuery = firstTerm(boolQuery.mustNot());
        assertEquals("tags", termQuery.field());
        assertEquals("已归档", termQuery.value().stringValue());
    }

    @Test
    @DisplayName("AND + OR + NOT 三种组合共存")
    void testBooleanCondition_AllThree() {
        SearchRequestDTO request = baseRequest();
        request.setBooleanConditions(List.of(
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "fileName", "报告"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_OR, "textContent", "总结"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_NOT, "fileType", "tmp")
        ));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        assertEquals(1, boolQuery.must().size(), "AND 应产生 1 个 must 子句");
        assertEquals(1, boolQuery.should().size(), "OR 应产生 1 个 should 子句");
        assertEquals(1, boolQuery.mustNot().size(), "NOT 应产生 1 个 mustNot 子句");
        assertEquals("1", boolQuery.minimumShouldMatch(), "应设置 minimumShouldMatch=1");
    }

    @Test
    @DisplayName("字段映射: fileName/textContent -> match, tags/fileType -> term, 空 field -> multi_match")
    void testBooleanCondition_FieldMapping() {
        SearchRequestDTO request = baseRequest();
        request.setBooleanConditions(List.of(
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "fileName", "报告"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "textContent", "总结"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "tags", "机密"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "fileType", "pdf"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, null, "默认值")
        ));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        // 5 个 AND 条件全部进入 must
        assertEquals(5, boolQuery.must().size());

        // fileName -> match
        MatchQuery fileNameMatch = firstMatch(boolQuery.must().stream()
                .filter(q -> q.isMatch() && q.match().field().equals("fileName"))
                .toList());
        assertEquals("报告", fileNameMatch.query().stringValue());

        // textContent -> match
        MatchQuery contentMatch = firstMatch(boolQuery.must().stream()
                .filter(q -> q.isMatch() && q.match().field().equals("textContent"))
                .toList());
        assertEquals("总结", contentMatch.query().stringValue());

        // tags -> term
        List<TermQuery> termQueries = boolQuery.must().stream()
                .filter(Query::isTerm)
                .map(Query::term)
                .toList();
        assertEquals(2, termQueries.size(), "tags + fileType 共 2 个 term 查询");

        // 空 field -> multi_match
        MultiMatchQuery multiMatch = firstMultiMatch(boolQuery.must());
        assertEquals("默认值", multiMatch.query());
        assertTrue(multiMatch.fields().contains("fileName"));
        assertTrue(multiMatch.fields().contains("textContent"));
    }

    // ==================== 二次检索 ====================

    @Test
    @DisplayName("二次检索 refineFileIds: 添加 terms 过滤到 filter 子句")
    void testRefineSearch_WithFileIds() {
        SearchRequestDTO request = baseRequest();
        request.setQuery("关键字");
        request.setRefineFileIds(List.of(101L, 102L, 103L));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        // filter 子句中应包含 fileId 的 terms 查询
        TermsQuery termsQuery = firstTerms(boolQuery.filter());
        assertEquals("fileId", termsQuery.field());
        TermsQueryField termsField = termsQuery.terms();
        assertNotNull(termsField);
        assertEquals(3, termsField.value().size(), "应包含 3 个 fileId 值");
        // 验证 ID 值（顺序可能不同，仅校验集合）
        List<Long> ids = termsField.value().stream()
                .map(fv -> fv.longValue())
                .toList();
        assertTrue(ids.contains(101L));
        assertTrue(ids.contains(102L));
        assertTrue(ids.contains(103L));
    }

    @Test
    @DisplayName("二次检索 refineQuery: 添加 multi_match 到 must 子句")
    void testRefineSearch_WithRefineQuery() {
        SearchRequestDTO request = baseRequest();
        request.setQuery("初始关键字");
        request.setRefineQuery("二次关键字");

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        // must 子句应包含两个 multi_match: 一个来自 query, 一个来自 refineQuery
        List<MultiMatchQuery> multiMatches = boolQuery.must().stream()
                .filter(Query::isMultiMatch)
                .map(Query::multiMatch)
                .toList();
        assertEquals(2, multiMatches.size(), "应有 2 个 multi_match（query + refineQuery）");

        List<String> queries = multiMatches.stream().map(MultiMatchQuery::query).toList();
        assertTrue(queries.contains("初始关键字"), "应包含原始 query");
        assertTrue(queries.contains("二次关键字"), "应包含 refineQuery");
    }

    @Test
    @DisplayName("二次检索 refineFileIds 为空: 不添加 terms 过滤")
    void testRefineSearch_EmptyRefineFileIds() {
        SearchRequestDTO request = baseRequest();
        request.setQuery("关键字");
        request.setRefineFileIds(List.of());

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        // filter 中不应有 fileId 的 terms 查询
        boolean hasFileIdTerms = boolQuery.filter().stream()
                .noneMatch(q -> q.isTerms() && q.terms().field().equals("fileId"));
        assertTrue(hasFileIdTerms, "refineFileIds 为空时不应添加 fileId 过滤");

        // refineQuery 也为空时，must 应只有 1 个（来自 query）
        List<MultiMatchQuery> multiMatches = boolQuery.must().stream()
                .filter(Query::isMultiMatch)
                .map(Query::multiMatch)
                .toList();
        assertEquals(1, multiMatches.size(), "无 refineQuery 时只有 1 个 multi_match");
    }

    @Test
    @DisplayName("refineFileIds 为 null 时不添加过滤（向后兼容）")
    void testRefineSearch_NullRefineFileIds() {
        SearchRequestDTO request = baseRequest();
        request.setQuery("关键字");
        // 不设置 refineFileIds（null）
        // 不设置 refineQuery（null）

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        // filter 应为空（无 fileType/targetId/sensitiveLevel/tags/dateRange/refineFileIds）
        // 注意：refineQuery 也为空，所以不应有任何额外过滤
        boolean hasFileIdTerms = boolQuery.filter().stream()
                .anyMatch(q -> q.isTerms() && q.terms().field().equals("fileId"));
        assertFalse(hasFileIdTerms, "refineFileIds 为 null 时不应添加 fileId 过滤");
    }

    // ==================== 向后兼容 ====================

    @Test
    @DisplayName("无布尔条件且无二次检索: 保持原有行为（仅 query 的 multi_match）")
    void testNormalSearch_WithoutBooleanConditions() {
        SearchRequestDTO request = baseRequest();
        request.setQuery("普通关键字");

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        // 仅 1 个 must（来自 query 的 multi_match）
        assertEquals(1, boolQuery.must().size());
        Query mustQuery = boolQuery.must().get(0);
        assertTrue(mustQuery.isMultiMatch(),
                "期望 multi_match，实际: " + mustQuery._kind());
        MultiMatchQuery mm = firstMultiMatch(boolQuery.must());
        assertEquals("普通关键字", mm.query());
        // 注意: fields 中 fileName 带 boost 后缀 "fileName^3"
        assertTrue(mm.fields().stream().anyMatch(f -> f.startsWith("fileName")),
                "fields 应包含 fileName（可能带 boost 后缀）: " + mm.fields());
        assertTrue(mm.fields().contains("textContent"),
                "fields 应包含 textContent: " + mm.fields());

        // 无 should / mustNot
        assertTrue(boolQuery.should().isEmpty());
        assertTrue(boolQuery.mustNot().isEmpty());
        assertNull(boolQuery.minimumShouldMatch());
        // 无 filter（未设置任何过滤条件）
        assertTrue(boolQuery.filter().isEmpty());
    }

    @Test
    @DisplayName("完全空请求: 返回 matchAll（向后兼容原行为）")
    void testNormalSearch_EmptyRequestReturnsMatchAll() {
        SearchRequestDTO request = baseRequest();
        // 不设置任何 query / 条件 / 过滤

        Query query = elasticsearchService.buildKeywordQuery(request);

        assertNotNull(query);
        assertTrue(query.isMatchAll(), "全空请求应返回 match_all");
    }

    @Test
    @DisplayName("布尔条件 + 二次检索 + 过滤条件共存")
    void testBooleanAndRefineAndFiltersCoexist() {
        SearchRequestDTO request = baseRequest();
        request.setQuery("基础关键字");
        request.setBooleanConditions(List.of(
                cond(SearchRequestDTO.BooleanCondition.LOGIC_AND, "fileName", "报告"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_OR, "textContent", "总结"),
                cond(SearchRequestDTO.BooleanCondition.LOGIC_NOT, "fileType", "tmp")
        ));
        request.setFileType("pdf");
        request.setRefineQuery("二次关键字");
        request.setRefineFileIds(List.of(1L, 2L));

        Query query = elasticsearchService.buildKeywordQuery(request);
        BoolQuery boolQuery = asBool(query);

        // must: 1 (query multi_match) + 1 (AND match) + 1 (refineQuery multi_match) = 3
        assertEquals(3, boolQuery.must().size(), "must 应包含 query + AND + refineQuery");
        assertEquals(1, boolQuery.should().size(), "should 应包含 OR 条件");
        assertEquals(1, boolQuery.mustNot().size(), "mustNot 应包含 NOT 条件");
        assertEquals("1", boolQuery.minimumShouldMatch());

        // filter: 1 (fileType term) + 1 (refineFileIds terms) = 2
        assertEquals(2, boolQuery.filter().size(), "filter 应包含 fileType + refineFileIds");

        // 验证 refineFileIds 的 terms 存在
        TermsQuery fileIdTerms = firstTerms(boolQuery.filter());
        assertEquals("fileId", fileIdTerms.field());
        assertEquals(2, fileIdTerms.terms().value().size());
    }
}
