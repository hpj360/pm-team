package com.redteam.parse.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.exception.BusinessException;
import com.redteam.parse.dto.NerEntityVO;
import com.redteam.parse.dto.ParseQueryDTO;
import com.redteam.parse.dto.ParseResultDTO;
import com.redteam.parse.dto.YaraMatchVO;
import com.redteam.parse.entity.NerResultEntity;
import com.redteam.parse.entity.ParseResultEntity;
import com.redteam.parse.mapper.NerResultMapper;
import com.redteam.parse.mapper.ParseResultMapper;
import com.redteam.parse.parser.TikaFileParser;
import com.redteam.parse.producer.FileParsedEventProducer;
import com.redteam.parse.producer.ParseEventProducer;
import com.redteam.parse.service.impl.FileParseServiceImpl;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 文件解析服务实现单元测试
 *
 * <p>覆盖同步解析、异步解析（幂等）、查询、分页、YARA/NER 降级等场景。
 * MinIO 与 Tika 均通过 Mock 注入。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileParseServiceImplTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private TikaFileParser tikaFileParser;

    @Mock
    private YaraScanService yaraScanService;

    @Mock
    private NerService nerService;

    @Mock
    private ParseResultMapper parseResultMapper;

    @Mock
    private NerResultMapper nerResultMapper;

    @Mock
    private ParseEventProducer parseEventProducer;

    @Mock
    private FileParsedEventProducer fileParsedEventProducer;

    @Mock
    private TagRecognitionTask tagRecognitionTask;

    @InjectMocks
    private FileParseServiceImpl fileParseService;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(fileParseService, "bucketName", "redteam-files");
        ReflectionTestUtils.setField(fileParseService, "tempDir", "/tmp/parse-test");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * 模拟 MinIO 返回文本流
     */
    private void mockMinioStream(String content) throws Exception {
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(response.readAllBytes()).thenReturn(content.getBytes());
        doReturn(content.getBytes().length).when(response).read(any(byte[].class));
        when(minioClient.getObject(any())).thenReturn(response);
    }

    /**
     * 构造 Tika 解析成功结果
     */
    private ParseResultDTO buildTikaSuccess() {
        ParseResultDTO dto = ParseResultDTO.success("Hello 192.168.1.1 world");
        dto.setPageCount(1);
        dto.setEncoding("UTF-8");
        dto.setLanguage("en");
        dto.setDuration(100L);
        dto.setParseDurationMs(100L);
        return dto;
    }

    /**
     * 构造已持久化解析结果实体
     */
    private ParseResultEntity buildEntity(Long fileId, String status) {
        ParseResultEntity entity = new ParseResultEntity();
        entity.setId(1L);
        entity.setFileId(fileId);
        entity.setFileName("test.txt");
        entity.setFileType("txt");
        entity.setFileSize(100L);
        entity.setTextContent("content");
        entity.setParseStatus(status);
        entity.setParseDurationMs(100L);
        entity.setCreateTime(LocalDateTime.now());
        return entity;
    }

    // ==================== parseFile(fileId) ====================

    @Test
    @DisplayName("parseFile(fileId): fileId 为空抛业务异常")
    void parseFileByFileId_nullId_throwsException() {
        assertThrows(BusinessException.class, () -> fileParseService.parseFile((Long) null));
    }

    @Test
    @DisplayName("parseFile(fileId): 命中已解析结果直接返回")
    void parseFileByFileId_existingSuccess_returnsDirectly() {
        when(parseResultMapper.selectOne(any())).thenReturn(buildEntity(1L, "SUCCESS"));

        ParseResultDTO result = fileParseService.parseFile(1L);

        assertEquals(1L, result.getFileId());
        assertEquals("SUCCESS", result.getParseStatus());
        assertTrue(result.getSuccess());
    }

    @Test
    @DisplayName("parseFile(fileId): 不存在且非 SUCCESS 抛业务异常")
    void parseFileByFileId_notFound_throwsException() {
        when(parseResultMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> fileParseService.parseFile(1L));
    }

    // ==================== parseFile(storagePath,...) ====================

    @Test
    @DisplayName("parseFile(storagePath): 解析成功，YARA/NER/持久化/事件全链路")
    void parseFileByPath_success() throws Exception {
        String text = "Hello 192.168.1.1 world CVE-2024-12345";
        mockMinioStream(text);
        when(tikaFileParser.parse(any(), eq("test.txt"))).thenReturn(buildTikaSuccess());
        when(yaraScanService.scanText(any(), anyString())).thenReturn(List.of(new YaraMatchVO()));
        when(nerService.extractEntities(anyString())).thenReturn(List.of(buildNerVO()));

        ParseResultDTO result = fileParseService.parseFile("path/test.txt", "test.txt", "txt");

        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals("SUCCESS", result.getParseStatus());
        assertNotNull(result.getTextHash());
        assertEquals(1, result.getYaraMatches().size());
        assertEquals(1, result.getNerEntities().size());
        verify(parseEventProducer).sendFileParsedEvent(any());
        verify(parseEventProducer, never()).sendFileParseFailedEvent(any(), any());
    }

    @Test
    @DisplayName("parseFile(storagePath): Tika 失败标记为 FAILED 并发失败事件")
    void parseFileByPath_tikaFails_marksFailed() throws Exception {
        mockMinioStream("content");
        ParseResultDTO tikaFail = ParseResultDTO.fail("tika error");
        when(tikaFileParser.parse(any(), anyString())).thenReturn(tikaFail);

        ParseResultDTO result = fileParseService.parseFile("path/x.txt", "x.txt", "txt");

        assertFalse(result.getSuccess());
        assertEquals("FAILED", result.getParseStatus());
        verify(parseEventProducer).sendFileParseFailedEvent(any(), anyString());
        verify(parseEventProducer, never()).sendFileParsedEvent(any());
    }

    @Test
    @DisplayName("parseFile(storagePath): YARA 扫描失败降级不影响主流程")
    void parseFileByPath_yaraFails_degrades() throws Exception {
        mockMinioStream("content");
        when(tikaFileParser.parse(any(), anyString())).thenReturn(buildTikaSuccess());
        when(yaraScanService.scanText(any(), anyString())).thenThrow(new RuntimeException("yara down"));
        when(nerService.extractEntities(anyString())).thenReturn(Collections.emptyList());

        ParseResultDTO result = fileParseService.parseFile("path/x.txt", "x.txt", "txt");

        assertTrue(result.getSuccess());
        assertEquals("SUCCESS", result.getParseStatus());
        assertNull(result.getYaraMatches());
        verify(parseEventProducer).sendFileParsedEvent(any());
    }

    @Test
    @DisplayName("parseFile(storagePath): NER 识别失败降级不影响主流程")
    void parseFileByPath_nerFails_degrades() throws Exception {
        mockMinioStream("content");
        when(tikaFileParser.parse(any(), anyString())).thenReturn(buildTikaSuccess());
        when(yaraScanService.scanText(any(), anyString())).thenReturn(Collections.emptyList());
        when(nerService.extractEntities(anyString())).thenThrow(new RuntimeException("ner down"));

        ParseResultDTO result = fileParseService.parseFile("path/x.txt", "x.txt", "txt");

        assertTrue(result.getSuccess());
        assertEquals("SUCCESS", result.getParseStatus());
        assertNull(result.getNerEntities());
    }

    @Test
    @DisplayName("parseFile(storagePath): MinIO 异常标记为 FAILED")
    void parseFileByPath_minioFails_marksFailed() throws Exception {
        when(minioClient.getObject(any())).thenThrow(new RuntimeException("minio down"));

        ParseResultDTO result = fileParseService.parseFile("path/x.txt", "x.txt", "txt");

        assertFalse(result.getSuccess());
        assertEquals("FAILED", result.getParseStatus());
        verify(parseEventProducer).sendFileParseFailedEvent(any(), anyString());
    }

    @Test
    @DisplayName("parseFile(storagePath): 缓存写入失败不影响结果")
    void parseFileByPath_cacheWriteFails() throws Exception {
        mockMinioStream("content");
        when(tikaFileParser.parse(any(), anyString())).thenReturn(buildTikaSuccess());
        when(yaraScanService.scanText(any(), anyString())).thenReturn(Collections.emptyList());
        when(nerService.extractEntities(anyString())).thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("redis down"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any());

        ParseResultDTO result = fileParseService.parseFile("path/x.txt", "x.txt", "txt");
        assertTrue(result.getSuccess());
    }

    // ==================== parseFileAsync(fileId) ====================

    @Test
    @DisplayName("parseFileAsync(fileId): fileId 为空跳过")
    void parseFileAsyncByFileId_nullId_skips() {
        fileParseService.parseFileAsync((Long) null);
        // 仅记录日志，无异常
    }

    @Test
    @DisplayName("parseFileAsync(fileId, ...): 参数非法跳过")
    void parseFileAsync_nullParams_skips() {
        fileParseService.parseFileAsync(null, "path", "name", "txt", 1L);
        fileParseService.parseFileAsync(1L, "", "name", "txt", 1L);
    }

    @Test
    @DisplayName("parseFileAsync(fileId, ...): 已成功解析跳过幂等")
    void parseFileAsync_alreadyParsed_skips() {
        when(parseResultMapper.selectOne(any())).thenReturn(buildEntity(1L, "SUCCESS"));
        fileParseService.parseFileAsync(1L, "path", "name", "txt", 1L);
        verify(tikaFileParser, never()).parse(any(), anyString());
    }

    @Test
    @DisplayName("parseFileAsync(fileId, ...): 未解析触发解析流程")
    void parseFileAsync_notParsed_triggersParse() throws Exception {
        mockMinioStream("content");
        when(parseResultMapper.selectOne(any())).thenReturn(null);
        when(tikaFileParser.parse(any(), anyString())).thenReturn(buildTikaSuccess());
        when(yaraScanService.scanText(any(), anyString())).thenReturn(Collections.emptyList());
        when(nerService.extractEntities(anyString())).thenReturn(Collections.emptyList());

        fileParseService.parseFileAsync(1L, "path/x.txt", "x.txt", "txt", 100L);

        verify(tikaFileParser).parse(any(), eq("x.txt"));
    }

    // ==================== getParseResult ====================

    @Test
    @DisplayName("getParseResult: fileId 为空抛业务异常")
    void getParseResult_nullId_throwsException() {
        assertThrows(BusinessException.class, () -> fileParseService.getParseResult(null));
    }

    @Test
    @DisplayName("getParseResult: 不存在抛业务异常")
    void getParseResult_notFound_throwsException() {
        when(parseResultMapper.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> fileParseService.getParseResult(1L));
    }

    @Test
    @DisplayName("getParseResult: 返回结果并附带 NER 实体")
    void getParseResult_success_withNerEntities() {
        when(parseResultMapper.selectOne(any())).thenReturn(buildEntity(1L, "SUCCESS"));
        NerResultEntity ner = new NerResultEntity();
        ner.setFileId(1L);
        ner.setEntityText("1.2.3.4");
        ner.setEntityType("IP");
        when(nerResultMapper.selectList(any())).thenReturn(List.of(ner));

        ParseResultDTO result = fileParseService.getParseResult(1L);

        assertEquals(1L, result.getFileId());
        assertEquals(1, result.getNerEntities().size());
        assertEquals("1.2.3.4", result.getNerEntities().get(0).getEntityText());
    }

    // ==================== listParseResults ====================

    @Test
    @DisplayName("listParseResults: 默认分页查询")
    @SuppressWarnings("unchecked")
    void listParseResults_default() {
        Page<ParseResultEntity> page = new Page<>(1, 10);
        page.setTotal(1L);
        page.setRecords(List.of(buildEntity(1L, "SUCCESS")));
        when(parseResultMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);
        when(nerResultMapper.selectList(any())).thenReturn(Collections.emptyList());

        ParseQueryDTO query = new ParseQueryDTO();
        var result = fileParseService.listParseResults(query);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("listParseResults: 带过滤条件")
    @SuppressWarnings("unchecked")
    void listParseResults_withFilters() {
        Page<ParseResultEntity> page = new Page<>(1, 10);
        page.setTotal(0L);
        page.setRecords(Collections.emptyList());
        when(parseResultMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        ParseQueryDTO query = new ParseQueryDTO();
        query.setFileId(1L);
        query.setFileName("test");
        query.setParseStatus("SUCCESS");
        query.setPageNum(1);
        query.setPageSize(10);
        var result = fileParseService.listParseResults(query);

        assertEquals(0L, result.getTotal());
    }

    @Test
    @DisplayName("listParseResults: pageSize 超过 100 被截断")
    @SuppressWarnings("unchecked")
    void listParseResults_pageSizeCapped() {
        Page<ParseResultEntity> page = new Page<>(1, 100);
        page.setTotal(0L);
        page.setRecords(Collections.emptyList());
        when(parseResultMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        ParseQueryDTO query = new ParseQueryDTO();
        query.setPageSize(500);
        fileParseService.listParseResults(query);

        ArgumentCaptor<Page<ParseResultEntity>> captor = ArgumentCaptor.forClass(Page.class);
        verify(parseResultMapper).selectPage(captor.capture(), any(Wrapper.class));
        assertEquals(100L, captor.getValue().getSize());
    }

    @Test
    @DisplayName("listParseResults: null query 使用默认分页")
    @SuppressWarnings("unchecked")
    void listParseResults_nullQuery() {
        Page<ParseResultEntity> page = new Page<>(1, 10);
        page.setTotal(0L);
        page.setRecords(Collections.emptyList());
        when(parseResultMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        var result = fileParseService.listParseResults(null);
        assertEquals(0L, result.getTotal());
    }

    // ==================== 辅助 ====================

    /**
     * 构造 NER 实体 VO
     */
    private NerEntityVO buildNerVO() {
        NerEntityVO vo = new NerEntityVO();
        vo.setEntityText("192.168.1.1");
        vo.setEntityType("IP");
        vo.setStartPos(6);
        vo.setEndPos(17);
        vo.setConfidence(0.95f);
        return vo;
    }
}
