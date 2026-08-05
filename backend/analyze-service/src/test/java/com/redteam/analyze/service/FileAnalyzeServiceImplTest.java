package com.redteam.analyze.service;

import cn.hutool.json.JSONUtil;
import com.redteam.analyze.entity.AnalyzeResultEntity;
import com.redteam.analyze.entity.AnalyzeTaskEntity;
import com.redteam.analyze.mapper.AnalyzeResultMapper;
import com.redteam.analyze.mapper.AnalyzeTaskMapper;
import com.redteam.analyze.producer.AnalyzeEventProducer;
import com.redteam.analyze.service.impl.FileAnalyzeServiceImpl;
import com.redteam.common.api.dto.AnalyzeResultDTO;
import com.redteam.common.api.dto.FileAnalyzeDTO;
import com.redteam.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 文件分析服务单元测试
 *
 * <p>覆盖关键词提取、实体识别、情感分析、摘要、向量嵌入、主流程、异步任务、降级等场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileAnalyzeServiceImplTest {

    @Mock
    private AnalyzeResultMapper analyzeResultMapper;

    @Mock
    private AnalyzeTaskMapper analyzeTaskMapper;

    @Mock
    private AnalyzeEventProducer analyzeEventProducer;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private FileAnalyzeServiceImpl fileAnalyzeService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileAnalyzeService, "embeddingApiUrl", "http://localhost:8083/api/search/embed");
        ReflectionTestUtils.setField(fileAnalyzeService, "embeddingEnabled", false);
        ReflectionTestUtils.setField(fileAnalyzeService, "embeddingCacheTtl", 86400L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== extractKeywords ====================

    @Test
    @DisplayName("extractKeywords: 空文本返回空列表")
    void extractKeywords_blankText_returnsEmpty() {
        assertTrue(fileAnalyzeService.extractKeywords("", 10).isEmpty());
        assertTrue(fileAnalyzeService.extractKeywords(null, 10).isEmpty());
    }

    @Test
    @DisplayName("extractKeywords: 英文文本词频统计")
    void extractKeywords_englishText_returnsByFrequency() {
        List<AnalyzeResultDTO.KeywordInfo> result =
                fileAnalyzeService.extractKeywords("security attack security malware security", 5);
        assertFalse(result.isEmpty());
        AnalyzeResultDTO.KeywordInfo top = result.get(0);
        assertEquals("security", top.getKeyword());
        assertEquals(3, top.getFrequency());
        assertEquals(1.0, top.getWeight());
    }

    @Test
    @DisplayName("extractKeywords: 中文文本二元组分词")
    void extractKeywords_chineseText_returnsBigrams() {
        List<AnalyzeResultDTO.KeywordInfo> result =
                fileAnalyzeService.extractKeywords("恶意攻击检测恶意攻击", 5);
        assertFalse(result.isEmpty());
        // "恶意" 和 "攻击" 应出现
        assertTrue(result.stream().anyMatch(k -> "恶意".equals(k.getKeyword())));
        assertTrue(result.stream().anyMatch(k -> "攻击".equals(k.getKeyword())));
    }

    @Test
    @DisplayName("extractKeywords: 停用词被过滤")
    void extractKeywords_stopWordsFiltered() {
        List<AnalyzeResultDTO.KeywordInfo> result =
                fileAnalyzeService.extractKeywords("the security and the attack", 5);
        assertTrue(result.stream().noneMatch(k -> "the".equals(k.getKeyword())));
        assertTrue(result.stream().noneMatch(k -> "and".equals(k.getKeyword())));
    }

    @Test
    @DisplayName("extractKeywords: topN 限制返回数量")
    void extractKeywords_topNLimitsResult() {
        List<AnalyzeResultDTO.KeywordInfo> result =
                fileAnalyzeService.extractKeywords("alpha beta gamma delta epsilon alpha beta gamma", 2);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("extractKeywords: topN 为空或非正数时默认 10")
    void extractKeywords_nullTopN_defaultsTo10() {
        List<AnalyzeResultDTO.KeywordInfo> result =
                fileAnalyzeService.extractKeywords("alpha alpha", null);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("extractKeywords: 权重归一化到 [0,1]")
    void extractKeywords_weightNormalized() {
        List<AnalyzeResultDTO.KeywordInfo> result =
                fileAnalyzeService.extractKeywords("alpha alpha beta", 5);
        AnalyzeResultDTO.KeywordInfo alpha = result.stream()
                .filter(k -> "alpha".equals(k.getKeyword())).findFirst().orElse(null);
        assertNotNull(alpha);
        assertEquals(1.0, alpha.getWeight());
        AnalyzeResultDTO.KeywordInfo beta = result.stream()
                .filter(k -> "beta".equals(k.getKeyword())).findFirst().orElse(null);
        assertNotNull(beta);
        assertTrue(beta.getWeight() > 0 && beta.getWeight() < 1.0);
    }

    // ==================== recognizeEntities ====================

    @Test
    @DisplayName("recognizeEntities: 空文本返回空列表")
    void recognizeEntities_blankText_returnsEmpty() {
        assertTrue(fileAnalyzeService.recognizeEntities("").isEmpty());
        assertTrue(fileAnalyzeService.recognizeEntities(null).isEmpty());
    }

    @Test
    @DisplayName("recognizeEntities: 识别 IP 地址")
    void recognizeEntities_ip() {
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("server 192.168.1.1 online");
        assertTrue(result.stream().anyMatch(e -> e.getType() == 1 && "192.168.1.1".equals(e.getName())));
    }

    @Test
    @DisplayName("recognizeEntities: 识别 URL")
    void recognizeEntities_url() {
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("visit https://example.com/path now");
        assertTrue(result.stream().anyMatch(e -> e.getType() == 3 && "https://example.com/path".equals(e.getName())));
    }

    @Test
    @DisplayName("recognizeEntities: 识别邮箱")
    void recognizeEntities_email() {
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("contact admin@redteam.com");
        assertTrue(result.stream().anyMatch(e -> e.getType() == 4 && "admin@redteam.com".equals(e.getName())));
    }

    @Test
    @DisplayName("recognizeEntities: 识别 SHA256（优先于 MD5）")
    void recognizeEntities_sha256() {
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("hash " + sha256);
        assertTrue(result.stream().anyMatch(e -> e.getType() == 6 && sha256.equals(e.getName())));
    }

    @Test
    @DisplayName("recognizeEntities: 识别 MD5")
    void recognizeEntities_md5() {
        String md5 = "d41d8cd98f00b204e9800998ecf8427e";
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("md5 " + md5);
        assertTrue(result.stream().anyMatch(e -> e.getType() == 5 && md5.equals(e.getName())));
    }

    @Test
    @DisplayName("recognizeEntities: 识别 CVE 编号")
    void recognizeEntities_cve() {
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("漏洞 CVE-2024-12345 影响");
        assertTrue(result.stream().anyMatch(e -> e.getType() == 7 && "CVE-2024-12345".equals(e.getName())));
    }

    @Test
    @DisplayName("recognizeEntities: 识别域名")
    void recognizeEntities_domain() {
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("访问 www.example.com.cn 站点");
        assertTrue(result.stream().anyMatch(e -> e.getType() == 2 && "www.example.com.cn".equals(e.getName())));
    }

    @Test
    @DisplayName("recognizeEntities: URL 优先于 DOMAIN，避免重复识别")
    void recognizeEntities_urlPrioritizedOverDomain() {
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("see https://example.com/page");
        long urlCount = result.stream().filter(e -> e.getType() == 3).count();
        long domainCount = result.stream().filter(e -> e.getType() == 2).count();
        assertEquals(1, urlCount);
        assertEquals(0, domainCount);
    }

    @Test
    @DisplayName("recognizeEntities: 相同实体聚合计数")
    void recognizeEntities_aggregatesCount() {
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("ip 10.0.0.1 and 10.0.0.1 again 10.0.0.1");
        AnalyzeResultDTO.EntityInfo ip = result.stream().filter(e -> e.getType() == 1).findFirst().orElse(null);
        assertNotNull(ip);
        assertEquals(3, ip.getCount());
    }

    // ==================== analyzeSentiment ====================

    @Test
    @DisplayName("analyzeSentiment: 空文本返回中性")
    void analyzeSentiment_blankText_returnsNeutral() {
        AnalyzeResultDTO.SentimentInfo info = fileAnalyzeService.analyzeSentiment("");
        assertEquals(3, info.getSentiment());
        assertEquals(0.0, info.getScore());
    }

    @Test
    @DisplayName("analyzeSentiment: 正面文本")
    void analyzeSentiment_positiveText() {
        AnalyzeResultDTO.SentimentInfo info = fileAnalyzeService.analyzeSentiment("this is good and excellent success");
        assertEquals(1, info.getSentiment());
        assertTrue(info.getScore() > 0);
    }

    @Test
    @DisplayName("analyzeSentiment: 负面文本")
    void analyzeSentiment_negativeText() {
        AnalyzeResultDTO.SentimentInfo info = fileAnalyzeService.analyzeSentiment("attack malware virus danger risk");
        assertEquals(2, info.getSentiment());
        assertTrue(info.getScore() < 0);
    }

    @Test
    @DisplayName("analyzeSentiment: 中性文本（无情感词）")
    void analyzeSentiment_neutralText() {
        AnalyzeResultDTO.SentimentInfo info = fileAnalyzeService.analyzeSentiment("system running normally process data");
        assertEquals(3, info.getSentiment());
        assertEquals(0.0, info.getScore());
    }

    @Test
    @DisplayName("analyzeSentiment: 混合情感按得分判定")
    void analyzeSentiment_mixedText() {
        AnalyzeResultDTO.SentimentInfo info = fileAnalyzeService.analyzeSentiment("good success attack malware");
        // 正面2 负面2，得分0，中性
        assertEquals(3, info.getSentiment());
        assertEquals(0.0, info.getScore());
        assertTrue(info.getConfidence() > 0);
    }

    // ==================== generateSummary ====================

    @Test
    @DisplayName("generateSummary: 空文本返回空串")
    void generateSummary_blankText_returnsEmpty() {
        assertEquals("", fileAnalyzeService.generateSummary("", 200));
        assertEquals("", fileAnalyzeService.generateSummary(null, 200));
    }

    @Test
    @DisplayName("generateSummary: 短文本原样返回")
    void generateSummary_shortText_returnsAsIs() {
        String text = "short text content";
        assertEquals(text, fileAnalyzeService.generateSummary(text, 200));
    }

    @Test
    @DisplayName("generateSummary: 长文本多句子生成摘要")
    void generateSummary_longText_generatesSummary() {
        String text = "这是第一句关于安全的内容。第二句是关于攻击的描述。第三句是普通内容没什么信息量。"
                + "第四句讨论漏洞利用。第五句总结安全建议。";
        String summary = fileAnalyzeService.generateSummary(text, 200);
        assertNotNull(summary);
        assertTrue(summary.length() <= 203);
    }

    @Test
    @DisplayName("generateSummary: 单句长文本截断")
    void generateSummary_singleLongSentence_truncated() {
        String text = "a".repeat(500);
        String summary = fileAnalyzeService.generateSummary(text, 100);
        assertTrue(summary.length() <= 103);
    }

    @Test
    @DisplayName("generateSummary: length 为空默认 200")
    void generateSummary_nullLength_defaultsTo200() {
        String text = "a".repeat(300);
        String summary = fileAnalyzeService.generateSummary(text, null);
        assertNotNull(summary);
    }

    // ==================== generateEmbedding / batchGenerateEmbedding ====================

    @Test
    @DisplayName("generateEmbedding: 空文本返回 UUID")
    void generateEmbedding_blankText_returnsUuid() {
        String id = fileAnalyzeService.generateEmbedding("");
        assertNotNull(id);
        assertFalse(id.isEmpty());
    }

    @Test
    @DisplayName("generateEmbedding: 禁用 HTTP 时本地降级返回 UUID 并缓存")
    void generateEmbedding_disabledHttp_localFallback() {
        String id = fileAnalyzeService.generateEmbedding("some text content");
        assertNotNull(id);
        verify(valueOperations).set(eq("analyze:embedding:" + id), anyString(), eq(86400L), any());
    }

    @Test
    @DisplayName("generateEmbedding: Redis 写入失败仍返回 ID")
    void generateEmbedding_redisFails_stillReturnsId() {
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        String id = fileAnalyzeService.generateEmbedding("some text content");
        assertNotNull(id);
    }

    @Test
    @DisplayName("generateEmbedding: 启用 HTTP 但远程失败降级到本地")
    void generateEmbedding_enabledHttp_remoteFailsFallback() {
        ReflectionTestUtils.setField(fileAnalyzeService, "embeddingEnabled", true);
        String id = fileAnalyzeService.generateEmbedding("some text content");
        assertNotNull(id);
        // 远程不可达，降级到本地，会写 Redis
        verify(valueOperations).set(eq("analyze:embedding:" + id), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("batchGenerateEmbedding: 空列表返回空")
    void batchGenerateEmbedding_emptyList_returnsEmpty() {
        assertTrue(fileAnalyzeService.batchGenerateEmbedding(List.of()).isEmpty());
        assertTrue(fileAnalyzeService.batchGenerateEmbedding(null).isEmpty());
    }

    @Test
    @DisplayName("batchGenerateEmbedding: 多文本逐条生成")
    void batchGenerateEmbedding_multipleTexts() {
        List<String> ids = fileAnalyzeService.batchGenerateEmbedding(Arrays.asList("text1", "text2", "text3"));
        assertEquals(3, ids.size());
        ids.forEach(id -> assertNotNull(id));
    }

    // ==================== extractSensitiveInfo ====================

    @Test
    @DisplayName("extractSensitiveInfo: 提取邮箱与 IP")
    void extractSensitiveInfo_emailAndIp() {
        List<AnalyzeResultDTO.SensitiveInfo> result =
                fileAnalyzeService.extractSensitiveInfo("mail admin@test.com ip 10.1.1.1");
        assertTrue(result.stream().anyMatch(s -> s.getType() == 1));
        assertTrue(result.stream().anyMatch(s -> s.getType() == 5));
    }

    @Test
    @DisplayName("extractSensitiveInfo: 空文本返回空")
    void extractSensitiveInfo_blankText_returnsEmpty() {
        assertTrue(fileAnalyzeService.extractSensitiveInfo("").isEmpty());
    }

    // ==================== analyze 主流程 ====================

    @Test
    @DisplayName("analyze: fileId 为空抛业务异常")
    void analyze_nullFileId_throwsException() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        assertThrows(BusinessException.class, () -> fileAnalyzeService.analyze(dto));
    }

    @Test
    @DisplayName("analyze: 全文分析（analyzeType=5）填充所有字段")
    void analyze_fullAnalysis_populatesAllFields() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(5);
        dto.setTextContent("contact admin@test.com ip 192.168.1.1, this is good security");

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertEquals(2, result.getStatus());
        assertEquals(100, result.getProgress());
        assertNotNull(result.getSensitiveInfos());
        assertNotNull(result.getKeywords());
        assertNotNull(result.getEntities());
        assertNotNull(result.getSentiment());
        assertNotNull(result.getSummary());
        assertNotNull(result.getDuration());
        assertNotNull(result.getCreateTime());
        assertNotNull(result.getFinishTime());
    }

    @Test
    @DisplayName("analyze: 单一分析类型（关键词）")
    void analyze_singleType_keywords() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(2);
        dto.setTextContent("security security attack malware");

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertEquals(2, result.getStatus());
        assertNotNull(result.getKeywords());
        assertNull(result.getSensitiveInfos());
        assertNull(result.getEntities());
    }

    @Test
    @DisplayName("analyze: 生成向量嵌入")
    void analyze_withEmbedding_setsEmbeddingId() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(5);
        dto.setTextContent("some text");
        dto.setGenerateEmbedding(Boolean.TRUE);

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertNotNull(result.getEmbeddingId());
    }

    @Test
    @DisplayName("analyze: 文件路径读取失败时状态为失败")
    void analyze_filePathUnreadable_statusFailed() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(5);
        dto.setFilePath("/nonexistent/path/file.txt");

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertEquals(3, result.getStatus());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("analyze: 从文件路径读取内容")
    void analyze_fromFilePath_usesFileContent() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "contact admin@test.com ip 192.168.1.1");
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(1);
        dto.setFilePath(file.toString());

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertEquals(2, result.getStatus());
        assertFalse(result.getSensitiveInfos().isEmpty());
    }

    // ==================== analyzeAsync ====================

    @Test
    @DisplayName("analyzeAsync: 创建任务并发送请求事件")
    void analyzeAsync_createsTaskAndSendsEvent() {
        when(analyzeTaskMapper.insert(any(AnalyzeTaskEntity.class))).thenAnswer(inv -> {
            AnalyzeTaskEntity t = inv.getArgument(0);
            t.setId(100L);
            return 1;
        });
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(5);
        dto.setGenerateEmbedding(Boolean.TRUE);

        Long taskId = fileAnalyzeService.analyzeAsync(dto);

        assertEquals(100L, taskId);
        verify(analyzeTaskMapper).insert(any(AnalyzeTaskEntity.class));
        verify(analyzeEventProducer).sendAnalyzeRequestEvent(100L, 1L, 5);
    }

    @Test
    @DisplayName("analyzeAsync: fileId 为空抛异常")
    void analyzeAsync_nullFileId_throwsException() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        assertThrows(BusinessException.class, () -> fileAnalyzeService.analyzeAsync(dto));
    }

    // ==================== getAnalyzeResult ====================

    @Test
    @DisplayName("getAnalyzeResult: 任务存在返回 DTO")
    void getAnalyzeResult_found_returnsDto() {
        AnalyzeResultEntity entity = new AnalyzeResultEntity();
        entity.setTaskId(10L);
        entity.setFileId(1L);
        entity.setAnalyzeType(5);
        entity.setStatus(2);
        entity.setProgress(100);
        entity.setDuration(500L);
        AnalyzeResultDTO inner = new AnalyzeResultDTO();
        inner.setSummary("test summary");
        entity.setResultJson(JSONUtil.toJsonStr(inner));

        when(analyzeResultMapper.selectOne(any())).thenReturn(entity);

        AnalyzeResultDTO result = fileAnalyzeService.getAnalyzeResult(10L);

        assertEquals(10L, result.getTaskId());
        assertEquals(1L, result.getFileId());
        assertEquals(2, result.getStatus());
        assertEquals("test summary", result.getSummary());
    }

    @Test
    @DisplayName("getAnalyzeResult: 任务不存在抛异常")
    void getAnalyzeResult_notFound_throwsException() {
        when(analyzeResultMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> fileAnalyzeService.getAnalyzeResult(999L));
    }

    @Test
    @DisplayName("getAnalyzeResult: taskId 为空抛异常")
    void getAnalyzeResult_nullTaskId_throwsException() {
        assertThrows(BusinessException.class, () -> fileAnalyzeService.getAnalyzeResult(null));
    }

    // ==================== processAnalyzeTask ====================

    @Test
    @DisplayName("processAnalyzeTask: 任务不存在直接返回")
    void processAnalyzeTask_taskNotFound_returns() {
        when(analyzeTaskMapper.selectById(999L)).thenReturn(null);
        fileAnalyzeService.processAnalyzeTask(999L);
        verify(analyzeResultMapper, never()).insert(any());
    }

    @Test
    @DisplayName("processAnalyzeTask: 已完成任务跳过")
    void processAnalyzeTask_completedTask_skips() {
        AnalyzeTaskEntity task = new AnalyzeTaskEntity();
        task.setId(1L);
        task.setStatus(2);
        when(analyzeTaskMapper.selectById(1L)).thenReturn(task);

        fileAnalyzeService.processAnalyzeTask(1L);

        verify(analyzeTaskMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("processAnalyzeTask: 成功处理持久化结果并发送完成事件")
    void processAnalyzeTask_success_persistsAndSendsCompleted() {
        AnalyzeTaskEntity task = new AnalyzeTaskEntity();
        task.setId(1L);
        task.setFileId(100L);
        task.setAnalyzeType(5);
        task.setStatus(0);
        task.setTextContent("contact admin@test.com good security");
        task.setGenerateEmbedding(0);
        when(analyzeTaskMapper.selectById(1L)).thenReturn(task);
        when(analyzeResultMapper.insert(any())).thenReturn(1);

        fileAnalyzeService.processAnalyzeTask(1L);

        ArgumentCaptor<AnalyzeResultEntity> captor = ArgumentCaptor.forClass(AnalyzeResultEntity.class);
        verify(analyzeResultMapper).insert(captor.capture());
        AnalyzeResultEntity saved = captor.getValue();
        assertEquals(1L, saved.getTaskId());
        assertEquals(100L, saved.getFileId());
        verify(analyzeEventProducer).sendAnalyzeCompletedEvent(eq(1L), eq(100L), anyLong(), any());
    }

    @Test
    @DisplayName("processAnalyzeTask: taskId 为空直接返回")
    void processAnalyzeTask_nullTaskId_returns() {
        fileAnalyzeService.processAnalyzeTask(null);
        verify(analyzeTaskMapper, never()).selectById(any());
    }

    // ==================== 补充边界与组合场景测试 ====================

    @Test
    @DisplayName("extractSensitiveInfo: 提取手机号")
    void extractSensitiveInfo_phone() {
        List<AnalyzeResultDTO.SensitiveInfo> result =
                fileAnalyzeService.extractSensitiveInfo("联系电话 13800138000");
        assertTrue(result.stream().anyMatch(s -> s.getType() == 2));
    }

    @Test
    @DisplayName("extractSensitiveInfo: 提取身份证号")
    void extractSensitiveInfo_idCard() {
        List<AnalyzeResultDTO.SensitiveInfo> result =
                fileAnalyzeService.extractSensitiveInfo("身份证 110101199003077895");
        assertTrue(result.stream().anyMatch(s -> s.getType() == 3));
    }

    @Test
    @DisplayName("extractSensitiveInfo: 提取域名")
    void extractSensitiveInfo_domain() {
        List<AnalyzeResultDTO.SensitiveInfo> result =
                fileAnalyzeService.extractSensitiveInfo("访问 www.example.com.cn 站点");
        assertTrue(result.stream().anyMatch(s -> s.getType() == 6));
    }

    @Test
    @DisplayName("recognizeEntities: 多类型实体混合识别")
    void recognizeEntities_mixedEntities() {
        String text = "server 192.168.1.1 contact admin@test.com visit https://example.com cve CVE-2024-12345";
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities(text);
        // 至少识别出 IP、邮箱、URL、CVE 四类
        assertTrue(result.stream().anyMatch(e -> e.getType() == 1));
        assertTrue(result.stream().anyMatch(e -> e.getType() == 3));
        assertTrue(result.stream().anyMatch(e -> e.getType() == 4));
        assertTrue(result.stream().anyMatch(e -> e.getType() == 7));
    }

    @Test
    @DisplayName("recognizeEntities: SHA256 优先于 MD5（64 位哈希不被识别为 MD5）")
    void recognizeEntities_sha256NotMisclassifiedAsMd5() {
        String sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        List<AnalyzeResultDTO.EntityInfo> result = fileAnalyzeService.recognizeEntities("hash " + sha256);
        // SHA256 占用区间后，不会被 MD5 二次识别
        long md5Count = result.stream().filter(e -> e.getType() == 5).count();
        assertEquals(0, md5Count);
    }

    @Test
    @DisplayName("analyze: analyzeType=null 走全文分析分支")
    void analyze_nullAnalyzeType_fullAnalysis() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(null);
        dto.setTextContent("contact admin@test.com ip 192.168.1.1 good");

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertEquals(2, result.getStatus());
        assertNotNull(result.getSensitiveInfos());
        assertNotNull(result.getKeywords());
        assertNotNull(result.getEntities());
    }

    @Test
    @DisplayName("analyze: analyzeType=3 实体识别分支")
    void analyze_type3_entitiesOnly() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(3);
        dto.setTextContent("server 192.168.1.1 online");

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertEquals(2, result.getStatus());
        assertNotNull(result.getEntities());
        assertNull(result.getSensitiveInfos());
        assertNull(result.getKeywords());
    }

    @Test
    @DisplayName("analyze: analyzeType=4 情感分析分支")
    void analyze_type4_sentimentOnly() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(4);
        dto.setTextContent("this is good and excellent");

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertEquals(2, result.getStatus());
        assertNotNull(result.getSentiment());
        assertEquals(1, result.getSentiment().getSentiment());
        assertNull(result.getKeywords());
    }

    @Test
    @DisplayName("analyze: analyzeType=1 敏感信息分支")
    void analyze_type1_sensitiveOnly() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(1);
        dto.setTextContent("mail admin@test.com ip 10.1.1.1");

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertEquals(2, result.getStatus());
        assertNotNull(result.getSensitiveInfos());
        assertFalse(result.getSensitiveInfos().isEmpty());
        assertNull(result.getKeywords());
    }

    @Test
    @DisplayName("analyze: textContent 与 filePath 同时存在时优先 textContent")
    void analyze_textContentPrioritizedOverFilePath() throws Exception {
        Path file = tempDir.resolve("ignored.txt");
        Files.writeString(file, "this content should be ignored");
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(2);
        dto.setTextContent("security attack malware");
        dto.setFilePath(file.toString());

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        // 关键词来自 textContent，不应包含 ignored
        assertTrue(result.getKeywords().stream().noneMatch(k -> k.getKeyword().contains("ignored")));
        assertTrue(result.getKeywords().stream().anyMatch(k -> "security".equals(k.getKeyword())));
    }

    @Test
    @DisplayName("analyze: 未提供文本且未提供路径时分析空文本")
    void analyze_noTextNoFilePath_returnsEmptyResults() {
        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(1L);
        dto.setAnalyzeType(5);

        AnalyzeResultDTO result = fileAnalyzeService.analyze(dto);

        assertEquals(2, result.getStatus());
        assertNotNull(result.getKeywords());
        assertTrue(result.getKeywords().isEmpty());
    }

    @Test
    @DisplayName("generateEmbedding: 相同文本生成不同 UUID（本地降级非确定性）")
    void generateEmbedding_sameTextDifferentCalls_returnsDifferentIds() {
        String id1 = fileAnalyzeService.generateEmbedding("duplicate text content");
        String id2 = fileAnalyzeService.generateEmbedding("duplicate text content");
        assertNotEquals(id1, id2);
    }

    @Test
    @DisplayName("batchGenerateEmbedding: 包含空字符串的列表正常处理")
    void batchGenerateEmbedding_withEmptyString_handled() {
        List<String> ids = fileAnalyzeService.batchGenerateEmbedding(Arrays.asList("text1", "", "text3"));
        assertEquals(3, ids.size());
        ids.forEach(id -> assertNotNull(id));
    }
}
