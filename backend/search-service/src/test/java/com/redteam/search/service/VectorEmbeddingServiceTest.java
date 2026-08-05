package com.redteam.search.service;

import com.redteam.search.config.SearchProperties;
import com.redteam.search.service.impl.VectorEmbeddingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 向量化服务单元测试
 *
 * <p>验证三级降级策略：外部 API -> DJL 本地模型 -> 哈希兜底。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VectorEmbeddingServiceTest {

    @Mock
    private SearchProperties searchProperties;

    @InjectMocks
    private VectorEmbeddingServiceImpl vectorEmbeddingService;

    /**
     * 初始化配置与 RestClient
     */
    @BeforeEach
    void setUp() {
        SearchProperties.Embedding embedding = new SearchProperties.Embedding();
        embedding.setApiUrl("http://localhost:8081/embed");
        embedding.setTimeoutSeconds(5);
        embedding.setCacheEnabled(false);
        SearchProperties.Milvus milvus = new SearchProperties.Milvus();
        milvus.setVectorDim(768);
        when(searchProperties.getEmbedding()).thenReturn(embedding);
        when(searchProperties.getMilvus()).thenReturn(milvus);

        // 手动初始化 RestClient（绕过 @PostConstruct）
        RestClient restClient = RestClient.builder().baseUrl("http://localhost:8081").build();
        ReflectionTestUtils.setField(vectorEmbeddingService, "restClient", restClient);
    }

    @Test
    @DisplayName("embed: 空文本返回零向量")
    void embed_blankTextReturnsZeroVector() {
        List<Float> result = vectorEmbeddingService.embed("");
        assertNotNull(result);
        assertEquals(768, result.size());
        assertTrue(result.stream().allMatch(v -> v == 0f));
    }

    @Test
    @DisplayName("embed: null 文本返回零向量")
    void embed_nullTextReturnsZeroVector() {
        List<Float> result = vectorEmbeddingService.embed(null);
        assertNotNull(result);
        assertEquals(768, result.size());
    }

    @Test
    @DisplayName("embed: 外部 API 失败时降级到哈希向量化")
    void embed_remoteFailsDegradesToHash() {
        // restClient 未 mock 具体响应，会抛异常，降级到哈希兜底
        List<Float> result = vectorEmbeddingService.embed("测试文本");
        assertNotNull(result);
        assertEquals(768, result.size());
        // 哈希向量应已 L2 归一化，模长 ≈ 1
        double norm = Math.sqrt(result.stream().mapToDouble(v -> v * v).sum());
        assertEquals(1.0, norm, 1e-3);
    }

    @Test
    @DisplayName("embed: 相同文本哈希兜底结果一致（确定性）")
    void embed_sameTextProducesSameHashVector() {
        List<Float> v1 = vectorEmbeddingService.embed("相同文本");
        List<Float> v2 = vectorEmbeddingService.embed("相同文本");
        assertEquals(v1, v2);
    }

    @Test
    @DisplayName("embed: 不同文本哈希兜底结果不同")
    void embed_differentTextProducesDifferentVector() {
        List<Float> v1 = vectorEmbeddingService.embed("文本A");
        List<Float> v2 = vectorEmbeddingService.embed("文本B");
        assertNotEquals(v1, v2);
    }

    @Test
    @DisplayName("embedBatch: 空列表返回空列表")
    void embedBatch_emptyListReturnsEmpty() {
        List<List<Float>> result = vectorEmbeddingService.embedBatch(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("embedBatch: null 返回空列表")
    void embedBatch_nullReturnsEmpty() {
        List<List<Float>> result = vectorEmbeddingService.embedBatch(null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("embedBatch: 批量降级为逐条向量化")
    void embedBatch_degradesToPerItem() {
        List<List<Float>> result = vectorEmbeddingService.embedBatch(List.of("文本1", "文本2"));
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(768, result.get(0).size());
        assertEquals(768, result.get(1).size());
    }

    @Test
    @DisplayName("dimension: 返回配置维度")
    void dimension_returnsConfiguredDim() {
        assertEquals(768, vectorEmbeddingService.dimension());
    }
}
