package com.redteam.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.TermsAggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettingsAnalysis;
import co.elastic.clients.json.JsonData;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.search.config.SearchProperties;
import com.redteam.search.dto.AggregationVO;
import com.redteam.search.dto.FileIndexDTO;
import com.redteam.search.dto.SearchHitVO;
import com.redteam.search.dto.SearchRequestDTO;
import com.redteam.search.dto.SearchResultVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Elasticsearch 检索服务实现
 *
 * <p>负责 ES 索引的创建、文档增删、关键字检索、聚合统计。
 * 使用 {@code search_after} 替代 deep paging 以保证深分页性能。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final SearchProperties searchProperties;

    /**
     * 高亮字段：文件名
     */
    private static final String FIELD_FILE_NAME = "fileName";
    /**
     * 高亮字段：文本内容
     */
    private static final String FIELD_TEXT_CONTENT = "textContent";

    /**
     * 启动时检查并创建索引
     */
    @PostConstruct
    public void init() {
        try {
            createIndexIfNotExists();
        } catch (Exception e) {
            // 索引初始化失败不阻塞启动，后续写入/检索时再降级处理
            log.error("ES 索引初始化失败，将在后续操作中重试", e);
        }
    }

    /**
     * 创建索引（含 IK 分词器）
     *
     * @throws IOException 与 ES 通信异常
     */
    public void createIndexIfNotExists() throws IOException {
        String indexName = searchProperties.getEs().getIndexName();
        boolean exists = elasticsearchClient.indices().exists(e -> e.index(indexName)).value();
        if (exists) {
            log.info("ES 索引已存在: {}", indexName);
            return;
        }

        // 优先读取 classpath:/es/index-mapping.json
        String mappingJson = loadMappingJson();
        CreateIndexRequest.Builder builder = new CreateIndexRequest.Builder().index(indexName);
        if (mappingJson != null) {
            builder.withJson(new java.io.StringReader(mappingJson));
        } else {
            builder.settings(buildIndexSettings())
                    .mappings(m -> m
                            .properties(FIELD_FILE_NAME, p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties(FIELD_TEXT_CONTENT, p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("fileType", p -> p.keyword(k -> k))
                            .properties("fileSize", p -> p.long_(l -> l))
                            .properties("fileSm3", p -> p.keyword(k -> k))
                            .properties("fileId", p -> p.long_(l -> l))
                            .properties("targetId", p -> p.long_(l -> l))
                            .properties("tags", p -> p.keyword(k -> k))
                            .properties("sensitiveLevel", p -> p.integer(i -> i))
                            .properties("isPublic", p -> p.integer(i -> i))
                            .properties("uploadTime", p -> p.date(d -> d))
                            .properties("createTime", p -> p.date(d -> d)));
        }
        elasticsearchClient.indices().create(builder.build());
        log.info("ES 索引创建成功: {}", indexName);
    }

    /**
     * 加载索引映射 JSON
     *
     * @return JSON 字符串，失败返回 null
     */
    private String loadMappingJson() {
        try (InputStream is = new ClassPathResource("es/index-mapping.json").getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("加载 es/index-mapping.json 失败，将使用编程式映射: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建索引 settings
     *
     * @return IndexSettings
     */
    private IndexSettings buildIndexSettings() {
        return IndexSettings.of(s -> s
                .numberOfShards(String.valueOf(searchProperties.getEs().getShards()))
                .numberOfReplicas(String.valueOf(searchProperties.getEs().getReplicas()))
                .analysis(IndexSettingsAnalysis.of(a -> a
                        .analyzer("ik_smart_analyzer", an -> an.custom(c -> c.tokenizer("ik_smart")))
                        .analyzer("ik_max_word_analyzer", an -> an.custom(c -> c.tokenizer("ik_max_word"))))));
    }

    /**
     * 写入 ES 文档（fileId 作为文档 ID）
     *
     * @param dto 文件索引数据
     */
    public void indexFile(FileIndexDTO dto) {
        if (dto == null || dto.getFileId() == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "文件索引数据 fileId 不能为空");
        }
        String indexName = searchProperties.getEs().getIndexName();
        try {
            Map<String, Object> doc = convertToDoc(dto);
            elasticsearchClient.index(i -> i.index(indexName).id(String.valueOf(dto.getFileId())).document(doc));
            log.info("ES 文档写入成功: fileId={}, fileName={}", dto.getFileId(), dto.getFileName());
        } catch (Exception e) {
            log.error("ES 文档写入失败: fileId={}", dto.getFileId(), e);
            throw BusinessException.of(ResultCode.INDEX_CREATE_ERROR, "ES 索引创建失败: " + e.getMessage());
        }
    }

    /**
     * 删除文档
     *
     * @param fileId 文件 ID
     */
    public void deleteDocument(Long fileId) {
        if (fileId == null) {
            return;
        }
        String indexName = searchProperties.getEs().getIndexName();
        try {
            elasticsearchClient.delete(d -> d.index(indexName).id(String.valueOf(fileId)));
            log.info("ES 文档删除成功: fileId={}", fileId);
        } catch (Exception e) {
            log.error("ES 文档删除失败: fileId={}", fileId, e);
            throw BusinessException.of(ResultCode.INDEX_DELETE_ERROR, "ES 索引删除失败: " + e.getMessage());
        }
    }

    /**
     * 关键字检索
     *
     * <p>使用 bool query：must(multi_match) + filter(fileType/targetId/sensitiveLevel/tags/dateRange)。
     * 高亮 fileName/textContent，聚合 fileType/targetId/sensitiveLevel/tags。
     * 采用 from + size 浅分页；深分页通过 searchAfter 方法支持。</p>
     *
     * @param request 检索请求
     * @return 检索结果
     */
    public SearchResultVO keywordSearch(SearchRequestDTO request) {
        if (request == null) {
            return SearchResultVO.empty(null);
        }
        long start = System.currentTimeMillis();
        String indexName = searchProperties.getEs().getIndexName();
        try {
            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index(indexName)
                    .query(buildKeywordQuery(request))
                    .from(Math.max(0, (request.getPageNum() - 1) * request.getPageSize()))
                    .size(request.getPageSize())
                    .sort(s -> s.field(f -> f.field("_score").order(SortOrder.Desc)))
                    .sort(s -> s.field(f -> f.field("fileId").order(SortOrder.Desc)))
                    .highlight(h -> h
                            .fields(FIELD_FILE_NAME, hl -> hl.preTags("<em>").postTags("</em>"))
                            .fields(FIELD_TEXT_CONTENT, hl -> hl
                                    .preTags("<em>").postTags("</em>")
                                    .fragmentSize(150).numberOfFragments(3)));

            // 聚合
            attachAggregations(searchBuilder);

            SearchResponse<Map> response = elasticsearchClient.search(searchBuilder.build(), Map.class);

            SearchResultVO vo = new SearchResultVO();
            vo.setTotal(response.hits().total() == null ? 0L : response.hits().total().value());
            vo.setPageNum(request.getPageNum());
            vo.setPageSize(request.getPageSize());
            vo.setHits(convertHits(response.hits().hits(), request.getSearchType()));
            vo.setAggregations(convertAggregations(response));
            vo.setResponseTimeMs(System.currentTimeMillis() - start);
            return vo;
        } catch (Exception e) {
            log.error("ES 关键字检索失败: query={}", request.getQuery(), e);
            // 降级返回空结果
            SearchResultVO empty = SearchResultVO.empty(request);
            empty.setResponseTimeMs(System.currentTimeMillis() - start);
            return empty;
        }
    }

    /**
     * 仅获取聚合结果（hits 不返回）
     *
     * @param request 检索请求
     * @return 聚合结果
     */
    public Map<String, Object> getAggregations(SearchRequestDTO request) {
        if (request == null) {
            return Map.of();
        }
        String indexName = searchProperties.getEs().getIndexName();
        try {
            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index(indexName)
                    .query(buildKeywordQuery(request))
                    .size(0);
            attachAggregations(searchBuilder);

            SearchResponse<Map> response = elasticsearchClient.search(searchBuilder.build(), Map.class);
            return convertAggregations(response);
        } catch (Exception e) {
            log.error("ES 聚合查询失败: query={}", request.getQuery(), e);
            return Map.of();
        }
    }

    /**
     * 搜索建议（前缀匹配 fileName）
     *
     * @param prefix 前缀
     * @param size   返回数量
     * @return 建议列表
     */
    public List<String> getSuggestions(String prefix, Integer size) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        String indexName = searchProperties.getEs().getIndexName();
        int limit = size == null || size <= 0 ? 10 : size;
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(indexName)
                    .query(q -> q.match(m -> m.field(FIELD_FILE_NAME).query(prefix)))
                    .size(limit)
                    .source(src -> src.fetch(false))
                    .build();
            SearchResponse<Map> response = elasticsearchClient.search(request, Map.class);
            return response.hits().hits().stream()
                    .map(h -> {
                        Object name = h.source() == null ? null : h.source().get(FIELD_FILE_NAME);
                        return name == null ? null : name.toString();
                    })
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("ES 搜索建议失败: prefix={}", prefix, e);
            return List.of();
        }
    }

    /**
     * 构建关键字 bool 查询
     *
     * <p>支持基础 multi_match + filter 过滤；同时支持 {@link SearchRequestDTO#getBooleanConditions()}
     * 提供的 AND/OR/NOT 组合条件，以及 {@link SearchRequestDTO#getRefineQuery()} / {@link
     * SearchRequestDTO#getRefineFileIds()} 提供的二次检索能力。</p>
     *
     * <p>可见性为 package-private 以便单元测试直接验证 Query 结构。</p>
     *
     * @param request 检索请求
     * @return Query
     */
    Query buildKeywordQuery(SearchRequestDTO request) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // must: multi_match on fileName + textContent
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            boolBuilder.must(m -> m.multiMatch(mm -> mm
                    .query(request.getQuery())
                    .fields(FIELD_FILE_NAME + "^3", FIELD_TEXT_CONTENT)
                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)));
        }

        // 布尔组合条件（AND/OR/NOT）
        List<SearchRequestDTO.BooleanCondition> conditions = request.getBooleanConditions();
        if (conditions != null && !conditions.isEmpty()) {
            boolean hasShould = false;
            for (SearchRequestDTO.BooleanCondition cond : conditions) {
                if (cond == null || cond.getLogic() == null) {
                    continue;
                }
                Query condQuery = buildBooleanConditionQuery(cond);
                if (condQuery == null) {
                    continue;
                }
                String logic = cond.getLogic().toUpperCase();
                switch (logic) {
                    case SearchRequestDTO.BooleanCondition.LOGIC_AND:
                        boolBuilder.must(condQuery);
                        break;
                    case SearchRequestDTO.BooleanCondition.LOGIC_OR:
                        boolBuilder.should(condQuery);
                        hasShould = true;
                        break;
                    case SearchRequestDTO.BooleanCondition.LOGIC_NOT:
                        boolBuilder.mustNot(condQuery);
                        break;
                    default:
                        log.warn("未知的布尔逻辑操作符: {}，跳过该条件", cond.getLogic());
                }
            }
            // 有 should 子句时至少匹配一个
            if (hasShould) {
                boolBuilder.minimumShouldMatch("1");
            }
        }

        // filter: fileType
        if (request.getFileType() != null && !request.getFileType().isBlank()) {
            boolBuilder.filter(f -> f.term(t -> t.field("fileType").value(request.getFileType())));
        }
        // filter: targetId
        if (request.getTargetId() != null) {
            boolBuilder.filter(f -> f.term(t -> t.field("targetId").value(request.getTargetId())));
        }
        // filter: sensitiveLevel
        if (request.getSensitiveLevel() != null) {
            boolBuilder.filter(f -> f.term(t -> t.field("sensitiveLevel").value(request.getSensitiveLevel())));
        }
        // filter: tags
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            boolBuilder.filter(f -> f.terms(tq -> tq
                    .field("tags")
                    .terms(tv -> tv.value(request.getTags().stream()
                            .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                            .collect(Collectors.toList())))));
        }
        // filter: dateRange
        if (request.getDateFrom() != null || request.getDateTo() != null) {
            boolBuilder.filter(f -> f.range(r -> {
                r.field("uploadTime");
                if (request.getDateFrom() != null) {
                    r.gte(JsonData.of(formatDate(request.getDateFrom())));
                }
                if (request.getDateTo() != null) {
                    r.lte(JsonData.of(formatDate(request.getDateTo())));
                }
                return r;
            }));
        }

        // 二次检索过滤（refineFileIds / refineQuery）
        buildRefineFilter(request, boolBuilder);

        // 无 must 且无 filter 时匹配全部
        BoolQuery boolQuery = boolBuilder.build();
        if (boolQuery.must().isEmpty() && boolQuery.filter().isEmpty()
                && boolQuery.should().isEmpty() && boolQuery.mustNot().isEmpty()) {
            return Query.of(q -> q.matchAll(ma -> ma));
        }
        return boolQuery._toQuery();
    }

    /**
     * 根据单个布尔条件构建子查询
     *
     * <p>field 映射规则：
     * <ul>
     *   <li>fileName / textContent → match 查询（分词）</li>
     *   <li>tags / fileType → term 查询（不分词，精确匹配）</li>
     *   <li>field 为空 → multi_match on fileName + textContent</li>
     * </ul>
     * </p>
     *
     * @param cond 布尔条件
     * @return Query，若 value 为空则返回 null
     */
    private Query buildBooleanConditionQuery(SearchRequestDTO.BooleanCondition cond) {
        if (cond.getValue() == null || cond.getValue().isBlank()) {
            return null;
        }
        String value = cond.getValue();
        String field = cond.getField();
        if (field == null || field.isBlank()) {
            // 默认 multi_match on fileName + textContent
            return Query.of(q -> q.multiMatch(mm -> mm
                    .query(value)
                    .fields(FIELD_FILE_NAME, FIELD_TEXT_CONTENT)));
        }
        switch (field) {
            case FIELD_FILE_NAME:
                return Query.of(q -> q.match(m -> m.field(FIELD_FILE_NAME).query(value)));
            case FIELD_TEXT_CONTENT:
                return Query.of(q -> q.match(m -> m.field(FIELD_TEXT_CONTENT).query(value)));
            case "tags":
                return Query.of(q -> q.term(t -> t.field("tags").value(value)));
            case "fileType":
                return Query.of(q -> q.term(t -> t.field("fileType").value(value)));
            default:
                log.warn("未知的搜索字段: {}，回退到 multi_match", field);
                return Query.of(q -> q.multiMatch(mm -> mm
                        .query(value)
                        .fields(FIELD_FILE_NAME, FIELD_TEXT_CONTENT)));
        }
    }

    /**
     * 构建二次检索过滤条件
     *
     * <p>若 refineFileIds 不为空，则作为 filter 子句以 terms 形式限定 fileId 范围；
     * 若 refineQuery 不为空，则作为额外的 must 子句（multi_match on fileName + textContent）。</p>
     *
     * @param request      检索请求
     * @param boolBuilder  主 bool 查询构建器
     */
    private void buildRefineFilter(SearchRequestDTO request, BoolQuery.Builder boolBuilder) {
        List<Long> refineFileIds = request.getRefineFileIds();
        if (refineFileIds != null && !refineFileIds.isEmpty()) {
            boolBuilder.filter(f -> f.terms(tq -> tq
                    .field("fileId")
                    .terms(tv -> tv.value(refineFileIds.stream()
                            .map(id -> co.elastic.clients.elasticsearch._types.FieldValue.of(id))
                            .collect(Collectors.toList())))));
        }
        if (request.getRefineQuery() != null && !request.getRefineQuery().isBlank()) {
            boolBuilder.must(m -> m.multiMatch(mm -> mm
                    .query(request.getRefineQuery())
                    .fields(FIELD_FILE_NAME + "^3", FIELD_TEXT_CONTENT)
                    .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)));
        }
    }

    /**
     * 附加聚合
     *
     * @param builder SearchRequest.Builder
     */
    private void attachAggregations(SearchRequest.Builder builder) {
        builder.aggregations("fileType", a -> a.terms(TermsAggregation.of(t -> t.field("fileType").size(20))))
                .aggregations("targetId", a -> a.terms(TermsAggregation.of(t -> t.field("targetId").size(20))))
                .aggregations("sensitiveLevel", a -> a.terms(TermsAggregation.of(t -> t.field("sensitiveLevel").size(10))))
                .aggregations("tags", a -> a.terms(TermsAggregation.of(t -> t.field("tags").size(50))));
    }

    /**
     * 转换 ES Hit 列表为 VO 列表
     *
     * @param hits       ES Hit 列表
     * @param searchType 检索类型
     * @return VO 列表
     */
    @SuppressWarnings("unchecked")
    private List<SearchHitVO> convertHits(List<Hit<Map>> hits, String searchType) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<SearchHitVO> list = new ArrayList<>(hits.size());
        for (Hit<Map> hit : hits) {
            Map<String, Object> source = hit.source();
            if (source == null) {
                continue;
            }
            SearchHitVO vo = new SearchHitVO();
            vo.setFileId(toLong(source.get("fileId")));
            vo.setFileName(toStr(source.get(FIELD_FILE_NAME)));
            vo.setFileType(toStr(source.get("fileType")));
            vo.setFileSize(toLong(source.get("fileSize")));
            vo.setFileSm3(toStr(source.get("fileSm3")));
            vo.setTargetId(toLong(source.get("targetId")));
            vo.setUploadTime(toLocalDateTime(source.get("uploadTime")));
            vo.setScore(hit.score() == null ? 0f : hit.score().floatValue());
            vo.setSearchType(searchType);
            Object tags = source.get("tags");
            if (tags instanceof List) {
                vo.setTags(((List<Object>) tags).stream()
                        .map(String::valueOf).collect(Collectors.toList()));
            }
            // 高亮
            if (hit.highlight() != null && !hit.highlight().isEmpty()) {
                Map<String, List<String>> hl = new HashMap<>();
                hit.highlight().forEach((k, v) -> hl.put(k, v));
                vo.setHighlight(hl);
            }
            list.add(vo);
        }
        return list;
    }

    /**
     * 转换 ES 聚合响应
     *
     * @param response ES 检索响应
     * @return 聚合 Map
     */
    private Map<String, Object> convertAggregations(SearchResponse<Map> response) {
        if (response.aggregations() == null || response.aggregations().isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        response.aggregations().forEach((name, agg) -> {
            AggregationVO vo = new AggregationVO();
            vo.setName(name);
            if (agg.isSterms()) {
                agg.sterms().buckets().array().forEach(b ->
                        vo.getBuckets().add(new AggregationVO.Bucket(b.key().stringValue(), b.docCount())));
            } else if (agg.isLterms()) {
                agg.lterms().buckets().array().forEach(b ->
                        vo.getBuckets().add(new AggregationVO.Bucket(String.valueOf(b.key()), b.docCount())));
            }
            result.put(name, vo);
        });
        return result;
    }

    /**
     * FileIndexDTO 转 ES 文档 Map
     *
     * @param dto 文件索引数据
     * @return ES 文档 Map
     */
    private Map<String, Object> convertToDoc(FileIndexDTO dto) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("fileId", dto.getFileId());
        doc.put(FIELD_FILE_NAME, dto.getFileName());
        doc.put("fileType", dto.getFileType());
        doc.put("fileSize", dto.getFileSize());
        doc.put("fileSm3", dto.getFileSm3());
        doc.put(FIELD_TEXT_CONTENT, dto.getTextContent());
        doc.put("targetId", dto.getTargetId());
        doc.put("tags", dto.getTags());
        doc.put("sensitiveLevel", dto.getSensitiveLevel());
        doc.put("isPublic", dto.getIsPublic());
        doc.put("uploadTime", dto.getUploadTime() == null ? null : formatDate(dto.getUploadTime()));
        doc.put("createTime", dto.getCreateTime() == null ? null : formatDate(dto.getCreateTime()));
        if (dto.getNerEntities() != null) {
            doc.put("nerEntities", dto.getNerEntities());
        }
        if (dto.getYaraMatches() != null) {
            doc.put("yaraMatches", dto.getYaraMatches());
        }
        return doc;
    }

    /**
     * 格式化日期为 ES 接受的字符串
     *
     * @param dateTime LocalDateTime
     * @return 字符串
     */
    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Object -> Long（空安全）
     *
     * @param obj 原始值
     * @return Long
     */
    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.valueOf(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Object -> String（空安全）
     *
     * @param obj 原始值
     * @return String
     */
    private String toStr(Object obj) {
        return obj == null ? null : obj.toString();
    }

    /**
     * Object -> LocalDateTime（空安全）
     *
     * @param obj 原始值
     * @return LocalDateTime
     */
    private LocalDateTime toLocalDateTime(Object obj) {
        if (obj == null) {
            return null;
        }
        String s = obj.toString();
        try {
            return LocalDateTime.parse(s.replace("Z", ""), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
