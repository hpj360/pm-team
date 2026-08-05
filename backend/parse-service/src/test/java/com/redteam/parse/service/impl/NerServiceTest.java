package com.redteam.parse.service.impl;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import cn.hutool.json.JSONUtil;
import com.redteam.parse.dto.NerEntityVO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * NER 实体识别服务单元测试
 *
 * <p>覆盖以下六类路径：</p>
 * <ol>
 *   <li>模型加载成功路径（mock DJL Criteria）</li>
 *   <li>模型加载失败降级路径</li>
 *   <li>模型推理成功路径</li>
 *   <li>模型推理异常降级路径</li>
 *   <li>定时重试恢复路径</li>
 *   <li>正则兜底基本功能验证</li>
 * </ol>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NerServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private NerServiceImpl nerService;

    @TempDir
    Path tempDir;

    /**
     * 测试前初始化：手动构造 NerServiceImpl，注入 SimpleMeterRegistry
     */
    @BeforeEach
    void setUp() {
        nerService = new NerServiceImpl(redisTemplate, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(nerService, "useRegexFallback", true);
        ReflectionTestUtils.setField(nerService, "modelPath", "models/security-bert");
        ReflectionTestUtils.setField(nerService, "cacheTtlSeconds", 3600L);
        ReflectionTestUtils.setField(nerService, "confidenceThreshold", 0.7f);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== 1. 模型加载成功路径（mock DJL Criteria） ====================

    @Nested
    @DisplayName("模型加载成功路径")
    class ModelLoadSuccessTest {

        @Test
        @DisplayName("preloadModel: Criteria 加载成功后 modelReady=true")
        void preloadModel_criteriaLoadSuccess_setsModelReady() throws Exception {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", tempDir.toString());

            try (MockedStatic<Criteria> mockedCriteria = mockStatic(Criteria.class)) {
                Criteria.Builder<String, List<NerEntityVO>> builder = mock(Criteria.Builder.class);
                Criteria<String, List<NerEntityVO>> criteria = mock(Criteria.class);
                ZooModel<String, List<NerEntityVO>> model = mock(ZooModel.class);
                Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);

                mockedCriteria.when(() -> Criteria.<String, List<NerEntityVO>>builder()).thenReturn(builder);
                doReturn(builder).when(builder).setTypes(any(), any());
                doReturn(builder).when(builder).optModelPath(any(Path.class));
                doReturn(builder).when(builder).optEngine(anyString());
                doReturn(builder).when(builder).optTranslator(any());
                doReturn(builder).when(builder).optOption(anyString(), anyString());
                doReturn(criteria).when(builder).build();
                doReturn(model).when(criteria).loadModel();
                doReturn(predictor).when(model).newPredictor();

                nerService.preloadModel();

                assertTrue(nerService.isModelReady());
            }
        }

        @Test
        @DisplayName("preloadModel: 加载成功后 predictor 不为空")
        void preloadModel_criteriaLoadSuccess_predictorNotNull() throws Exception {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", tempDir.toString());

            try (MockedStatic<Criteria> mockedCriteria = mockStatic(Criteria.class)) {
                Criteria.Builder<String, List<NerEntityVO>> builder = mock(Criteria.Builder.class);
                Criteria<String, List<NerEntityVO>> criteria = mock(Criteria.class);
                ZooModel<String, List<NerEntityVO>> model = mock(ZooModel.class);
                Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);

                mockedCriteria.when(() -> Criteria.<String, List<NerEntityVO>>builder()).thenReturn(builder);
                doReturn(builder).when(builder).setTypes(any(), any());
                doReturn(builder).when(builder).optModelPath(any(Path.class));
                doReturn(builder).when(builder).optEngine(anyString());
                doReturn(builder).when(builder).optTranslator(any());
                doReturn(builder).when(builder).optOption(anyString(), anyString());
                doReturn(criteria).when(builder).build();
                doReturn(model).when(criteria).loadModel();
                doReturn(predictor).when(model).newPredictor();

                nerService.preloadModel();

                assertNotNull(nerService.getPredictorForTest());
            }
        }

        @Test
        @DisplayName("preloadModel: 加载成功后 lastError 为 null 且 modelLoadFailed=false")
        void preloadModel_criteriaLoadSuccess_clearsErrorState() throws Exception {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", tempDir.toString());

            try (MockedStatic<Criteria> mockedCriteria = mockStatic(Criteria.class)) {
                Criteria.Builder<String, List<NerEntityVO>> builder = mock(Criteria.Builder.class);
                Criteria<String, List<NerEntityVO>> criteria = mock(Criteria.class);
                ZooModel<String, List<NerEntityVO>> model = mock(ZooModel.class);
                Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);

                mockedCriteria.when(() -> Criteria.<String, List<NerEntityVO>>builder()).thenReturn(builder);
                doReturn(builder).when(builder).setTypes(any(), any());
                doReturn(builder).when(builder).optModelPath(any(Path.class));
                doReturn(builder).when(builder).optEngine(anyString());
                doReturn(builder).when(builder).optTranslator(any());
                doReturn(builder).when(builder).optOption(anyString(), anyString());
                doReturn(criteria).when(builder).build();
                doReturn(model).when(criteria).loadModel();
                doReturn(predictor).when(model).newPredictor();

                nerService.preloadModel();

                assertFalse(nerService.isModelLoadFailed());
                Map<String, Object> status = nerService.getModelStatus();
                assertNull(status.get("lastError"));
                assertEquals("READY", status.get("status"));
            }
        }
    }

    // ==================== 2. 模型加载失败降级路径 ====================

    @Nested
    @DisplayName("模型加载失败降级路径")
    class ModelLoadFailureTest {

        @Test
        @DisplayName("preloadModel: 模型路径不存在时降级到正则")
        void preloadModel_modelPathNotExists_degradesToRegex() {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", "/nonexistent/model/path");

            nerService.preloadModel();

            assertTrue(nerService.isModelLoadFailed());
            assertFalse(nerService.isModelReady());
        }

        @Test
        @DisplayName("preloadModel: 加载失败后 lastError 包含错误信息")
        void preloadModel_loadFails_setsLastError() {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", "/nonexistent/model/path");

            nerService.preloadModel();

            Map<String, Object> status = nerService.getModelStatus();
            assertNotNull(status.get("lastError"));
            assertTrue(((String) status.get("lastError")).contains("模型路径不存在"));
        }

        @Test
        @DisplayName("preloadModel: 加载失败后 getModelStatus 返回 FAILED")
        void preloadModel_loadFails_statusIsFailed() {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", "/nonexistent/model/path");

            nerService.preloadModel();

            Map<String, Object> status = nerService.getModelStatus();
            assertEquals("FAILED", status.get("status"));
            assertEquals("/nonexistent/model/path", status.get("modelPath"));
        }
    }

    // ==================== 3. 模型推理成功路径 ====================

    @Nested
    @DisplayName("模型推理成功路径")
    class ModelInferenceSuccessTest {

        @Test
        @DisplayName("extractEntities: 模型就绪时返回模型推理结果")
        void extractEntities_modelReady_returnsModelResults() throws TranslateException {
            Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);
            List<NerEntityVO> mockEntities = List.of(
                    buildVO("192.168.1.1", "IP", 0.95f),
                    buildVO("example.com", "DOMAIN", 0.88f)
            );
            when(predictor.predict(anyString())).thenReturn(mockEntities);

            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            nerService.setPredictor(predictor);
            ReflectionTestUtils.setField(nerService, "modelReady", true);

            List<NerEntityVO> result = nerService.extractEntities("some text with entities");

            assertEquals(2, result.size());
            assertEquals("192.168.1.1", result.get(0).getEntityText());
            assertEquals("example.com", result.get(1).getEntityText());
        }

        @Test
        @DisplayName("extractEntities: 模型推理结果按置信度阈值过滤")
        void extractEntities_modelReady_filtersLowConfidence() throws TranslateException {
            Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);
            List<NerEntityVO> mockEntities = List.of(
                    buildVO("192.168.1.1", "IP", 0.95f),
                    buildVO("low.confidence.com", "DOMAIN", 0.5f),
                    buildVO("cve-2024-1234", "CVE", 0.85f)
            );
            when(predictor.predict(anyString())).thenReturn(mockEntities);

            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            nerService.setPredictor(predictor);
            ReflectionTestUtils.setField(nerService, "modelReady", true);

            List<NerEntityVO> result = nerService.extractEntities("some text");

            assertEquals(2, result.size());
            assertTrue(result.stream().noneMatch(e -> "low.confidence.com".equals(e.getEntityText())));
        }

        @Test
        @DisplayName("extractEntities: 模型推理成功时不触发降级计数器")
        void extractEntities_modelReady_noFallbackIncrement() throws TranslateException {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            NerServiceImpl service = new NerServiceImpl(redisTemplate, registry);
            ReflectionTestUtils.setField(service, "useRegexFallback", false);
            ReflectionTestUtils.setField(service, "confidenceThreshold", 0.7f);

            Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);
            when(predictor.predict(anyString())).thenReturn(List.of(buildVO("1.2.3.4", "IP", 0.9f)));
            service.setPredictor(predictor);
            ReflectionTestUtils.setField(service, "modelReady", true);

            service.extractEntities("text");

            assertEquals(0.0, registry.counter("ner_fallback_count_total").count());
            assertEquals(1.0, registry.counter("ner_inference_count_total").count());
        }
    }

    // ==================== 4. 模型推理异常降级路径 ====================

    @Nested
    @DisplayName("模型推理异常降级路径")
    class ModelInferenceExceptionTest {

        @Test
        @DisplayName("extractEntities: 模型推理抛异常时降级到正则")
        void extractEntities_modelThrows_degradesToRegex() throws TranslateException {
            Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);
            when(predictor.predict(anyString())).thenThrow(new TranslateException("inference error"));

            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            nerService.setPredictor(predictor);
            ReflectionTestUtils.setField(nerService, "modelReady", true);

            List<NerEntityVO> result = nerService.extractEntities("ip 10.0.0.1 here");

            assertFalse(result.isEmpty());
            assertTrue(result.stream().anyMatch(e -> "IP".equals(e.getEntityType())));
        }

        @Test
        @DisplayName("extractEntities: 模型推理异常后 modelReady 设为 false")
        void extractEntities_modelThrows_setsModelReadyFalse() throws TranslateException {
            Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);
            when(predictor.predict(anyString())).thenThrow(new TranslateException("inference error"));

            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            nerService.setPredictor(predictor);
            ReflectionTestUtils.setField(nerService, "modelReady", true);

            nerService.extractEntities("some text");

            assertFalse(nerService.isModelReady());
        }

        @Test
        @DisplayName("extractEntities: 模型推理异常时递增降级计数器")
        void extractEntities_modelThrows_incrementsFallbackCounter() throws TranslateException {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            NerServiceImpl service = new NerServiceImpl(redisTemplate, registry);
            ReflectionTestUtils.setField(service, "useRegexFallback", false);
            ReflectionTestUtils.setField(service, "confidenceThreshold", 0.7f);

            Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);
            when(predictor.predict(anyString())).thenThrow(new TranslateException("error"));
            service.setPredictor(predictor);
            ReflectionTestUtils.setField(service, "modelReady", true);

            service.extractEntities("text 1.2.3.4");
            service.extractEntities("more text 5.6.7.8");

            assertEquals(2.0, registry.counter("ner_fallback_count_total").count());
            assertEquals(2.0, registry.counter("ner_inference_count_total").count());
        }
    }

    // ==================== 5. 定时重试恢复路径 ====================

    @Nested
    @DisplayName("定时重试恢复路径")
    class RetryModelLoadTest {

        @Test
        @DisplayName("retryModelLoad: modelLoadFailed=false 时不执行重试")
        void retryModelLoad_whenNotFailed_doesNothing() {
            ReflectionTestUtils.setField(nerService, "modelLoadFailed", false);
            ReflectionTestUtils.setField(nerService, "modelReady", false);

            nerService.retryModelLoad();

            assertFalse(nerService.isModelLoadFailed());
            assertFalse(nerService.isModelReady());
        }

        @Test
        @DisplayName("retryModelLoad: 重试成功后恢复 modelReady=true")
        void retryModelLoad_whenFailedAndSucceeds_recoversModel() throws Exception {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", tempDir.toString());
            ReflectionTestUtils.setField(nerService, "modelLoadFailed", true);
            ReflectionTestUtils.setField(nerService, "modelReady", false);

            try (MockedStatic<Criteria> mockedCriteria = mockStatic(Criteria.class)) {
                Criteria.Builder<String, List<NerEntityVO>> builder = mock(Criteria.Builder.class);
                Criteria<String, List<NerEntityVO>> criteria = mock(Criteria.class);
                ZooModel<String, List<NerEntityVO>> model = mock(ZooModel.class);
                Predictor<String, List<NerEntityVO>> predictor = mock(Predictor.class);

                mockedCriteria.when(() -> Criteria.<String, List<NerEntityVO>>builder()).thenReturn(builder);
                doReturn(builder).when(builder).setTypes(any(), any());
                doReturn(builder).when(builder).optModelPath(any(Path.class));
                doReturn(builder).when(builder).optEngine(anyString());
                doReturn(builder).when(builder).optTranslator(any());
                doReturn(builder).when(builder).optOption(anyString(), anyString());
                doReturn(criteria).when(builder).build();
                doReturn(model).when(criteria).loadModel();
                doReturn(predictor).when(model).newPredictor();

                nerService.retryModelLoad();

                assertTrue(nerService.isModelReady());
                assertFalse(nerService.isModelLoadFailed());
                Map<String, Object> status = nerService.getModelStatus();
                assertEquals("READY", status.get("status"));
                assertNull(status.get("lastError"));
            }
        }

        @Test
        @DisplayName("retryModelLoad: 重试失败后更新 lastError 且保持 modelLoadFailed=true")
        void retryModelLoad_whenFailedAndFails_updatesLastError() {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", "/still/nonexistent/path");
            ReflectionTestUtils.setField(nerService, "modelLoadFailed", true);
            ReflectionTestUtils.setField(nerService, "modelReady", false);

            nerService.retryModelLoad();

            assertTrue(nerService.isModelLoadFailed());
            assertFalse(nerService.isModelReady());
            Map<String, Object> status = nerService.getModelStatus();
            assertNotNull(status.get("lastError"));
            assertEquals("FAILED", status.get("status"));
        }
    }

    // ==================== 6. 正则兜底基本功能验证 ====================

    @Nested
    @DisplayName("正则兜底基本功能验证")
    class RegexFallbackTest {

        @Test
        @DisplayName("getModelStatus: 正则兜底模式返回 FALLBACK")
        void getModelStatus_fallbackMode_returnsFallback() {
            Map<String, Object> status = nerService.getModelStatus();

            assertEquals("FALLBACK", status.get("status"));
            assertEquals("models/security-bert", status.get("modelPath"));
            assertNull(status.get("lastError"));
        }

        @Test
        @DisplayName("getModelStatus: 模型加载失败后返回 FAILED")
        void getModelStatus_failedMode_returnsFailed() {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", "/nonexistent/path");
            nerService.preloadModel();

            Map<String, Object> status = nerService.getModelStatus();
            assertEquals("FAILED", status.get("status"));
        }

        @Test
        @DisplayName("getModelStatus: 模型就绪时返回 READY")
        void getModelStatus_readyMode_returnsReady() {
            ReflectionTestUtils.setField(nerService, "modelReady", true);

            Map<String, Object> status = nerService.getModelStatus();
            assertEquals("READY", status.get("status"));
        }

        @Test
        @DisplayName("extractEntities: 正则兜底识别多种实体类型")
        void extractEntities_regexFallback_multipleEntityTypes() {
            String text = "server 10.0.0.1, domain evil.com, url https://bad.com/p, email a@b.com, hash d41d8cd98f00b204e9800998ecf8427e, CVE-2024-12345";
            List<NerEntityVO> result = nerService.extractEntities(text);

            assertTrue(result.stream().anyMatch(e -> "IP".equals(e.getEntityType())));
            assertTrue(result.stream().anyMatch(e -> "DOMAIN".equals(e.getEntityType())));
            assertTrue(result.stream().anyMatch(e -> "URL".equals(e.getEntityType())));
            assertTrue(result.stream().anyMatch(e -> "EMAIL".equals(e.getEntityType())));
            assertTrue(result.stream().anyMatch(e -> "HASH_MD5".equals(e.getEntityType())));
            assertTrue(result.stream().anyMatch(e -> "CVE".equals(e.getEntityType())));
        }

        @Test
        @DisplayName("extractEntities: 正则兜底空文本返回空列表")
        void extractEntities_regexFallback_blankText_returnsEmpty() {
            assertTrue(nerService.extractEntities("").isEmpty());
            assertTrue(nerService.extractEntities(null).isEmpty());
            assertTrue(nerService.extractEntities("   ").isEmpty());
        }

        @Test
        @DisplayName("extractEntities: 正则兜底无匹配实体返回空列表")
        void extractEntities_regexFallback_noEntities_returnsEmpty() {
            List<NerEntityVO> result = nerService.extractEntities("这是一段没有安全实体的普通文本内容");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("extractEntities: 正则兜底 URL 优先于 DOMAIN")
        void extractEntities_regexFallback_urlPrioritizedOverDomain() {
            List<NerEntityVO> result = nerService.extractEntities("visit https://example.com/page now");
            List<NerEntityVO> urls = result.stream().filter(e -> "URL".equals(e.getEntityType())).toList();
            List<NerEntityVO> domains = result.stream().filter(e -> "DOMAIN".equals(e.getEntityType())).toList();
            assertEquals(1, urls.size());
            assertTrue(domains.isEmpty());
        }

        @Test
        @DisplayName("extractEntities: 正则兜底 SHA256 优先于 MD5")
        void extractEntities_regexFallback_sha256PrioritizedOverMd5() {
            String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            List<NerEntityVO> result = nerService.extractEntities("hash " + sha256);
            List<NerEntityVO> sha256Entities = result.stream().filter(e -> "HASH_SHA256".equals(e.getEntityType())).toList();
            List<NerEntityVO> md5Entities = result.stream().filter(e -> "HASH_MD5".equals(e.getEntityType())).toList();
            assertEquals(1, sha256Entities.size());
            assertTrue(md5Entities.isEmpty());
        }

        @Test
        @DisplayName("extractEntities: 正则兜底 startPos/endPos 正确")
        void extractEntities_regexFallback_positionsCorrect() {
            String text = "ip 192.168.1.1 end";
            List<NerEntityVO> result = nerService.extractEntities(text);
            List<NerEntityVO> ips = result.stream().filter(e -> "IP".equals(e.getEntityType())).toList();
            assertEquals(1, ips.size());
            NerEntityVO ip = ips.get(0);
            assertEquals(text.substring(ip.getStartPos(), ip.getEndPos()), ip.getEntityText());
        }
    }

    // ==================== 缓存与文件读取（保留原有测试） ====================

    @Nested
    @DisplayName("文件读取与缓存")
    class CacheAndFileTest {

        @Test
        @DisplayName("extractEntitiesFromFile: 参数非法返回空列表")
        void extractEntitiesFromFile_invalidParams_returnsEmpty() {
            assertTrue(nerService.extractEntitiesFromFile(null, "/tmp/x").isEmpty());
            assertTrue(nerService.extractEntitiesFromFile(1L, "").isEmpty());
        }

        @Test
        @DisplayName("extractEntitiesFromFile: 命中缓存直接返回")
        void extractEntitiesFromFile_cacheHit() {
            when(valueOperations.get("ner:result:1")).thenReturn(
                    JSONUtil.toJsonStr(List.of(buildVO("1.2.3.4", "IP", 0.95f))));

            List<NerEntityVO> result = nerService.extractEntitiesFromFile(1L, "/tmp/x.txt");

            assertEquals(1, result.size());
            assertEquals("1.2.3.4", result.get(0).getEntityText());
        }

        @Test
        @DisplayName("extractEntitiesFromFile: 缓存未命中从文件读取并写缓存")
        void extractEntitiesFromFile_cacheMiss_readsFile() throws Exception {
            Path file = tempDir.resolve("test.txt");
            Files.writeString(file, "ip 192.168.1.1");
            when(valueOperations.get(anyString())).thenReturn(null);

            List<NerEntityVO> result = nerService.extractEntitiesFromFile(1L, file.toString());

            assertFalse(result.isEmpty());
            verify(valueOperations).set(eq("ner:result:1"), anyString(), eq(3600L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("extractEntitiesFromFile: 缓存读取异常降级返回空")
        void extractEntitiesFromFile_cacheReadError_returnsEmpty() {
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
            assertTrue(nerService.extractEntitiesFromFile(1L, "/tmp/nonexistent-xyz.txt").isEmpty());
        }

        @Test
        @DisplayName("extractEntitiesFromFile: 文件不存在返回空列表")
        void extractEntitiesFromFile_fileNotExists_returnsEmpty() {
            when(valueOperations.get(anyString())).thenReturn(null);
            assertTrue(nerService.extractEntitiesFromFile(1L, "/tmp/nonexistent-file-xyz.txt").isEmpty());
        }

        @Test
        @DisplayName("extractEntitiesFromFile: 缓存写入失败不影响返回")
        void extractEntitiesFromFile_cacheWriteFails_stillReturns() throws Exception {
            Path file = tempDir.resolve("test2.txt");
            Files.writeString(file, "ip 192.168.1.1");
            when(valueOperations.get(anyString())).thenReturn(null);
            doThrow(new RuntimeException("redis write fail"))
                    .when(valueOperations).set(anyString(), anyString(), anyLong(), any());

            List<NerEntityVO> result = nerService.extractEntitiesFromFile(1L, file.toString());
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("extractEntities: 模型未就绪时自动使用正则")
        void extractEntities_modelNotReady_usesRegex() {
            ReflectionTestUtils.setField(nerService, "useRegexFallback", false);
            ReflectionTestUtils.setField(nerService, "modelPath", "/nonexistent/path");
            nerService.preloadModel();
            List<NerEntityVO> result = nerService.extractEntities("ip 10.0.0.1");
            assertFalse(result.isEmpty());
        }

        @Test
        @DisplayName("extractEntities: 超大文本截断后仍能识别实体")
        void extractEntities_largeTextTruncated_stillExtracts() {
            StringBuilder sb = new StringBuilder();
            sb.append("192.168.1.1 ");
            for (int i = 0; i < 5_000; i++) {
                sb.append(' ');
            }
            sb.append("CVE-2024-12345");
            List<NerEntityVO> result = nerService.extractEntities(sb.toString());
            assertFalse(result.isEmpty());
            assertTrue(result.stream().anyMatch(e -> "IP".equals(e.getEntityType())));
            assertTrue(result.stream().anyMatch(e -> "CVE".equals(e.getEntityType())));
        }
    }

    // ==================== 指标埋点验证 ====================

    @Nested
    @DisplayName("Prometheus 指标埋点")
    class MetricsTest {

        @Test
        @DisplayName("extractEntities: 正则兜底时递增 inference_count_total")
        void extractEntities_regexFallback_incrementsInferenceCount() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            NerServiceImpl service = new NerServiceImpl(redisTemplate, registry);
            ReflectionTestUtils.setField(service, "useRegexFallback", true);
            ReflectionTestUtils.setField(service, "confidenceThreshold", 0.7f);

            service.extractEntities("ip 1.2.3.4");
            service.extractEntities("ip 5.6.7.8");

            assertEquals(2.0, registry.counter("ner_inference_count_total").count());
        }

        @Test
        @DisplayName("extractEntities: 正则兜底时递增 fallback_count_total")
        void extractEntities_regexFallback_incrementsFallbackCount() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            NerServiceImpl service = new NerServiceImpl(redisTemplate, registry);
            ReflectionTestUtils.setField(service, "useRegexFallback", true);
            ReflectionTestUtils.setField(service, "confidenceThreshold", 0.7f);

            service.extractEntities("ip 1.2.3.4");

            assertEquals(1.0, registry.counter("ner_fallback_count_total").count());
        }

        @Test
        @DisplayName("extractEntities: 推理延迟被记录到 timer")
        void extractEntities_latencyRecorded() {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            NerServiceImpl service = new NerServiceImpl(redisTemplate, registry);
            ReflectionTestUtils.setField(service, "useRegexFallback", true);
            ReflectionTestUtils.setField(service, "confidenceThreshold", 0.7f);

            service.extractEntities("ip 1.2.3.4");

            assertTrue(registry.timer("ner_inference_latency_seconds").count() >= 1);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 NER 实体 VO
     *
     * @param text       实体文本
     * @param type       实体类型
     * @param confidence 置信度
     * @return NerEntityVO 实例
     */
    private NerEntityVO buildVO(String text, String type, float confidence) {
        NerEntityVO vo = new NerEntityVO();
        vo.setEntityText(text);
        vo.setEntityType(type);
        vo.setConfidence(confidence);
        return vo;
    }

    /**
     * 构造 NER 实体 VO（默认置信度 0.95）
     *
     * @param text 实体文本
     * @param type 实体类型
     * @return NerEntityVO 实例
     */
    private NerEntityVO buildVO(String text, String type) {
        return buildVO(text, type, 0.95f);
    }
}
