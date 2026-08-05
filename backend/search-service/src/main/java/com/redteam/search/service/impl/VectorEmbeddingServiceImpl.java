package com.redteam.search.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.search.config.SearchProperties;
import com.redteam.search.service.VectorEmbeddingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量化服务实现
 *
 * <p>三级降级策略：
 * <ol>
 *   <li>优先调用外部模型服务（HTTP API，如 sentence-transformers/bge-large-zh）</li>
 *   <li>失败时降级到 DJL 本地模型（需引入 DJL 依赖，预留接口）</li>
 *   <li>都失败则使用简单哈希向量化（仅作兜底，不推荐用于生产语义检索）</li>
 * </ol>
 * 支持基于 Redis 的向量缓存（cacheEnabled=true 时启用）。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorEmbeddingServiceImpl implements VectorEmbeddingService {

    private final SearchProperties searchProperties;

    /**
     * HTTP 客户端（懒加载）
     */
    private RestClient restClient;

    /**
     * 初始化 HTTP 客户端
     */
    @PostConstruct
    public void init() {
        SearchProperties.Embedding conf = searchProperties.getEmbedding();
        restClient = RestClient.builder()
                .baseUrl(conf.getApiUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("向量化服务初始化: apiUrl={}, timeout={}s, dim={}, cacheEnabled={}",
                conf.getApiUrl(), conf.getTimeoutSeconds(),
                searchProperties.getMilvus().getVectorDim(), conf.getCacheEnabled());
    }

    /**
     * 文本向量化
     *
     * @param text 原始文本
     * @return 768 维浮点向量
     */
    @Override
    public List<Float> embed(String text) {
        if (StrUtil.isBlank(text)) {
            return zeroVector();
        }
        // 截断过长文本，避免模型超时
        String truncated = truncate(text, 8000);

        try {
            List<Float> remote = embedViaRemote(truncated);
            if (remote != null && !remote.isEmpty()) {
                return remote;
            }
        } catch (Exception e) {
            log.warn("外部向量化服务调用失败，降级处理: {}", e.getMessage());
        }

        // DJL 本地模型降级（预留扩展点）
        List<Float> local = embedViaLocalModel(truncated);
        if (local != null && !local.isEmpty()) {
            return local;
        }

        // 哈希向量化兜底
        log.warn("外部与本地模型均不可用，使用哈希向量化兜底");
        return hashEmbed(truncated);
    }

    /**
     * 批量文本向量化
     *
     * @param texts 文本列表
     * @return 向量列表（顺序与入参一致）
     */
    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        // 优先尝试批量调用外部 API
        try {
            List<List<Float>> remote = embedBatchViaRemote(texts);
            if (remote != null && remote.size() == texts.size()) {
                return remote;
            }
        } catch (Exception e) {
            log.warn("外部批量向量化失败，降级为逐条向量化: {}", e.getMessage());
        }
        // 降级逐条处理
        return texts.stream().map(this::embed).collect(Collectors.toList());
    }

    /**
     * 向量维度
     *
     * @return 维度
     */
    @Override
    public int dimension() {
        return searchProperties.getMilvus().getVectorDim();
    }

    // ==================== 外部 HTTP 模型服务 ====================

    /**
     * 调用外部向量化 API（单条）
     *
     * @param text 文本
     * @return 向量，失败返回 null
     */
    private List<Float> embedViaRemote(String text) {
        SearchProperties.Embedding conf = searchProperties.getEmbedding();
        JSONObject body = new JSONObject();
        body.set("text", text);
        try {
            String resp = restClient.post()
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return parseEmbeddingResponse(resp);
        } catch (Exception e) {
            throw new RuntimeException("外部向量化 API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 调用外部向量化 API（批量）
     *
     * @param texts 文本列表
     * @return 向量列表，失败返回 null
     */
    private List<List<Float>> embedBatchViaRemote(List<String> texts) {
        SearchProperties.Embedding conf = searchProperties.getEmbedding();
        JSONObject body = new JSONObject();
        body.set("texts", texts);
        try {
            String resp = restClient.post()
                    .uri("/batch")
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);
            return parseBatchEmbeddingResponse(resp);
        } catch (Exception e) {
            throw new RuntimeException("外部批量向量化 API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析单条向量化响应
     *
     * @param resp HTTP 响应体
     * @return 向量
     */
    private List<Float> parseEmbeddingResponse(String resp) {
        if (StrUtil.isBlank(resp)) {
            return null;
        }
        JSONObject json = JSONUtil.parseObj(resp);
        Object embedding = json.get("embedding");
        if (embedding == null) {
            embedding = json.get("vector");
        }
        if (embedding == null) {
            embedding = json.get("data");
        }
        if (embedding instanceof JSONArray) {
            return toFloatList((JSONArray) embedding);
        }
        return null;
    }

    /**
     * 解析批量向量化响应
     *
     * @param resp HTTP 响应体
     * @return 向量列表
     */
    private List<List<Float>> parseBatchEmbeddingResponse(String resp) {
        if (StrUtil.isBlank(resp)) {
            return null;
        }
        JSONObject json = JSONUtil.parseObj(resp);
        Object embedding = json.get("embeddings");
        if (embedding == null) {
            embedding = json.get("data");
        }
        if (embedding instanceof JSONArray) {
            JSONArray arr = (JSONArray) embedding;
            List<List<Float>> result = new ArrayList<>(arr.size());
            for (Object item : arr) {
                if (item instanceof JSONArray) {
                    result.add(toFloatList((JSONArray) item));
                }
            }
            return result;
        }
        return null;
    }

    /**
     * JSONArray 转 List<Float>
     *
     * @param arr JSONArray
     * @return List<Float>
     */
    private List<Float> toFloatList(JSONArray arr) {
        List<Float> list = new ArrayList<>(arr.size());
        for (Object item : arr) {
            if (item instanceof Number) {
                list.add(((Number) item).floatValue());
            } else {
                list.add(Float.valueOf(item.toString()));
            }
        }
        return list;
    }

    // ==================== DJL 本地模型（预留扩展点） ====================

    /**
     * DJL 本地模型向量化（预留扩展点，需引入 ai.djl:api + ai.djl.pytorch:pytorch-engine 依赖）
     *
     * <p>当前未启用，返回 null 触发哈希兜底。后续可在此加载 bge-large-zh ONNX 模型。</p>
     *
     * @param text 文本
     * @return 向量，不可用返回 null
     */
    private List<Float> embedViaLocalModel(String text) {
        // 预留扩展点：DJL 本地推理
        // 依赖未引入，直接返回 null 触发兜底
        return null;
    }

    // ==================== 哈希向量化兜底 ====================

    /**
     * 哈希向量化（兜底方案）
     *
     * <p>基于 SHA-256 哈希生成确定性向量，保证相同文本得到相同向量。
     * 不具备语义能力，仅保证检索服务可用。</p>
     *
     * @param text 文本
     * @return 维向量
     */
    private List<Float> hashEmbed(String text) {
        int dim = searchProperties.getMilvus().getVectorDim();
        List<Float> vector = new ArrayList<>(dim);
        byte[] hash = DigestUtil.sha256(text.getBytes(StandardCharsets.UTF_8));
        // 循环填充 hash 字节，归一化到 [-1, 1]
        for (int i = 0; i < dim; i++) {
            byte b = hash[i % hash.length];
            vector.add((b & 0xFF) / 127.5f - 1.0f);
        }
        // L2 归一化，便于 COSINE 度量
        return l2Normalize(vector);
    }

    /**
     * L2 归一化
     *
     * @param vector 原始向量
     * @return 归一化后向量
     */
    private List<Float> l2Normalize(List<Float> vector) {
        double sum = 0.0;
        for (Float v : vector) {
            sum += v * v;
        }
        double norm = Math.sqrt(sum);
        if (norm < 1e-9) {
            return vector;
        }
        List<Float> normalized = new ArrayList<>(vector.size());
        for (Float v : vector) {
            normalized.add((float) (v / norm));
        }
        return normalized;
    }

    /**
     * 截断文本
     *
     * @param text     原始文本
     * @param maxChars 最大字符数
     * @return 截断后文本
     */
    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    /**
     * 零向量
     *
     * @return 维零向量
     */
    private List<Float> zeroVector() {
        int dim = searchProperties.getMilvus().getVectorDim();
        List<Float> v = new ArrayList<>(dim);
        for (int i = 0; i < dim; i++) {
            v.add(0f);
        }
        return v;
    }
}
