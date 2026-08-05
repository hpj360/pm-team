package com.redteam.report.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.report.dto.ReportGenerateDTO;
import com.redteam.report.dto.ReportShareDTO;
import com.redteam.report.dto.ReportStatsDTO;
import com.redteam.report.dto.ReportTemplateVO;
import com.redteam.report.dto.ReportVO;
import com.redteam.report.entity.ReportEntity;
import com.redteam.report.entity.ReportTemplateEntity;
import com.redteam.report.mapper.ReportMapper;
import com.redteam.report.mapper.ReportTemplateMapper;
import com.redteam.report.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
 * 报告服务集成测试
 *
 * <p>验证 ReportController → ReportService → ReportMapper 端到端请求链路，
 * 使用 @MockBean 隔离 Mapper 层（避免依赖真实 PostgreSQL），保留 Spring 容器装配、
 * 参数校验、JSON 序列化、异常处理等真实行为。</p>
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>报告生成 / 详情 / 分页查询 / 删除 / 下载接口</li>
 *   <li>报告共享 / 取消共享 / 重新生成 / 失败重试接口</li>
 *   <li>报告统计 / 模板列表接口</li>
 *   <li>异常路径：参数校验失败、BusinessException 传播</li>
 * </ul>
 *
 * @author 红方团队
 */
@SpringJUnitConfig
@WebAppConfiguration
@Import(ReportIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "report.output-path=./target/test-reports/",
        "report.pdf-font=STSong-Light",
        "report.default-template=task-summary"
})
@DisplayName("报告服务集成测试")
class ReportIntegrationTest {

    @TestConfiguration
    @EnableWebMvc
    static class TestConfig {
        @Bean
        public ReportService reportService(ReportMapper reportMapper, ReportTemplateMapper reportTemplateMapper) {
            return new com.redteam.report.service.impl.ReportServiceImpl(reportMapper, reportTemplateMapper);
        }

        @Bean
        public com.redteam.report.controller.ReportController reportController(ReportService reportService) {
            return new com.redteam.report.controller.ReportController(reportService);
        }

        @Bean
        public com.redteam.common.exception.GlobalExceptionHandler globalExceptionHandler() {
            return new com.redteam.common.exception.GlobalExceptionHandler();
        }

        @Bean
        public org.thymeleaf.TemplateEngine templateEngine() {
            return new org.thymeleaf.TemplateEngine();
        }
    }

    @MockBean
    private ReportMapper reportMapper;

    @MockBean
    private ReportTemplateMapper reportTemplateMapper;

    @Autowired
    private ReportService reportService;

    @Autowired
    private com.redteam.report.controller.ReportController reportController;

    @Autowired
    private com.redteam.common.exception.GlobalExceptionHandler exceptionHandler;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp(WebApplicationContext wac) {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // 通过反射注入 mock self 代理：generateReportAsync 默认 do-nothing，
        // 避免 @Async 触发真实线程池和 Thymeleaf 模板渲染（OGNL 依赖缺失）
        try {
            java.lang.reflect.Field selfField =
                    com.redteam.report.service.impl.ReportServiceImpl.class.getDeclaredField("self");
            selfField.setAccessible(true);
            selfField.set(reportService, mock(com.redteam.report.service.impl.ReportServiceImpl.class));
        } catch (Exception ignored) {
            // 忽略
        }
    }

    // ===================== POST /api/v1/reports =====================

