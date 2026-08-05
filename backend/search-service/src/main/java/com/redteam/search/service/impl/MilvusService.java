package com.redteam.search.service.impl;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.search.config.SearchProperties;
import com.redteam.search.dto.VectorSearchResultDTO;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量检索服务实现
 *
 * <p>负责 collection 创建、向量增删、向量检索。
 * 索引类型 IVF_FLAT（nlist=1024），度量类型 COSINE，检索 nprobe=16。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusService {

    /**
     * 字段名：file_id
     */
    public static final String FIELD_FILE_ID = "file_id";
    /**
     * 字段名：file_name
     */
    public static final String FIELD_FILE_NAME = "file_name";
    /**
     * 字段名：file_sm3
     */
    public static final String FIELD_FILE_SM3 = "file_sm3";
    /**
     * 字段名：embedding
     */
    public static final String FIELD_EMBEDDING = "embedding";
    /**
     * 字段名：target_id
     */
    public static final String FIELD_TARGET_ID = "target_id";
    /**
     * 字段名：upload_time
     */
    public static final String FIELD_UPLOAD_TIME = "upload_time";

    private final MilvusServiceClient milvusServiceClient;
    private final SearchProperties searchProperties;

    /**
     * 启动时初始化 collection
     */
    @PostConstruct
    public void init() {
        try {
            createCollectionIfNotExists();
        } catch (Exception e) {
            log.error("Milvus collection 初始化失败，将在后续操作中重试", e);
        }
    }

    /**
     * 创建 collection（含索引）并加载到内存
     *
     * @throws Exception 与 Milvus 通信异常
     */
    public void createCollectionIfNotExists() throws Exception {
        SearchProperties.Milvus conf = searchProperties.getMilvus();
        String collectionName = conf.getCollectionName();

        R<Boolean> hasResp = milvusServiceClient.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(collectionName).build());
        if (hasResp.getData() != null && hasResp.getData()) {
            log.info("Milvus collection 已存在: {}", collectionName);
            ensureLoaded(collectionName);
            return;
        }

        FieldType fileIdField = FieldType.newBuilder()
                .withName(FIELD_FILE_ID).withDataType(DataType.Int64)
                .withPrimaryKey(true).withAutoID(false).build();
        FieldType fileNameField = FieldType.newBuilder()
                .withName(FIELD_FILE_NAME).withDataType(DataType.VarChar)
                .withMaxLength(255).build();
        FieldType fileSm3Field = FieldType.newBuilder()
                .withName(FIELD_FILE_SM3).withDataType(DataType.VarChar)
                .withMaxLength(128).build();
        FieldType embeddingField = FieldType.newBuilder()
                .withName(FIELD_EMBEDDING).withDataType(DataType.FloatVector)
                .withDimension(conf.getVectorDim()).build();
        FieldType targetIdField = FieldType.newBuilder()
                .withName(FIELD_TARGET_ID).withDataType(DataType.Int64).build();
        FieldType uploadTimeField = FieldType.newBuilder()
                .withName(FIELD_UPLOAD_TIME).withDataType(DataType.Int64).build();

        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("红方文件向量索引")
                .withFieldTypes(List.of(fileIdField, fileNameField, fileSm3Field,
                        embeddingField, targetIdField, uploadTimeField))
                .withShardsNum(2)
                .build();
        milvusServiceClient.createCollection(createParam);

        // 创建向量索引
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(FIELD_EMBEDDING)
                .withIndexType(parseIndexType(conf.getIndexType()))
                .withMetricType(parseMetricType(conf.getMetricType()))
                .withExtraParam("{\"nlist\":" + conf.getNlist() + "}")
                .build();
        milvusServiceClient.createIndex(indexParam);

        // 加载到内存
        milvusServiceClient.loadCollection(
                LoadCollectionParam.newBuilder().withCollectionName(collectionName).build());

        log.info("Milvus collection 创建并加载成功: name={}, dim={}, indexType={}, metricType={}",
                collectionName, conf.getVectorDim(), conf.getIndexType(), conf.getMetricType());
    }

    /**
     * 确保 collection 已加载
     *
     * @param collectionName collection 名称
     */
    private void ensureLoaded(String collectionName) {
        try {
            milvusServiceClient.loadCollection(
                    LoadCollectionParam.newBuilder().withCollectionName(collectionName).build());
        } catch (Exception e) {
            log.debug("collection 已加载或加载失败（可忽略）: {}", e.getMessage());
        }
    }

    /**
     * 插入向量（覆盖语义：file_id 已存在则先删后插）
     *
     * @param fileId     文件 ID
     * @param embedding  向量
     * @param metadata   元数据（file_name / file_sm3 / target_id / upload_time）
     */
    public void insertVector(Long fileId, List<Float> embedding, Map<String, Object> metadata) {
        if (fileId == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "fileId 不能为空");
        }
        if (embedding == null || embedding.isEmpty()) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "embedding 不能为空");
        }
        SearchProperties.Milvus conf = searchProperties.getMilvus();
        // 幂等：先删除可能存在的旧向量
        deleteVectorQuietly(fileId);

        String fileName = metadata == null ? null : (String) metadata.get(FIELD_FILE_NAME);
        String fileSm3 = metadata == null ? null : (String) metadata.get(FIELD_FILE_SM3);
        Long targetId = metadata == null ? null : toLong(metadata.get(FIELD_TARGET_ID));
        Long uploadTime = metadata == null ? null : toLong(metadata.get(FIELD_UPLOAD_TIME));

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field(FIELD_FILE_ID, List.of(fileId)));
        fields.add(new InsertParam.Field(FIELD_FILE_NAME, List.of(fileName == null ? "" : fileName)));
        fields.add(new InsertParam.Field(FIELD_FILE_SM3, List.of(fileSm3 == null ? "" : fileSm3)));
        fields.add(new InsertParam.Field(FIELD_EMBEDDING, List.of(embedding)));
        fields.add(new InsertParam.Field(FIELD_TARGET_ID, List.of(targetId == null ? 0L : targetId)));
        fields.add(new InsertParam.Field(FIELD_UPLOAD_TIME, List.of(uploadTime == null ? 0L : uploadTime)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(conf.getCollectionName())
                .withFields(fields)
                .build();
        try {
            R<io.milvus.grpc.MutationResult> resp = milvusServiceClient.insert(insertParam);
            if (resp.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Milvus insert 失败: " + resp.getMessage());
            }
            log.info("Milvus 向量插入成功: fileId={}, dim={}", fileId, embedding.size());
        } catch (Exception e) {
            log.error("Milvus 向量插入失败: fileId={}", fileId, e);
            throw BusinessException.of(ResultCode.VECTOR_INDEX_ERROR, "Milvus 向量插入失败: " + e.getMessage());
        }
    }

    /**
     * 删除向量
     *
     * @param fileId 文件 ID
     */
    public void deleteVector(Long fileId) {
        if (fileId == null) {
            return;
        }
        SearchProperties.Milvus conf = searchProperties.getMilvus();
        DeleteParam deleteParam = DeleteParam.newBuilder()
                .withCollectionName(conf.getCollectionName())
                .withExpr(FIELD_FILE_ID + " == " + fileId)
                .build();
        try {
            R<io.milvus.grpc.MutationResult> resp = milvusServiceClient.delete(deleteParam);
            if (resp.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Milvus delete 失败: " + resp.getMessage());
            }
            log.info("Milvus 向量删除成功: fileId={}", fileId);
        } catch (Exception e) {
            log.error("Milvus 向量删除失败: fileId={}", fileId, e);
            throw BusinessException.of(ResultCode.INDEX_DELETE_ERROR, "Milvus 向量删除失败: " + e.getMessage());
        }
    }

    /**
     * 静默删除向量（用于 insert 前的幂等清理，失败不抛异常）
     *
     * @param fileId 文件 ID
     */
    private void deleteVectorQuietly(Long fileId) {
        try {
            SearchProperties.Milvus conf = searchProperties.getMilvus();
            milvusServiceClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(conf.getCollectionName())
                    .withExpr(FIELD_FILE_ID + " == " + fileId)
                    .build());
        } catch (Exception e) {
            log.debug("Milvus 静默删除失败（可忽略）: fileId={}, msg={}", fileId, e.getMessage());
        }
    }

    /**
     * 向量检索
     *
     * <p>使用 IVF_FLAT 索引，nprobe=16，度量类型 COSINE。
     * 返回结果按相似度降序排列，每条结果包含 fileId、score、metadata。</p>
     *
     * @param queryVector 查询向量
     * @param topK        返回数量
     * @param filter      过滤表达式（可为 null）
     * @return 向量检索结果列表
     */
    public List<VectorSearchResultDTO> vectorSearch(List<Float> queryVector, int topK, String filter) {
        if (queryVector == null || queryVector.isEmpty()) {
            return Collections.emptyList();
        }
        SearchProperties.Milvus conf = searchProperties.getMilvus();
        int effectiveTopK = topK <= 0 ? conf.getVectorDim() : topK;

        SearchParam.Builder searchBuilder = SearchParam.newBuilder()
                .withCollectionName(conf.getCollectionName())
                .withVectorFieldName(FIELD_EMBEDDING)
                .withVectors(List.of(queryVector))
                .withTopK(effectiveTopK)
                .withMetricType(parseMetricType(conf.getMetricType()))
                .withParams("{\"nprobe\":" + conf.getNprobe() + "}")
                .withOutFields(List.of(FIELD_FILE_NAME, FIELD_FILE_SM3, FIELD_TARGET_ID, FIELD_UPLOAD_TIME));

        if (filter != null && !filter.isBlank()) {
            searchBuilder.withExpr(filter);
        }

        try {
            R<SearchResults> response = milvusServiceClient.search(searchBuilder.build());
            if (response.getStatus() != R.Status.Success.getCode()) {
                log.error("Milvus 检索失败: {}", response.getMessage());
                return Collections.emptyList();
            }
            return parseSearchResults(response.getData(), filter);
        } catch (Exception e) {
            log.error("Milvus 检索异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 构造过滤表达式
     *
     * @param targetId 目标 ID（可为 null）
     * @return 过滤表达式
     */
    public static String buildFilter(Long targetId) {
        if (targetId == null) {
            return null;
        }
        return FIELD_TARGET_ID + " == " + targetId;
    }

    /**
     * 解析 Milvus 检索结果
     *
     * @param searchResults Milvus 原始结果
     * @param filter        过滤表达式
     * @return 结果列表
     */
    @SuppressWarnings("unchecked")
    private List<VectorSearchResultDTO> parseSearchResults(SearchResults searchResults, String filter) {
        if (searchResults == null || searchResults.getResults() == null) {
            return Collections.emptyList();
        }
        SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResults.getResults());
        List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);
        if (idScores == null || idScores.isEmpty()) {
            return Collections.emptyList();
        }

        // 预取字段数据
        List<?> fileNames = safeGetField(wrapper, FIELD_FILE_NAME);
        List<?> fileSm3s = safeGetField(wrapper, FIELD_FILE_SM3);
        List<?> targetIds = safeGetField(wrapper, FIELD_TARGET_ID);
        List<?> uploadTimes = safeGetField(wrapper, FIELD_UPLOAD_TIME);

        List<VectorSearchResultDTO> results = new ArrayList<>(idScores.size());
        for (int i = 0; i < idScores.size(); i++) {
            SearchResultsWrapper.IDScore iq = idScores.get(i);
            // 优先使用 longID；为 0 时回退到 strID 解析，兼容老版本数据
            long longId = iq.getLongID();
            Long fileId = longId > 0L ? longId : toLong(iq.getStrID());
            if (fileId == null || fileId <= 0L) {
                continue;
            }
            float score = iq.getScore();
            Map<String, Object> meta = new HashMap<>(4);
            meta.put(FIELD_FILE_NAME, getAtIndex(fileNames, i));
            meta.put(FIELD_FILE_SM3, getAtIndex(fileSm3s, i));
            meta.put(FIELD_TARGET_ID, toLong(getAtIndex(targetIds, i)));
            meta.put(FIELD_UPLOAD_TIME, toLong(getAtIndex(uploadTimes, i)));
            VectorSearchResultDTO dto = new VectorSearchResultDTO(fileId, score, meta);
            dto.setRank(i + 1);
            results.add(dto);
        }
        return results;
    }

    /**
     * 安全获取字段数据
     *
     * @param wrapper   SearchResultsWrapper
     * @param fieldName 字段名
     * @return 字段值列表
     */
    private List<?> safeGetField(SearchResultsWrapper wrapper, String fieldName) {
        try {
            return wrapper.getFieldData(fieldName, 0);
        } catch (Exception e) {
            log.debug("获取 Milvus 字段数据失败: field={}, msg={}", fieldName, e.getMessage());
            return Lists.newArrayList();
        }
    }

    /**
     * 列表安全取值
     *
     * @param list  列表
     * @param index 索引
     * @return 值
     */
    private Object getAtIndex(List<?> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        Object v = list.get(index);
        if (v instanceof JsonObject) {
            return ((JsonObject) v).toString();
        }
        return v;
    }

    /**
     * 解析索引类型
     *
     * @param indexType 索引类型字符串
     * @return IndexType
     */
    private IndexType parseIndexType(String indexType) {
        if (indexType == null) {
            return IndexType.IVF_FLAT;
        }
        try {
            return IndexType.valueOf(indexType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知索引类型，降级为 IVF_FLAT: {}", indexType);
            return IndexType.IVF_FLAT;
        }
    }

    /**
     * 解析度量类型
     *
     * @param metricType 度量类型字符串
     * @return MetricType
     */
    private MetricType parseMetricType(String metricType) {
        if (metricType == null) {
            return MetricType.COSINE;
        }
        try {
            return MetricType.valueOf(metricType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("未知度量类型，降级为 COSINE: {}", metricType);
            return MetricType.COSINE;
        }
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
}
