package com.redteam.search.service;

import com.redteam.common.exception.BusinessException;
import com.redteam.search.config.SearchProperties;
import com.redteam.search.dto.VectorSearchResultDTO;
import com.redteam.search.service.impl.MilvusService;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.R;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Milvus 向量检索服务单元测试
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MilvusServiceTest {

    @Mock
    private MilvusServiceClient milvusServiceClient;

    @Mock
    private SearchProperties searchProperties;

    @InjectMocks
    private MilvusService milvusService;

    /**
     * 初始化配置
     */
    @BeforeEach
    void setUp() {
        SearchProperties.Milvus milvus = new SearchProperties.Milvus();
        milvus.setCollectionName("file_vectors");
        milvus.setVectorDim(768);
        milvus.setIndexType("IVF_FLAT");
        milvus.setMetricType("COSINE");
        milvus.setNlist(1024);
        milvus.setNprobe(16);
        when(searchProperties.getMilvus()).thenReturn(milvus);
    }

    @Test
    @DisplayName("insertVector: fileId 为 null 抛业务异常")
    void insertVector_nullFileIdThrows() {
        List<Float> vector = List.of(0.1f, 0.2f);
        assertThrows(BusinessException.class,
                () -> milvusService.insertVector(null, vector, new HashMap<>()));
    }

    @Test
    @DisplayName("insertVector: 向量为空抛业务异常")
    void insertVector_emptyVectorThrows() {
        assertThrows(BusinessException.class,
                () -> milvusService.insertVector(1L, Collections.emptyList(), new HashMap<>()));
    }

    @Test
    @DisplayName("insertVector: 插入成功")
    void insertVector_success() {
        List<Float> vector = List.of(0.1f, 0.2f, 0.3f);
        Map<String, Object> meta = new HashMap<>();
        meta.put(MilvusService.FIELD_FILE_NAME, "file.pdf");
        meta.put(MilvusService.FIELD_FILE_SM3, "sm3hash");
        meta.put(MilvusService.FIELD_TARGET_ID, 100L);
        meta.put(MilvusService.FIELD_UPLOAD_TIME, 1700000000000L);

        R<io.milvus.grpc.MutationResult> resp = mock(R.class);
        when(resp.getStatus()).thenReturn(R.Status.Success.getCode());
        try {
            when(milvusServiceClient.insert(any(InsertParam.class))).thenReturn(resp);
            when(milvusServiceClient.delete(any(DeleteParam.class))).thenReturn(resp);
        } catch (Exception e) {
            // ignore
        }

        assertDoesNotThrow(() -> milvusService.insertVector(1L, vector, meta));
    }

    @Test
    @DisplayName("insertVector: Milvus 异常时抛业务异常")
    void insertVector_milvusThrowsThrowsBusinessException() throws Exception {
        List<Float> vector = List.of(0.1f);
        when(milvusServiceClient.delete(any(DeleteParam.class))).thenThrow(new RuntimeException("Milvus down"));

        assertThrows(BusinessException.class,
                () -> milvusService.insertVector(1L, vector, new HashMap<>()));
    }

    @Test
    @DisplayName("deleteVector: fileId 为 null 直接返回")
    void deleteVector_nullFileIdReturns() {
        assertDoesNotThrow(() -> milvusService.deleteVector(null));
    }

    @Test
    @DisplayName("deleteVector: 删除成功")
    void deleteVector_success() throws Exception {
        R<io.milvus.grpc.MutationResult> resp = mock(R.class);
        when(resp.getStatus()).thenReturn(R.Status.Success.getCode());
        when(milvusServiceClient.delete(any(DeleteParam.class))).thenReturn(resp);

        assertDoesNotThrow(() -> milvusService.deleteVector(1L));
    }

    @Test
    @DisplayName("deleteVector: 异常时抛业务异常")
    void deleteVector_exceptionThrowsBusinessException() throws Exception {
        when(milvusServiceClient.delete(any(DeleteParam.class))).thenThrow(new RuntimeException("delete fail"));
        assertThrows(BusinessException.class, () -> milvusService.deleteVector(1L));
    }

    @Test
    @DisplayName("vectorSearch: 查询向量为空返回空列表")
    void vectorSearch_emptyVectorReturnsEmpty() {
        List<VectorSearchResultDTO> result = milvusService.vectorSearch(Collections.emptyList(), 10, null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("vectorSearch: 查询为 null 返回空列表")
    void vectorSearch_nullVectorReturnsEmpty() {
        List<VectorSearchResultDTO> result = milvusService.vectorSearch(null, 10, null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("vectorSearch: 检索异常时返回空列表（降级）")
    void vectorSearch_exceptionReturnsEmpty() throws Exception {
        when(milvusServiceClient.search(any(SearchParam.class))).thenThrow(new RuntimeException("search fail"));
        List<VectorSearchResultDTO> result = milvusService.vectorSearch(List.of(0.1f), 10, null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("vectorSearch: 检索失败状态返回空列表")
    void vectorSearch_failedStatusReturnsEmpty() throws Exception {
        R<io.milvus.grpc.SearchResults> resp = mock(R.class);
        when(resp.getStatus()).thenReturn(R.Status.UnexpectedError.getCode());
        when(resp.getMessage()).thenReturn("search error");
        when(milvusServiceClient.search(any(SearchParam.class))).thenReturn(resp);

        List<VectorSearchResultDTO> result = milvusService.vectorSearch(List.of(0.1f), 10, null);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("vectorSearch: 带过滤表达式构造 SearchParam")
    void vectorSearch_withFilter() throws Exception {
        R<io.milvus.grpc.SearchResults> resp = mock(R.class);
        when(resp.getStatus()).thenReturn(R.Status.Success.getCode());
        when(resp.getData()).thenReturn(null);
        when(milvusServiceClient.search(any(SearchParam.class))).thenReturn(resp);

        List<VectorSearchResultDTO> result = milvusService.vectorSearch(List.of(0.1f), 5, "target_id == 100");
        assertNotNull(result);
    }

    @Test
    @DisplayName("buildFilter: targetId 为 null 返回 null")
    void buildFilter_nullTargetIdReturnsNull() {
        assertNull(MilvusService.buildFilter(null));
    }

    @Test
    @DisplayName("buildFilter: targetId 非空返回过滤表达式")
    void buildFilter_nonNullTargetIdReturnsExpr() {
        String filter = MilvusService.buildFilter(100L);
        assertEquals("target_id == 100", filter);
    }
}