    @Test
    @DisplayName("集成 - 生成报告应返回 PENDING 状态")
    void testGenerateReportFlow() throws Exception {
        when(reportMapper.insert(any(ReportEntity.class))).thenReturn(1);

        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportName("渗透测试报告");
        dto.setReportType("PENETRATION_TEST");
        dto.setFormat("PDF");
        dto.setTaskId("task-1001");

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.reportName").value("渗透测试报告"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.isShared").value(0));
    }

    @Test
    @DisplayName("集成 - 缺少必填字段应返回 400")
    void testGenerateReportValidation() throws Exception {
        ReportGenerateDTO dto = new ReportGenerateDTO();
        dto.setReportType("TASK_SUMMARY");
        dto.setFormat("PDF");

        mockMvc.perform(post("/api/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ===================== GET /api/v1/reports/{reportId} =====================

    @Test
    @DisplayName("集成 - 获取报告详情应返回完整 VO")
    void testGetReportFlow() throws Exception {
        ReportEntity entity = buildEntity("rpt-get-001", "PDF", "COMPLETED");
        entity.setSummary("任务摘要");
        entity.setVersion(2);
        when(reportMapper.selectById("rpt-get-001")).thenReturn(entity);

        mockMvc.perform(get("/api/v1/reports/rpt-get-001"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.reportId").value("rpt-get-001"))
                .andExpect(jsonPath("$.data.summary").value("任务摘要"))
                .andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    @DisplayName("集成 - 报告不存在应返回业务错误码")
    void testGetReportNotFoundFlow() throws Exception {
        when(reportMapper.selectById("rpt-missing")).thenReturn(null);

        mockMvc.perform(get("/api/v1/reports/rpt-missing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.getCode()));
    }

    // ===================== GET /api/v1/reports =====================

    @Test
    @DisplayName("集成 - 分页查询报告应返回分页结构")
    void testListReportsFlow() throws Exception {
        ReportEntity e1 = buildEntity("rpt-1", "PDF", "COMPLETED");
        ReportEntity e2 = buildEntity("rpt-2", "HTML", "COMPLETED");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ReportEntity> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1L, 10L, 2L);
        page.setRecords(Arrays.asList(e1, e2));

        when(reportMapper.selectPage(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/reports")
                        .param("current", "1")
                        .param("size", "10")
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].reportId").value("rpt-1"));
    }

    // ===================== DELETE /api/v1/reports/{reportId} =====================

    @Test
    @DisplayName("集成 - 删除报告应返回成功")
    void testDeleteReportFlow() throws Exception {
        ReportEntity entity = buildEntity("rpt-del", "PDF", "COMPLETED");
        entity.setFilePath(null);
        when(reportMapper.selectById("rpt-del")).thenReturn(entity);
        when(reportMapper.deleteById("rpt-del")).thenReturn(1);

        mockMvc.perform(delete("/api/v1/reports/rpt-del"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== GET /api/v1/reports/{reportId}/download =====================

    @Test
    @DisplayName("集成 - 下载报告应返回文件流")
    void testDownloadReportFlow() throws Exception {
        File tempFile = Files.createTempFile("download-test", ".html").toFile();
        Files.writeString(tempFile.toPath(), "<html>test</html>");
        tempFile.deleteOnExit();

        ReportEntity entity = buildEntity("rpt-dl", "HTML", "COMPLETED");
        entity.setFilePath(tempFile.getAbsolutePath());
        when(reportMapper.selectById("rpt-dl")).thenReturn(entity);

        mockMvc.perform(get("/api/v1/reports/rpt-dl/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().exists("Content-Disposition"));
    }

    // ===================== GET /api/v1/reports/templates =====================

    @Test
    @DisplayName("集成 - 获取模板列表应返回 VO 数组")
    void testListTemplatesFlow() throws Exception {
        ReportTemplateEntity t1 = new ReportTemplateEntity();
        t1.setTemplateId("tpl-1");
        t1.setTemplateName("任务总结模板");
        t1.setTemplateType("TASK_SUMMARY");
        t1.setTemplatePath("task-summary");

        when(reportTemplateMapper.selectList(any())).thenReturn(Collections.singletonList(t1));

        mockMvc.perform(get("/api/v1/reports/templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].templateId").value("tpl-1"))
                .andExpect(jsonPath("$.data[0].templateName").value("任务总结模板"));
    }

    // ===================== GET /api/v1/reports/stats =====================

    @Test
    @DisplayName("集成 - 获取报告统计应返回统计结构")
    void testGetReportStatsFlow() throws Exception {
        when(reportMapper.selectCount(any()))
                .thenReturn(10L)   // total
                .thenReturn(2L).thenReturn(1L).thenReturn(6L).thenReturn(1L)  // 4 状态
                .thenReturn(3L).thenReturn(2L).thenReturn(1L).thenReturn(2L).thenReturn(2L)  // 5 type
                .thenReturn(4L).thenReturn(3L).thenReturn(3L)  // 3 format
                .thenReturn(2L);  // shared

        mockMvc.perform(get("/api/v1/reports/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.completedCount").value(6))
                .andExpect(jsonPath("$.data.sharedCount").value(2));
    }

    // ===================== POST /api/v1/reports/{reportId}/share =====================

    @Test
    @DisplayName("集成 - 共享报告应返回更新后的 VO")
    void testShareReportFlow() throws Exception {
        ReportEntity entity = buildEntity("rpt-share", "PDF", "COMPLETED");
        when(reportMapper.selectById("rpt-share")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        ReportShareDTO dto = new ReportShareDTO();
        dto.setUserIds(Arrays.asList(1001L, 1002L));

        mockMvc.perform(post("/api/v1/reports/rpt-share/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.isShared").value(1))
                .andExpect(jsonPath("$.data.sharedWith").value("1001,1002"));
    }

    @Test
    @DisplayName("集成 - 共享报告参数校验：用户列表为空应返回 400")
    void testShareReportValidation() throws Exception {
        ReportShareDTO dto = new ReportShareDTO();
        dto.setUserIds(Collections.emptyList());

        mockMvc.perform(post("/api/v1/reports/rpt-share/share")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ===================== DELETE /api/v1/reports/{reportId}/share =====================

    @Test
    @DisplayName("集成 - 取消共享应将 isShared 置为 0")
    void testUnshareReportFlow() throws Exception {
        ReportEntity entity = buildEntity("rpt-unshare", "PDF", "COMPLETED");
        entity.setIsShared(1);
        entity.setSharedWith("1001");
        when(reportMapper.selectById("rpt-unshare")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        mockMvc.perform(delete("/api/v1/reports/rpt-unshare/share"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isShared").value(0));
    }

    // ===================== POST /api/v1/reports/{reportId}/regenerate =====================

    @Test
    @DisplayName("集成 - 重新生成报告应递增版本号并返回 PENDING")
    void testRegenerateReportFlow() throws Exception {
        ReportEntity entity = buildEntity("rpt-regen", "HTML", "COMPLETED");
        entity.setVersion(3);
        entity.setFilePath(null);
        when(reportMapper.selectById("rpt-regen")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        mockMvc.perform(post("/api/v1/reports/rpt-regen/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.version").value(4));
    }

    // ===================== POST /api/v1/reports/{reportId}/retry =====================

    @Test
    @DisplayName("集成 - 重试失败报告应返回 PENDING")
    void testRetryFailedReportFlow() throws Exception {
        ReportEntity entity = buildEntity("rpt-retry", "HTML", "FAILED");
        entity.setFailureReason("渲染失败");
        when(reportMapper.selectById("rpt-retry")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        mockMvc.perform(post("/api/v1/reports/rpt-retry/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("集成 - 重试非 FAILED 状态报告应返回业务错误")
    void testRetryFailedReportNotFailed() throws Exception {
        ReportEntity entity = buildEntity("rpt-ok", "PDF", "COMPLETED");
        when(reportMapper.selectById("rpt-ok")).thenReturn(entity);
        when(reportMapper.updateById(any())).thenReturn(1);

        mockMvc.perform(post("/api/v1/reports/rpt-ok/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FAIL.getCode()));
    }

    // ===================== 辅助方法 =====================

    private ReportEntity buildEntity(String reportId, String format, String status) {
        ReportEntity entity = new ReportEntity();
        entity.setReportId(reportId);
        entity.setReportName("测试报告");
        entity.setReportType("TASK_SUMMARY");
        entity.setFormat(format);
        entity.setStatus(status);
        entity.setVersion(1);
        entity.setIsShared(0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }
}
