package com.redteam.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.ai.entity.KnowledgeEntity;
import com.redteam.ai.mapper.KnowledgeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库检索服务
 *
 * <p>基于 Milvus 向量数据库实现知识库的索引与语义检索。知识库种子涵盖：
 * ATT&CK 矩阵、CVE 漏洞库、APT 组织档案、历史分析报告。</p>
 *
 * <p>降级策略：Milvus 不可用时返回空列表并记录日志，不阻塞主流程。</p>
 *
 * @author 红方团队
 */
@Service
@Slf4j
public class RagService {

    /**
     * 检索结果最大片段长度
     */
    private static final int MAX_SNIPPET_LENGTH = 500;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    /**
     * search-service 服务地址（内含 Milvus 向量检索能力）
     */
    @Value("${search.service.url:http://localhost:8081}")
    private String searchServiceUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 索引知识库文档
     *
     * <p>将文档写入本地数据库，并调用 search-service 进行向量化与索引。
     * 向量索引失败时不阻塞文档落库。</p>
     *
     * @param knowledgeId 知识ID（为空时自动生成）
     * @param content     文档内容
     * @param metadata    元数据
     * @return 知识ID
     */
    public String indexDocument(String knowledgeId, String content, Map<String, Object> metadata) {
        log.info("索引知识库文档, knowledgeId={}, contentLength={}", knowledgeId,
                content == null ? 0 : content.length());

        KnowledgeEntity entity = new KnowledgeEntity();
        entity.setKnowledgeId(knowledgeId);
        entity.setContent(content);
        entity.setSource(metadata == null ? null : (String) metadata.get("source"));
        entity.setTitle(metadata == null ? null : (String) metadata.get("title"));
        try {
            entity.setMetadataJson(objectMapper.writeValueAsString(metadata == null ? Collections.emptyMap() : metadata));
        } catch (Exception e) {
            log.warn("元数据序列化失败: {}", e.getMessage());
            entity.setMetadataJson("{}");
        }
        entity.setCreatedAt(LocalDateTime.now());

        // 落库
        try {
            knowledgeMapper.insert(entity);
        } catch (Exception e) {
            log.warn("知识库文档落库失败, knowledgeId={}: {}", knowledgeId, e.getMessage());
        }

        // 调用 search-service 向量索引（降级：失败不阻塞）
        try {
            String url = searchServiceUrl + "/api/search/knowledge/index";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("knowledgeId", entity.getKnowledgeId());
            body.put("content", content);
            body.put("metadata", metadata);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForObject(url, request, String.class);
            log.info("知识库文档向量索引成功, knowledgeId={}", entity.getKnowledgeId());
        } catch (Exception e) {
            log.warn("向量索引失败（降级，文档已落库）, knowledgeId={}: {}", entity.getKnowledgeId(), e.getMessage());
        }

        return entity.getKnowledgeId();
    }

    /**
     * 语义检索知识库
     *
     * <p>调用 search-service 进行向量检索，返回 topK 相关知识片段。
     * 降级：search-service 不可用时从本地数据库做关键词匹配兜底。</p>
     *
     * @param query 查询语句
     * @param topK  返回条数
     * @return 知识片段列表（每项含 knowledgeId、title、content、score、source）
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        int limit = topK <= 0 ? 5 : topK;

        // 优先调用 search-service 向量检索
        try {
            String url = searchServiceUrl + "/api/search/knowledge/search?query=" + query + "&topK=" + limit;
            String response = restTemplate.getForObject(url, String.class);
            if (response != null && !response.isBlank()) {
                List<Map<String, Object>> results = objectMapper.readValue(response, List.class);
                log.info("向量检索成功, query={}, results={}", query, results.size());
                return results;
            }
        } catch (Exception e) {
            log.warn("向量检索失败，降级为本地关键词匹配, query={}: {}", query, e.getMessage());
        }

        // 降级：本地关键词匹配
        return localKeywordSearch(query, limit);
    }

    /**
     * 删除知识库文档
     *
     * <p>同时删除本地数据库记录与 search-service 中的向量索引。</p>
     *
     * @param knowledgeId 知识ID
     * @return 是否删除成功
     */
    public boolean deleteKnowledge(String knowledgeId) {
        log.info("删除知识库文档, knowledgeId={}", knowledgeId);
        boolean dbDeleted = false;
        try {
            KnowledgeEntity entity = new KnowledgeEntity();
            entity.setKnowledgeId(knowledgeId);
            knowledgeMapper.deleteById(entity);
            dbDeleted = true;
        } catch (Exception e) {
            log.warn("删除知识库记录失败, knowledgeId={}: {}", knowledgeId, e.getMessage());
        }

        // 调用 search-service 删除向量索引（降级：失败不阻塞）
        try {
            String url = searchServiceUrl + "/api/search/knowledge/" + knowledgeId;
            restTemplate.delete(url);
            log.info("向量索引删除成功, knowledgeId={}", knowledgeId);
        } catch (Exception e) {
            log.warn("删除向量索引失败（降级）, knowledgeId={}: {}", knowledgeId, e.getMessage());
        }

        return dbDeleted;
    }

    /**
     * 查询全部知识库文档列表
     *
     * @return 知识库文档列表
     */
    public List<KnowledgeEntity> listAll() {
        try {
            return knowledgeMapper.selectAllOrderByCreatedAtDesc();
        } catch (Exception e) {
            log.warn("查询知识库列表失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 本地关键词匹配（降级检索）
     *
     * @param query 查询语句
     * @param limit 返回条数
     * @return 匹配的知识片段列表
     */
    private List<Map<String, Object>> localKeywordSearch(String query, int limit) {
        List<KnowledgeEntity> all = listAll();
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (KnowledgeEntity entity : all) {
            if (entity.getContent() != null && entity.getContent().toLowerCase().contains(lowerQuery)) {
                Map<String, Object> item = new HashMap<>();
                item.put("knowledgeId", entity.getKnowledgeId());
                item.put("title", entity.getTitle());
                item.put("content", truncate(entity.getContent(), MAX_SNIPPET_LENGTH));
                item.put("source", entity.getSource());
                item.put("score", 0.5);
                results.add(item);
                if (results.size() >= limit) {
                    break;
                }
            }
        }
        log.info("本地关键词匹配完成, query={}, results={}", query, results.size());
        return results;
    }

    /**
     * 截断字符串到指定长度
     *
     * @param text    原始文本
     * @param maxLen  最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
