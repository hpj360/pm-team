package com.redteam.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.json.JsonData;
import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.api.dto.FileSearchDTO;
import com.redteam.common.result.PageResult;
import com.redteam.search.service.FileSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件检索服务实现类
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSearchServiceImpl implements FileSearchService {

    private final ElasticsearchClient esClient;

    private static final String INDEX_NAME = "files";

    @Override
    public PageResult<FileInfoDTO> search(FileSearchDTO searchDTO) {
        try {
            // 构建查询条件
            Query query = buildQuery(searchDTO);

            // 执行搜索
            SearchResponse<FileInfoDTO> response = esClient.search(s -> s
                    .index(INDEX_NAME)
                    .query(query)
                    .from((searchDTO.getCurrent() - 1) * searchDTO.getSize())
                    .size(searchDTO.getSize()),
                    FileInfoDTO.class);

            // 处理结果
            List<FileInfoDTO> records = response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());

            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            return PageResult.of(searchDTO.getCurrent().longValue(), searchDTO.getSize().longValue(), total, records);

        } catch (Exception e) {
            log.error("全文检索失败", e);
            return PageResult.empty();
        }
    }

    @Override
    public List<FileInfoDTO> semanticSearch(String query, Double similarityThreshold, Integer size) {
        // TODO: 实现向量检索，需要集成Milvus
        log.info("语义搜索: {}", query);
        return new ArrayList<>();
    }

    @Override
    public PageResult<FileInfoDTO> searchWithHighlight(String keyword, Integer current, Integer size) {
        try {
            // 构建高亮查询
            SearchResponse<FileInfoDTO> response = esClient.search(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q
                            .multiMatch(m -> m
                                    .fields("filename", "description", "textContent")
                                    .query(keyword)))
                    .highlight(h -> h
                            .fields("filename", f -> f)
                            .fields("description", f -> f)
                            .preTags("<em class='highlight'>")
                            .postTags("</em>"))
                    .from((current - 1) * size)
                    .size(size),
                    FileInfoDTO.class);

            List<FileInfoDTO> records = response.hits().hits().stream()
                    .map(hit -> {
                        FileInfoDTO dto = hit.source();
                        // 设置高亮内容
                        if (hit.highlight().containsKey("filename")) {
                            // dto.setFilename(hit.highlight().get("filename").get(0));
                        }
                        return dto;
                    })
                    .collect(Collectors.toList());

            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            return PageResult.of(current.longValue(), size.longValue(), total, records);

        } catch (Exception e) {
            log.error("高亮检索失败", e);
            return PageResult.empty();
        }
    }

    @Override
    public boolean indexFile(Long fileId) {
        // TODO: 实现文件索引
        log.info("索引文件: {}", fileId);
        return true;
    }

    @Override
    public boolean batchIndexFiles(List<Long> fileIds) {
        // TODO: 实现批量索引
        log.info("批量索引文件: {}", fileIds.size());
        return true;
    }

    @Override
    public boolean deleteIndex(Long fileId) {
        try {
            esClient.delete(d -> d
                    .index(INDEX_NAME)
                    .id(String.valueOf(fileId)));
            log.info("删除索引成功: {}", fileId);
            return true;
        } catch (Exception e) {
            log.error("删除索引失败", e);
            return false;
        }
    }

    @Override
    public boolean updateIndex(Long fileId) {
        // TODO: 实现更新索引
        log.info("更新索引: {}", fileId);
        return true;
    }

    @Override
    public List<String> getSuggestions(String prefix, Integer size) {
        try {
            SearchResponse<FileInfoDTO> response = esClient.search(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q
                            .prefix(p -> p
                                    .field("filename")
                                    .value(prefix)))
                    .size(size),
                    FileInfoDTO.class);

            return response.hits().hits().stream()
                    .map(hit -> hit.source().getFilename())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("获取搜索建议失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public Object aggregate(String field) {
        // TODO: 实现聚合统计
        log.info("聚合统计: {}", field);
        return null;
    }

    /**
     * 构建查询条件
     *
     * @param searchDTO 检索条件
     * @return 查询对象
     */
    private Query buildQuery(FileSearchDTO searchDTO) {
        // 构建布尔查询
        return Query.of(q -> q
                .bool(b -> {
                    // 关键词查询
                    if (searchDTO.getKeyword() != null && !searchDTO.getKeyword().isEmpty()) {
                        b.must(m -> m
                                .multiMatch(mm -> mm
                                        .fields("filename", "description", "textContent")
                                        .query(searchDTO.getKeyword())));
                    }

                    // 文件名查询
                    if (searchDTO.getFilename() != null && !searchDTO.getFilename().isEmpty()) {
                        b.must(m -> m
                                .wildcard(w -> w
                                        .field("filename")
                                        .value("*" + searchDTO.getFilename() + "*")));
                    }

                    // 文件类型过滤
                    if (searchDTO.getFileTypes() != null && !searchDTO.getFileTypes().isEmpty()) {
                        b.filter(f -> f
                                .terms(t -> t
                                        .field("fileType")
                                        .terms(searchDTO.getFileTypes().stream()
                                                .map(type -> co.elastic.clients.elasticsearch._types.FieldValue.of(type))
                                                .collect(Collectors.toList()))));
                    }

                    // 文件大小范围
                    if (searchDTO.getFileSizeMin() != null || searchDTO.getFileSizeMax() != null) {
                        b.filter(f -> f
                                .range(r -> {
                                    r.field("fileSize");
                                    if (searchDTO.getFileSizeMin() != null) {
                                        r.gte(JsonData.of(searchDTO.getFileSizeMin()));
                                    }
                                    if (searchDTO.getFileSizeMax() != null) {
                                        r.lte(JsonData.of(searchDTO.getFileSizeMax()));
                                    }
                                    return r;
                                }));
                    }

                    return b;
                }));
    }
}
