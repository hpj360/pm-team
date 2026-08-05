package com.redteam.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.report.dto.ReportGenerateDTO;
import com.redteam.report.dto.ReportQueryDTO;
import com.redteam.report.dto.ReportShareDTO;
import com.redteam.report.dto.ReportStatsDTO;
import com.redteam.report.dto.ReportTemplateVO;
import com.redteam.report.dto.ReportVO;
import com.redteam.report.entity.ReportEntity;
import com.redteam.report.entity.ReportTemplateEntity;
import com.redteam.report.mapper.ReportMapper;
import com.redteam.report.mapper.ReportTemplateMapper;
import com.redteam.report.service.impl.ReportServiceImpl;
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
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportServiceImpl} 单元测试
 *
 * <p>覆盖所有 public 方法及异步生成三种格式（PDF/Word/HTML）的核心路径，
 * 使用 Mockito 隔离 Mapper 与 TemplateEngine，使用 {@link TempDir} 隔离文件系统。</p>
 *
 * <p>测试覆盖率目标 ≥ 80%。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private ReportTemplateMapper reportTemplateMapper;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private ReportServiceImpl reportService;

    @TempDir
    Path tempDir;

    /**
     * 测试前的公共初始化：注入 @Value 字段、设置 self 代理、注入 TemplateEngine
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reportService, "outputPath", tempDir.toString() + File.separator);
        ReflectionTestUtils.setField(reportService, "pdfFont", "STSong-Light");
        ReflectionTestUtils.setField(reportService, "defaultTemplate", "task-summary");
        // 手动注入 TemplateEngine（@Autowired 字段不会被 @InjectMocks 自动注入）
        ReflectionTestUtils.setField(reportService, "templateEngine", templateEngine);
        // self 设为 mock，便于独立测试 generateReport 的入库逻辑
        ReflectionTestUtils.setField(reportService, "self", mock(ReportServiceImpl.class));
    }

    // ===================== generateReport =====================

    /**
     * 测试生成报告：入库 PENDING 状态并触发异步生成
     */
    @Test
    @DisplayName("生成报告 - 应入库并返回 PENDING 状态")
    void testGenerateReport() {
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportName("测试报告");
        dto.setReportType("TASK_SUMMARY");
        dto.setFormat("PDF");

        when(reportMapper.insert(any(ReportEntity.class))).thenAnswer(invocation -> {
            ReportEntity entity = invocation.getArgument(0);
            entity.setReportId("rpt-test-001");
            return 1;
        });

        ReportVO vo = reportService.generateReport(dto);

        assertNotNull(vo);
        assertEquals("PENDING", vo.getStatus());
        assertEquals("测试报告", vo.getReportName());
        assertEquals("TASK_SUMMARY", vo.getReportType());
        assertEquals("PDF", vo.getFormat());
        verify(reportMapper).insert(any(ReportEntity.class));
        ReportServiceImpl self = (ReportServiceImpl) ReflectionTestUtils.getField(reportService, "self");
        assertNotNull(self);
        verify(self).generateReportAsync(eq("rpt-test-001"));
    }

    /**
     * 测试生成报告 - 按 reportType 自动匹配模板
     */
    @Test
    @DisplayName("生成报告 - 未指定模板时按 reportType 自动匹配")
    void testGenerateReportAutoTemplate() {
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportName("渗透测试报告");
        dto.setReportType("PENETRATION_TEST");
        dto.setFormat("HTML");

        when(reportMapper.insert(any(ReportEntity.class))).thenReturn(1);

        ReportVO vo = reportService.generateReport(dto);

        ArgumentCaptor<ReportEntity> captor = ArgumentCaptor.forClass(ReportEntity.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals("penetration-test", captor.getValue().getTemplateId());
        assertNotNull(vo);
    }

    // ===================== generateReportAsync =====================

    /**
     * 测试异步生成 HTML 报告
     */
    @Test
    @DisplayName("异步生成 HTML 报告 - 应成功")
    void testGenerateReportAsyncHtml() {
        ReportEntity entity = buildEntity("rpt-html-001", "HTML");
        when(reportMapper.selectById("rpt-html-001")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);
        when(templateEngine.process(eq("task-summary"), any(Context.class)))
                .thenReturn("<html><body><h1>测试报告</h1></body></html>");

        reportService.generateReportAsync("rpt-html-001");

        assertEquals("COMPLETED", entity.getStatus());
        assertNotNull(entity.getFilePath());
        assertTrue(entity.getFileSize() > 0);
        assertNotNull(entity.getGeneratedAt());
        verify(reportMapper).selectById("rpt-html-001");
    }

    /**
     * 测试异步生成 Word 报告
     */
    @Test
    @DisplayName("异步生成 Word 报告 - 应成功")
    void testGenerateReportAsyncWord() {
        ReportEntity entity = buildEntity("rpt-word-001", "WORD");
        when(reportMapper.selectById("rpt-word-001")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);
        when(templateEngine.process(eq("task-summary"), any(Context.class)))
                .thenReturn("<html><body><h1>Word 测试</h1><p>段落内容</p></body></html>");

        reportService.generateReportAsync("rpt-word-001");

        assertEquals("COMPLETED", entity.getStatus());
        assertNotNull(entity.getFilePath());
        assertTrue(entity.getFileSize() > 0);
    }

    /**
     * 测试异步生成 PDF 报告
     */
    @Test
    @DisplayName("异步生成 PDF 报告 - 应成功")
    void testGenerateReportAsyncPdf() {
        ReportEntity entity = buildEntity("rpt-pdf-001", "PDF");
        when(reportMapper.selectById("rpt-pdf-001")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);
        when(templateEngine.process(eq("task-summary"), any(Context.class)))
                .thenReturn("<html><body><h1>PDF Test Report</h1><p>Content</p></body></html>");

        reportService.generateReportAsync("rpt-pdf-001");

        assertEquals("COMPLETED", entity.getStatus());
        assertNotNull(entity.getFilePath());
        assertTrue(entity.getFileSize() > 0);
    }

    /**
     * 测试异步生成报告 - 使用指定模板
     */
    @Test
    @DisplayName("异步生成 - 使用指定的 templateId 解析模板")
    void testGenerateReportAsyncWithTemplateId() {
        ReportEntity entity = buildEntity("rpt-tpl-001", "HTML");
        entity.setTemplateId("tpl-custom");
        ReportTemplateEntity template = new ReportTemplateEntity();
        template.setTemplateId("tpl-custom");
        template.setTemplatePath("custom-template");

        when(reportMapper.selectById("rpt-tpl-001")).thenReturn(entity);
        when(reportTemplateMapper.selectById("tpl-custom")).thenReturn(template);
        when(reportMapper.updateById(any())).thenReturn(1);
        when(templateEngine.process(eq("custom-template"), any(Context.class)))
                .thenReturn("<html><body>Custom</body></html>");

        reportService.generateReportAsync("rpt-tpl-001");

        assertEquals("COMPLETED", entity.getStatus());
    }

    /**
     * 测试异步生成报告 - 实体不存在时应直接返回
     */
    @Test
    @DisplayName("异步生成 - 报告不存在时安全返回")
    void testGenerateReportAsyncNotFound() {
        when(reportMapper.selectById("rpt-missing")).thenReturn(null);

        assertDoesNotThrow(() -> reportService.generateReportAsync("rpt-missing"));
        verify(reportMapper, never()).updateById(any());
    }

    /**
     * 测试异步生成报告 - 渲染异常时状态置为 FAILED
     */
    @Test
    @DisplayName("异步生成 - 渲染异常时状态应置为 FAILED")
    void testGenerateReportAsyncFailure() {
        ReportEntity entity = buildEntity("rpt-fail-001", "HTML");
        when(reportMapper.selectById("rpt-fail-001")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);
        when(templateEngine.process(anyString(), any(Context.class)))
                .thenThrow(new RuntimeException("模板渲染失败"));

        reportService.generateReportAsync("rpt-fail-001");

        assertEquals("FAILED", entity.getStatus());
        assertNotNull(entity.getGeneratedAt());
    }

    /**
     * 测试异步生成报告 - 不支持的格式应导致 FAILED
     */
    @Test
    @DisplayName("异步生成 - 不支持的格式应导致 FAILED 状态")
    void testGenerateReportAsyncUnsupportedFormat() {
        ReportEntity entity = buildEntity("rpt-bad-fmt", "EXCEL");
        when(reportMapper.selectById("rpt-bad-fmt")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        reportService.generateReportAsync("rpt-bad-fmt");

        assertEquals("FAILED", entity.getStatus());
    }

    // ===================== getReport =====================

    /**
     * 测试获取报告详情 - 成功
     */
    @Test
    @DisplayName("获取报告详情 - 应返回 VO")
    void testGetReport() {
        ReportEntity entity = buildEntity("rpt-get-001", "PDF");
        entity.setStatus("COMPLETED");
        when(reportMapper.selectById("rpt-get-001")).thenReturn(entity);

        ReportVO vo = reportService.getReport("rpt-get-001");

        assertNotNull(vo);
        assertEquals("rpt-get-001", vo.getReportId());
        assertEquals("测试报告", vo.getReportName());
        assertEquals("COMPLETED", vo.getStatus());
    }

    /**
     * 测试获取报告详情 - 不存在时应抛出异常
     */
    @Test
    @DisplayName("获取报告详情 - 不存在时应抛出 BusinessException")
    void testGetReportNotFound() {
        when(reportMapper.selectById("rpt-missing")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reportService.getReport("rpt-missing"));
        assertTrue(ex.getMessage().contains("报告不存在"));
    }

    // ===================== listReports =====================

    /**
     * 测试分页查询 - 带条件
     */
    @Test
    @DisplayName("分页查询报告 - 应返回分页结果")
    @SuppressWarnings("unchecked")
    void testListReports() {
        ReportQueryDTO query = new ReportQueryDTO();
        query.setCurrent(1L);
        query.setSize(10L);
        query.setReportType("TASK_SUMMARY");
        query.setStatus("COMPLETED");
        query.setKeyword("测试");

        ReportEntity e1 = buildEntity("rpt-1", "PDF");
        ReportEntity e2 = buildEntity("rpt-2", "HTML");
        Page<ReportEntity> page = new Page<>(1L, 10L, 2L);
        page.setRecords(Arrays.asList(e1, e2));

        when(reportMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<ReportVO> result = reportService.listReports(query);

        assertNotNull(result);
        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getRecords().size());
        assertEquals("rpt-1", result.getRecords().get(0).getReportId());
    }

    /**
     * 测试分页查询 - 空结果
     */
    @Test
    @DisplayName("分页查询报告 - 空结果应返回空分页")
    @SuppressWarnings("unchecked")
    void testListReportsEmpty() {
        ReportQueryDTO query = new ReportQueryDTO();
        Page<ReportEntity> page = new Page<>(1L, 10L, 0L);
        page.setRecords(Collections.emptyList());

        when(reportMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        PageResult<ReportVO> result = reportService.listReports(query);

        assertNotNull(result);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    // ===================== deleteReport =====================

    /**
     * 测试删除报告 - 含物理文件删除
     */
    @Test
    @DisplayName("删除报告 - 应删除文件并删除记录")
    void testDeleteReportWithFile() {
        File tempFile = tempDir.resolve("to-delete.pdf").toFile();
        assertDoesNotThrow(() -> tempFile.createNewFile());
        assertTrue(tempFile.exists());

        ReportEntity entity = buildEntity("rpt-del-001", "PDF");
        entity.setFilePath(tempFile.getAbsolutePath());

        when(reportMapper.selectById("rpt-del-001")).thenReturn(entity);
        when(reportMapper.deleteById("rpt-del-001")).thenReturn(1);

        reportService.deleteReport("rpt-del-001");

        verify(reportMapper).deleteById("rpt-del-001");
        assertTrue(!tempFile.exists());
    }

    /**
     * 测试删除报告 - 不存在时应抛出异常
     */
    @Test
    @DisplayName("删除报告 - 不存在时应抛出 BusinessException")
    void testDeleteReportNotFound() {
        when(reportMapper.selectById("rpt-missing")).thenReturn(null);

        assertThrows(BusinessException.class, () -> reportService.deleteReport("rpt-missing"));
        verify(reportMapper, never()).deleteById(anyString());
    }

    /**
     * 测试删除报告 - 文件路径为空时不应尝试删除文件
     */
    @Test
    @DisplayName("删除报告 - 文件路径为空时安全处理")
    void testDeleteReportNoFilePath() {
        ReportEntity entity = buildEntity("rpt-no-file", "PDF");
        entity.setFilePath(null);

        when(reportMapper.selectById("rpt-no-file")).thenReturn(entity);
        when(reportMapper.deleteById("rpt-no-file")).thenReturn(1);

        assertDoesNotThrow(() -> reportService.deleteReport("rpt-no-file"));
        verify(reportMapper).deleteById("rpt-no-file");
    }

    // ===================== downloadReport =====================

    /**
     * 测试下载报告 - 成功
     */
    @Test
    @DisplayName("下载报告 - 应返回文件资源")
    void testDownloadReport() {
        File tempFile = tempDir.resolve("download-test.html").toFile();
        assertDoesNotThrow(() -> tempFile.createNewFile());

        ReportEntity entity = buildEntity("rpt-dl-001", "HTML");
        entity.setStatus("COMPLETED");
        entity.setFilePath(tempFile.getAbsolutePath());

        when(reportMapper.selectById("rpt-dl-001")).thenReturn(entity);

        Resource resource = reportService.downloadReport("rpt-dl-001");

        assertNotNull(resource);
        assertTrue(resource.exists());
    }

    /**
     * 测试下载报告 - 不存在时抛出异常
     */
    @Test
    @DisplayName("下载报告 - 不存在时应抛出异常")
    void testDownloadReportNotFound() {
        when(reportMapper.selectById("rpt-missing")).thenReturn(null);
        assertThrows(BusinessException.class, () -> reportService.downloadReport("rpt-missing"));
    }

    /**
     * 测试下载报告 - 未完成时抛出异常
     */
    @Test
    @DisplayName("下载报告 - 未完成状态时应抛出异常")
    void testDownloadReportNotCompleted() {
        ReportEntity entity = buildEntity("rpt-pending", "PDF");
        entity.setStatus("PENDING");
        when(reportMapper.selectById("rpt-pending")).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reportService.downloadReport("rpt-pending"));
        assertTrue(ex.getMessage().contains("报告尚未生成完成"));
    }

    /**
     * 测试下载报告 - 文件不存在时抛出异常
     */
    @Test
    @DisplayName("下载报告 - 文件不存在时应抛出异常")
    void testDownloadReportFileMissing() {
        ReportEntity entity = buildEntity("rpt-nofile", "PDF");
        entity.setStatus("COMPLETED");
        entity.setFilePath(tempDir.resolve("non-exist.pdf").toString());

        when(reportMapper.selectById("rpt-nofile")).thenReturn(entity);

        assertThrows(BusinessException.class, () -> reportService.downloadReport("rpt-nofile"));
    }

    // ===================== listTemplates =====================

    /**
     * 测试获取模板列表
     */
    @Test
    @DisplayName("获取模板列表 - 应返回模板 VO 列表")
    void testListTemplates() {
        ReportTemplateEntity t1 = new ReportTemplateEntity();
        t1.setTemplateId("tpl-1");
        t1.setTemplateName("任务总结模板");
        t1.setTemplateType("TASK_SUMMARY");
        t1.setTemplatePath("task-summary");
        t1.setDescription("任务完成自动生成");

        ReportTemplateEntity t2 = new ReportTemplateEntity();
        t2.setTemplateId("tpl-2");
        t2.setTemplateName("渗透测试模板");
        t2.setTemplateType("PENETRATION_TEST");
        t2.setTemplatePath("penetration-test");
        t2.setDescription("渗透测试专用");

        when(reportTemplateMapper.selectList(any())).thenReturn(Arrays.asList(t1, t2));

        List<ReportTemplateVO> result = reportService.listTemplates();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("tpl-1", result.get(0).getTemplateId());
        assertEquals("任务总结模板", result.get(0).getTemplateName());
        assertEquals("task-summary", result.get(0).getTemplatePath());
    }

    /**
     * 测试获取模板列表 - 空列表
     */
    @Test
    @DisplayName("获取模板列表 - 空列表应返回空集合")
    void testListTemplatesEmpty() {
        when(reportTemplateMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<ReportTemplateVO> result = reportService.listTemplates();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ===================== shareReport / unshareReport =====================

    /**
     * 测试共享报告 - 成功
     */
    @Test
    @DisplayName("共享报告 - 应更新 sharedWith 与 isShared 字段")
    void testShareReport() {
        ReportEntity entity = buildEntity("rpt-share-001", "PDF");
        when(reportMapper.selectById("rpt-share-001")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        ReportShareDTO dto = new ReportShareDTO();
        dto.setUserIds(Arrays.asList(1001L, 1002L, 1003L));

        ReportVO vo = reportService.shareReport("rpt-share-001", dto);

        assertNotNull(vo);
        assertEquals(1, vo.getIsShared());
        assertEquals("1001,1002,1003", vo.getSharedWith());
        verify(reportMapper).updateById(any());
    }

    /**
     * 测试共享报告 - 报告不存在时应抛出异常
     */
    @Test
    @DisplayName("共享报告 - 报告不存在时应抛出 BusinessException")
    void testShareReportNotFound() {
        when(reportMapper.selectById("rpt-missing")).thenReturn(null);

        ReportShareDTO dto = new ReportShareDTO();
        dto.setUserIds(Collections.singletonList(1001L));

        assertThrows(BusinessException.class,
                () -> reportService.shareReport("rpt-missing", dto));
        verify(reportMapper, never()).updateById(any());
    }

    /**
     * 测试取消共享 - 成功
     */
    @Test
    @DisplayName("取消共享 - 应清空 sharedWith 并将 isShared 置为 0")
    void testUnshareReport() {
        ReportEntity entity = buildEntity("rpt-unshare-001", "PDF");
        entity.setIsShared(1);
        entity.setSharedWith("1001,1002");
        when(reportMapper.selectById("rpt-unshare-001")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        ReportVO vo = reportService.unshareReport("rpt-unshare-001");

        assertNotNull(vo);
        assertEquals(0, vo.getIsShared());
        assertNull(vo.getSharedWith());
        verify(reportMapper).updateById(any());
    }

    /**
     * 测试取消共享 - 报告不存在时抛出异常
     */
    @Test
    @DisplayName("取消共享 - 报告不存在时应抛出异常")
    void testUnshareReportNotFound() {
        when(reportMapper.selectById("rpt-missing")).thenReturn(null);
        assertThrows(BusinessException.class, () -> reportService.unshareReport("rpt-missing"));
    }

    // ===================== regenerateReport =====================

    /**
     * 测试重新生成报告 - 版本号递增
     */
    @Test
    @DisplayName("重新生成报告 - 版本号应递增并触发异步生成")
    void testRegenerateReport() {
        ReportEntity entity = buildEntity("rpt-regen-001", "HTML");
        entity.setVersion(3);
        entity.setStatus("COMPLETED");
        entity.setFilePath(null);

        when(reportMapper.selectById("rpt-regen-001")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        ReportVO vo = reportService.regenerateReport("rpt-regen-001");

        assertNotNull(vo);
        assertEquals("PENDING", vo.getStatus());
        assertEquals(4, vo.getVersion());
        assertNull(vo.getFilePath());
        assertNull(vo.getSummary());
        assertNull(vo.getFailureReason());

        ReportServiceImpl self = (ReportServiceImpl) ReflectionTestUtils.getField(reportService, "self");
        assertNotNull(self);
        verify(self).generateReportAsync("rpt-regen-001");
    }

    /**
     * 测试重新生成报告 - 版本号为 null 时应初始化为 2
     */
    @Test
    @DisplayName("重新生成报告 - 版本号为 null 时应初始化为 2")
    void testRegenerateReportNullVersion() {
        ReportEntity entity = buildEntity("rpt-regen-null", "HTML");
        entity.setVersion(null);
        entity.setStatus("COMPLETED");

        when(reportMapper.selectById("rpt-regen-null")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        ReportVO vo = reportService.regenerateReport("rpt-regen-null");

        assertEquals(2, vo.getVersion());
    }

    /**
     * 测试重新生成报告 - 报告不存在时抛出异常
     */
    @Test
    @DisplayName("重新生成报告 - 报告不存在时应抛出异常")
    void testRegenerateReportNotFound() {
        when(reportMapper.selectById("rpt-missing")).thenReturn(null);
        assertThrows(BusinessException.class, () -> reportService.regenerateReport("rpt-missing"));
    }

    /**
     * 测试重新生成报告 - 应删除旧文件
     */
    @Test
    @DisplayName("重新生成报告 - 应删除旧文件")
    void testRegenerateReportDeletesOldFile() {
        File oldFile = tempDir.resolve("old.pdf").toFile();
        assertDoesNotThrow(() -> oldFile.createNewFile());
        assertTrue(oldFile.exists());

        ReportEntity entity = buildEntity("rpt-regen-del", "PDF");
        entity.setStatus("COMPLETED");
        entity.setFilePath(oldFile.getAbsolutePath());
        entity.setVersion(1);

        when(reportMapper.selectById("rpt-regen-del")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        reportService.regenerateReport("rpt-regen-del");

        assertTrue(!oldFile.exists());
    }

    // ===================== retryFailed =====================

    /**
     * 测试重试失败报告 - 成功
     */
    @Test
    @DisplayName("重试失败报告 - 应重置状态并触发异步生成")
    void testRetryFailedSuccess() {
        ReportEntity entity = buildEntity("rpt-retry-001", "HTML");
        entity.setStatus("FAILED");
        entity.setFailureReason("模板渲染失败");

        when(reportMapper.selectById("rpt-retry-001")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        ReportVO vo = reportService.retryFailed("rpt-retry-001");

        assertNotNull(vo);
        assertEquals("PENDING", vo.getStatus());
        assertNull(vo.getFailureReason());
        assertNull(vo.getFilePath());
        ReportServiceImpl self = (ReportServiceImpl) ReflectionTestUtils.getField(reportService, "self");
        assertNotNull(self);
        verify(self).generateReportAsync("rpt-retry-001");
    }

    /**
     * 测试重试失败报告 - 非 FAILED 状态时应抛出异常
     */
    @Test
    @DisplayName("重试失败报告 - 非 FAILED 状态应抛出异常")
    void testRetryFailedNotFailedStatus() {
        ReportEntity entity = buildEntity("rpt-retry-ok", "PDF");
        entity.setStatus("COMPLETED");

        when(reportMapper.selectById("rpt-retry-ok")).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reportService.retryFailed("rpt-retry-ok"));
        assertTrue(ex.getMessage().contains("仅失败状态的报告可重试"));
    }

    /**
     * 测试重试失败报告 - 报告不存在时抛出异常
     */
    @Test
    @DisplayName("重试失败报告 - 报告不存在时应抛出异常")
    void testRetryFailedNotFound() {
        when(reportMapper.selectById("rpt-missing")).thenReturn(null);
        assertThrows(BusinessException.class, () -> reportService.retryFailed("rpt-missing"));
    }

    /**
     * 测试重试失败报告 - 应清理旧的失败文件
     */
    @Test
    @DisplayName("重试失败报告 - 应清理旧的失败文件")
    void testRetryFailedCleansOldFile() {
        File oldFile = tempDir.resolve("old-failed.html").toFile();
        assertDoesNotThrow(() -> oldFile.createNewFile());

        ReportEntity entity = buildEntity("rpt-retry-clean", "HTML");
        entity.setStatus("FAILED");
        entity.setFilePath(oldFile.getAbsolutePath());

        when(reportMapper.selectById("rpt-retry-clean")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        reportService.retryFailed("rpt-retry-clean");

        assertTrue(!oldFile.exists());
    }

    // ===================== getReportStats =====================

    /**
     * 测试报告统计 - 应正确聚合各维度数据
     */
    @Test
    @DisplayName("报告统计 - 应返回完整统计结果")
    void testGetReportStats() {
        // 统一 stub：selectCount 调用序列（total + 4状态 + 5类型 + 3格式 + shared = 14 次）
        when(reportMapper.selectCount(any()))
                .thenReturn(10L)   // total
                .thenReturn(2L)    // PENDING
                .thenReturn(1L)    // GENERATING
                .thenReturn(6L)    // COMPLETED
                .thenReturn(1L)    // FAILED
                // byType (5 个值)
                .thenReturn(3L).thenReturn(2L).thenReturn(1L).thenReturn(2L).thenReturn(2L)
                // byFormat (3 个值)
                .thenReturn(4L).thenReturn(3L).thenReturn(3L)
                // shared
                .thenReturn(2L);

        ReportStatsDTO stats = reportService.getReportStats();

        assertNotNull(stats);
        assertEquals(10L, stats.getTotal());
        assertEquals(6L, stats.getCompletedCount());
        assertEquals(2L, stats.getPendingCount());
        assertEquals(1L, stats.getGeneratingCount());
        assertEquals(1L, stats.getFailedCount());
        assertEquals(2L, stats.getSharedCount());
        // 完成率 = 6 / 10 * 100 = 60.0
        assertEquals(60.0, stats.getCompletionRate(), 0.001);
        assertNotNull(stats.getByStatus());
        assertEquals(4, stats.getByStatus().size());
        assertNotNull(stats.getByType());
        assertEquals(5, stats.getByType().size());
        assertNotNull(stats.getByFormat());
        assertEquals(3, stats.getByFormat().size());
    }

    /**
     * 测试报告统计 - 总数为 0 时完成率应为 0
     */
    @Test
    @DisplayName("报告统计 - 总数为 0 时完成率应为 0")
    void testGetReportStatsEmpty() {
        when(reportMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L)   // total
                .thenReturn(0L).thenReturn(0L).thenReturn(0L).thenReturn(0L)  // 4 状态
                .thenReturn(0L).thenReturn(0L).thenReturn(0L).thenReturn(0L).thenReturn(0L)  // 5 type
                .thenReturn(0L).thenReturn(0L).thenReturn(0L)  // 3 format
                .thenReturn(0L);  // shared

        ReportStatsDTO stats = reportService.getReportStats();

        assertNotNull(stats);
        assertEquals(0L, stats.getTotal());
        assertEquals(0.0, stats.getCompletionRate(), 0.001);
    }

    /**
     * 测试报告统计 - selectCount 返回 null 时应兜底为 0
     */
    @Test
    @DisplayName("报告统计 - selectCount 返回 null 时应兜底为 0")
    void testGetReportStatsNullCount() {
        when(reportMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(null)
                .thenReturn(null).thenReturn(null).thenReturn(null).thenReturn(null)
                .thenReturn(null).thenReturn(null).thenReturn(null).thenReturn(null).thenReturn(null)
                .thenReturn(null).thenReturn(null).thenReturn(null)
                .thenReturn(null);

        ReportStatsDTO stats = reportService.getReportStats();

        assertNotNull(stats);
        assertEquals(0L, stats.getTotal());
        assertEquals(0L, stats.getCompletedCount());
        assertEquals(0.0, stats.getCompletionRate(), 0.001);
    }

    // ===================== toVO 字段映射 =====================

    /**
     * 测试 getReport 返回的 VO 应包含新增字段
     */
    @Test
    @DisplayName("getReport - VO 应包含 summary/version/isShared/failureReason 字段")
    void testGetReportIncludesNewFields() {
        ReportEntity entity = buildEntity("rpt-fields-001", "PDF");
        entity.setStatus("COMPLETED");
        entity.setSummary("测试摘要");
        entity.setVersion(2);
        entity.setIsShared(1);
        entity.setSharedWith("1001,1002");
        entity.setFailureReason(null);
        entity.setMetadata("{\"key\":\"value\"}");

        when(reportMapper.selectById("rpt-fields-001")).thenReturn(entity);

        ReportVO vo = reportService.getReport("rpt-fields-001");

        assertNotNull(vo);
        assertEquals("测试摘要", vo.getSummary());
        assertEquals(2, vo.getVersion());
        assertEquals(1, vo.getIsShared());
        assertEquals("1001,1002", vo.getSharedWith());
        assertEquals("{\"key\":\"value\"}", vo.getMetadata());
        assertNull(vo.getFailureReason());
    }

    // ===================== 辅助方法 =====================

    /**
     * 构造测试用报告实体
     *
     * @param reportId 报告ID
     * @param format   报告格式
     * @return 报告实体
     */
    private ReportEntity buildEntity(String reportId, String format) {
        ReportEntity entity = new ReportEntity();
        entity.setReportId(reportId);
        entity.setReportName("测试报告");
        entity.setReportType("TASK_SUMMARY");
        entity.setFormat(format);
        entity.setStatus("PENDING");
        return entity;
    }
}
