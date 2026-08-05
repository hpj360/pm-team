package com.redteam.report.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.exception.GlobalExceptionHandler;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.ResultCode;
import com.redteam.report.dto.ReportGenerateDTO;
import com.redteam.report.dto.ReportTemplateVO;
import com.redteam.report.dto.ReportVO;
import com.redteam.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ReportController} 接口测试
 *
 * <p>使用 {@link MockMvcBuilders#standaloneSetup} 隔离测试 Controller 层，
 * 验证请求路由、参数校验、响应序列化及异常传播。</p>
 *
 * <p>测试覆盖率目标 ≥ 80%。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    private static final String BASE_URL = "/api/v1/reports";

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController reportController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    /**
     * 测试前初始化 MockMvc，注册全局异常处理器以模拟生产环境行为
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper.findAndRegisterModules();
    }

    // ===================== POST /api/v1/reports =====================

    /**
     * 测试生成报告接口 - 成功
     */
    @Test
    @DisplayName("POST /reports - 生成报告成功")
    void testGenerateReportSuccess() throws Exception {
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportName("渗透测试报告");
        dto.setReportType("PENETRATION_TEST");
        dto.setFormat("PDF");
        dto.setTaskId("task-1001");

        ReportVO vo = buildVO("rpt-001", "渗透测试报告", "PENETRATION_TEST", "PDF", "PENDING");
        when(reportService.generateReport(any(ReportGenerateDTO.class))).thenReturn(vo);

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reportId").value("rpt-001"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.format").value("PDF"));
    }

    /**
     * 测试生成报告接口 - 参数校验失败（缺少 reportName）
     */
    @Test
    @DisplayName("POST /reports - 缺少必填字段应返回参数错误")
    void testGenerateReportValidationFail() throws Exception {
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportType("TASK_SUMMARY");
        dto.setFormat("PDF");
        // 缺少 reportName

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    /**
     * 测试生成报告接口 - 格式不合法
     */
    @Test
    @DisplayName("POST /reports - 格式不合法应返回参数错误")
    void testGenerateReportInvalidFormat() throws Exception {
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportName("测试报告");
        dto.setReportType("TASK_SUMMARY");
        dto.setFormat("EXCEL");

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    // ===================== GET /api/v1/reports/{reportId} =====================

    /**
     * 测试获取报告详情 - 成功
     */
    @Test
    @DisplayName("GET /reports/{id} - 获取报告详情成功")
    void testGetReportSuccess() throws Exception {
        ReportVO vo = buildVO("rpt-002", "漏洞扫描报告", "VULNERABILITY_SCAN", "WORD", "COMPLETED");
        when(reportService.getReport("rpt-002")).thenReturn(vo);

        mockMvc.perform(get(BASE_URL + "/rpt-002"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reportId").value("rpt-002"))
                .andExpect(jsonPath("$.data.reportName").value("漏洞扫描报告"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    /**
     * 测试获取报告详情 - 报告不存在（BusinessException 经 GlobalExceptionHandler 处理，HTTP 200 + 错误码）
     */
    @Test
    @DisplayName("GET /reports/{id} - 报告不存在应返回 200 + 错误码")
    void testGetReportNotFound() throws Exception {
        when(reportService.getReport("rpt-missing"))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "报告不存在: rpt-missing"));

        mockMvc.perform(get(BASE_URL + "/rpt-missing"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("报告不存在: rpt-missing"));
    }

    // ===================== GET /api/v1/reports =====================

    /**
     * 测试分页查询报告
     */
    @Test
    @DisplayName("GET /reports - 分页查询报告成功")
    void testListReports() throws Exception {
        ReportVO v1 = buildVO("rpt-1", "报告A", "TASK_SUMMARY", "PDF", "COMPLETED");
        ReportVO v2 = buildVO("rpt-2", "报告B", "PENETRATION_TEST", "HTML", "COMPLETED");
        PageResult<ReportVO> page = PageResult.of(1L, 10L, 2L, Arrays.asList(v1, v2));

        when(reportService.listReports(any())).thenReturn(page);

        mockMvc.perform(get(BASE_URL)
                        .param("current", "1")
                        .param("size", "10")
                        .param("reportType", "TASK_SUMMARY")
                        .param("status", "COMPLETED")
                        .param("keyword", "报告"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].reportId").value("rpt-1"))
                .andExpect(jsonPath("$.data.records[1].reportId").value("rpt-2"));
    }

    /**
     * 测试分页查询报告 - 使用默认分页参数
     */
    @Test
    @DisplayName("GET /reports - 使用默认分页参数")
    void testListReportsDefaultPaging() throws Exception {
        PageResult<ReportVO> empty = PageResult.of(1L, 10L, 0L, Collections.emptyList());
        when(reportService.listReports(any())).thenReturn(empty);

        mockMvc.perform(get(BASE_URL))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    // ===================== DELETE /api/v1/reports/{reportId} =====================

    /**
     * 测试删除报告 - 成功
     */
    @Test
    @DisplayName("DELETE /reports/{id} - 删除报告成功")
    void testDeleteReportSuccess() throws Exception {
        doNothing().when(reportService).deleteReport("rpt-del");

        mockMvc.perform(delete(BASE_URL + "/rpt-del"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /**
     * 测试删除报告 - 不存在（BusinessException 经 GlobalExceptionHandler 处理）
     */
    @Test
    @DisplayName("DELETE /reports/{id} - 报告不存在应返回 200 + 错误码")
    void testDeleteReportNotFound() throws Exception {
        doThrow(new BusinessException(ResultCode.NOT_FOUND, "报告不存在"))
                .when(reportService).deleteReport("rpt-missing");

        mockMvc.perform(delete(BASE_URL + "/rpt-missing"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()));
    }

    // ===================== GET /api/v1/reports/{reportId}/download =====================

    /**
     * 测试下载报告 - 成功
     */
    @Test
    @DisplayName("GET /reports/{id}/download - 下载报告成功")
    void testDownloadReportSuccess() throws Exception {
        File tempFile = tempDir.resolve("download-test.html").toFile();
        Files.writeString(tempFile.toPath(), "<html>test</html>");

        when(reportService.downloadReport("rpt-dl"))
                .thenReturn(new FileSystemResource(tempFile));

        mockMvc.perform(get(BASE_URL + "/rpt-dl/download"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"rpt-dl\""));
    }

    /**
     * 测试下载报告 - 报告未完成（BusinessException 经 GlobalExceptionHandler 处理）
     */
    @Test
    @DisplayName("GET /reports/{id}/download - 报告未完成应返回 200 + 错误码")
    void testDownloadReportNotCompleted() throws Exception {
        when(reportService.downloadReport("rpt-pending"))
                .thenThrow(new BusinessException(ResultCode.FAIL, "报告尚未生成完成"));

        mockMvc.perform(get(BASE_URL + "/rpt-pending/download"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FAIL.getCode()));
    }

    // ===================== GET /api/v1/reports/templates =====================

    /**
     * 测试获取模板列表
     */
    @Test
    @DisplayName("GET /reports/templates - 获取模板列表成功")
    void testListTemplates() throws Exception {
        ReportTemplateVO t1 = new ReportTemplateVO();
        t1.setTemplateId("tpl-1");
        t1.setTemplateName("任务总结模板");
        t1.setTemplateType("TASK_SUMMARY");
        t1.setTemplatePath("task-summary");
        t1.setDescription("任务完成自动生成");

        ReportTemplateVO t2 = new ReportTemplateVO();
        t2.setTemplateId("tpl-2");
        t2.setTemplateName("渗透测试模板");
        t2.setTemplateType("PENETRATION_TEST");
        t2.setTemplatePath("penetration-test");
        t2.setDescription("渗透测试专用");

        List<ReportTemplateVO> templates = Arrays.asList(t1, t2);
        when(reportService.listTemplates()).thenReturn(templates);

        mockMvc.perform(get(BASE_URL + "/templates"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].templateId").value("tpl-1"))
                .andExpect(jsonPath("$.data[0].templateName").value("任务总结模板"))
                .andExpect(jsonPath("$.data[1].templateId").value("tpl-2"));
    }

    /**
     * 测试获取模板列表 - 空列表
     */
    @Test
    @DisplayName("GET /reports/templates - 空模板列表")
    void testListTemplatesEmpty() throws Exception {
        when(reportService.listTemplates()).thenReturn(Collections.emptyList());

        mockMvc.perform(get(BASE_URL + "/templates"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    // ===================== 辅助方法 =====================

    /**
     * 构造测试用 VO
     *
     * @param reportId   报告ID
     * @param reportName 报告名称
     * @param type       报告类型
     * @param format     报告格式
     * @param status     报告状态
     * @return VO 实例
     */
    private ReportVO buildVO(String reportId, String reportName, String type, String format, String status) {
        ReportVO vo = new ReportVO();
        vo.setReportId(reportId);
        vo.setReportName(reportName);
        vo.setReportType(type);
        vo.setFormat(format);
        vo.setStatus(status);
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setCreateTime(LocalDateTime.now());
        vo.setUpdateTime(LocalDateTime.now());
        return vo;
    }
}
