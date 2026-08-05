package com.redteam.report.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.common.result.ResultCode;
import com.redteam.common.util.UserContext;
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
import com.redteam.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 报告生成服务实现
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>{@link #generateReport(ReportGenerateDTO)} 同步入库为 PENDING，立即返回 reportId</li>
 *   <li>{@link #generateReportAsync(String)} 通过 {@code reportTaskExecutor} 异步执行</li>
 *   <li>根据 {@code format} 分发到 {@link #generatePdf} / {@link #generateWord} / {@link #generateHtml}</li>
 *   <li>渲染完成后更新状态为 COMPLETED，失败则置为 FAILED</li>
 * </ol>
 *
 * <p>技术栈：Thymeleaf 渲染 HTML → iText html2pdf 转 PDF / POI 生成 Word。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 报告状态枚举常量
     */
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_GENERATING = "GENERATING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final ReportMapper reportMapper;
    private final ReportTemplateMapper reportTemplateMapper;

    /**
     * 自身代理引用，用于在同类内部调用 {@link #generateReportAsync(String)} 时仍然走 Spring AOP 代理，
     * 确保 {@code @Async} 注解生效。
     */
    @Lazy
    @Autowired
    private ReportServiceImpl self;

    @Value("${report.output-path:/data/reports/}")
    private String outputPath;

    @Value("${report.pdf-font:STSong-Light}")
    private String pdfFont;

    @Value("${report.default-template:task-summary}")
    private String defaultTemplate;

    /**
     * Thymeleaf 模板引擎
     */
    @Autowired
    private TemplateEngine templateEngine;

    /**
     * 异步生成报告入口
     *
     * <p>立即返回 PENDING 状态的报告信息，实际生成在异步线程池中执行。</p>
     *
     * @param dto 报告生成请求
     * @return 报告基础信息
     */
    @Override
    public ReportVO generateReport(ReportGenerateDTO dto) {
        log.info("生成报告请求: reportName={}, type={}, format={}", dto.getReportName(), dto.getReportType(), dto.getFormat());

        ReportEntity entity = new ReportEntity();
        entity.setReportName(dto.getReportName());
        entity.setReportType(dto.getReportType());
        entity.setTaskId(dto.getTaskId());
        entity.setTargetId(dto.getTargetId());
        entity.setTemplateId(StrUtil.blankToDefault(dto.getTemplateId(), resolveTemplateByType(dto.getReportType())));
        entity.setFormat(dto.getFormat().toUpperCase());
        entity.setStatus(STATUS_PENDING);
        entity.setGeneratedBy(UserContext.getUserId());
        entity.setVersion(1);
        entity.setIsShared(0);
        entity.setFailureReason(null);

        reportMapper.insert(entity);
        log.info("报告已入库（PENDING）: reportId={}", entity.getReportId());

        // 通过代理调用，确保 @Async 生效
        self.generateReportAsync(entity.getReportId());

        return toVO(entity);
    }

    /**
     * 异步生成报告
     *
     * <p>使用 {@code reportTaskExecutor} 线程池执行，方法内部捕获所有异常，
     * 失败时将状态更新为 FAILED 并保留错误信息。</p>
     *
     * @param reportId 报告ID
     */
    @Async("reportTaskExecutor")
    public void generateReportAsync(String reportId) {
        ReportEntity entity = reportMapper.selectById(reportId);
        if (entity == null) {
            log.warn("异步生成报告失败：报告不存在 reportId={}", reportId);
            return;
        }

        log.info("开始异步生成报告: reportId={}, format={}, version={}", reportId, entity.getFormat(), entity.getVersion());
        try {
            entity.setStatus(STATUS_GENERATING);
            entity.setFailureReason(null);
            reportMapper.updateById(entity);

            Map<String, Object> data = buildReportData(entity);

            String filePath;
            switch (entity.getFormat().toUpperCase()) {
                case "PDF":
                    filePath = generatePdf(entity, data);
                    break;
                case "WORD":
                    filePath = generateWord(entity, data);
                    break;
                case "HTML":
                    filePath = generateHtml(entity, data);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的报告格式: " + entity.getFormat());
            }

            File file = new File(filePath);
            entity.setFilePath(filePath);
            entity.setFileSize(file.length());
            entity.setStatus(STATUS_COMPLETED);
            entity.setGeneratedAt(LocalDateTime.now());
            entity.setSummary(buildReportSummary(entity, data));
            reportMapper.updateById(entity);
            log.info("报告生成成功: reportId={}, filePath={}, size={}B", reportId, filePath, file.length());
        } catch (Exception e) {
            log.error("报告生成失败: reportId={}", reportId, e);
            String reason = e.getMessage();
            if (reason != null && reason.length() > 500) {
                reason = reason.substring(0, 500);
            }
            entity.setStatus(STATUS_FAILED);
            entity.setFailureReason(reason);
            entity.setGeneratedAt(LocalDateTime.now());
            reportMapper.updateById(entity);
        }
    }

    /**
     * 获取报告详情
     *
     * @param reportId 报告ID
     * @return 报告信息
     */
    @Override
    public ReportVO getReport(String reportId) {
        ReportEntity entity = reportMapper.selectById(reportId);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在: " + reportId);
        }
        return toVO(entity);
    }

    /**
     * 分页查询报告列表
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<ReportVO> listReports(ReportQueryDTO query) {
        Page<ReportEntity> page = new Page<>(query.getCurrent(), query.getSize());
        LambdaQueryWrapper<ReportEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(query.getReportType()), ReportEntity::getReportType, query.getReportType());
        wrapper.eq(StrUtil.isNotBlank(query.getStatus()), ReportEntity::getStatus, query.getStatus());
        wrapper.eq(StrUtil.isNotBlank(query.getTaskId()), ReportEntity::getTaskId, query.getTaskId());
        wrapper.eq(StrUtil.isNotBlank(query.getTargetId()), ReportEntity::getTargetId, query.getTargetId());
        wrapper.like(StrUtil.isNotBlank(query.getKeyword()), ReportEntity::getReportName, query.getKeyword());
        wrapper.orderByDesc(ReportEntity::getCreateTime);

        Page<ReportEntity> result = reportMapper.selectPage(page, wrapper);
        List<ReportVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(query.getCurrent(), query.getSize(), result.getTotal(), records);
    }

    /**
     * 删除报告
     *
     * <p>先删除物理文件，再逻辑删除数据库记录。</p>
     *
     * @param reportId 报告ID
     */
    @Override
    public void deleteReport(String reportId) {
        ReportEntity entity = reportMapper.selectById(reportId);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在: " + reportId);
        }
        if (StrUtil.isNotBlank(entity.getFilePath())) {
            try {
                FileUtil.del(entity.getFilePath());
                log.info("已删除报告文件: {}", entity.getFilePath());
            } catch (Exception e) {
                log.warn("删除报告文件失败: {}", entity.getFilePath(), e);
            }
        }
        reportMapper.deleteById(reportId);
        log.info("报告已删除: reportId={}", reportId);
    }

    /**
     * 下载报告文件
     *
     * @param reportId 报告ID
     * @return 文件资源
     */
    @Override
    public org.springframework.core.io.Resource downloadReport(String reportId) {
        ReportEntity entity = reportMapper.selectById(reportId);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告不存在: " + reportId);
        }
        if (!STATUS_COMPLETED.equals(entity.getStatus())) {
            throw new BusinessException(ResultCode.FAIL, "报告尚未生成完成，当前状态: " + entity.getStatus());
        }
        File file = new File(entity.getFilePath());
        if (!file.exists()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "报告文件不存在: " + entity.getFilePath());
        }
        return new FileSystemResource(file);
    }

    /**
     * 获取所有可用报告模板列表
     *
     * @return 模板列表
     */
    @Override
    public List<ReportTemplateVO> listTemplates() {
        List<ReportTemplateEntity> entities = reportTemplateMapper.selectList(null);
        return entities.stream().map(this::toTemplateVO).collect(Collectors.toList());
    }

    /**
     * 共享报告给指定用户列表
     *
     * <p>将 {@code sharedWith} 字段更新为逗号分隔的用户ID字符串，并将 {@code isShared} 置为 1。</p>
     *
     * @param reportId 报告ID
     * @param dto      共享请求
     * @return 更新后的报告 VO
     */
    @Override
    public ReportVO shareReport(String reportId, ReportShareDTO dto) {
        log.info("共享报告: reportId={}, userCount={}", reportId, dto.getUserIds().size());
        ReportEntity entity = getReportEntityById(reportId);

        String sharedWith = dto.getUserIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        entity.setSharedWith(sharedWith);
        entity.setIsShared(1);
        reportMapper.updateById(entity);
        log.info("报告共享成功: reportId={}, sharedWith={}", reportId, sharedWith);
        return toVO(entity);
    }

    /**
     * 取消报告共享
     *
     * @param reportId 报告ID
     * @return 更新后的报告 VO
     */
    @Override
    public ReportVO unshareReport(String reportId) {
        log.info("取消报告共享: reportId={}", reportId);
        ReportEntity entity = getReportEntityById(reportId);
        entity.setSharedWith(null);
        entity.setIsShared(0);
        reportMapper.updateById(entity);
        log.info("报告共享已取消: reportId={}", reportId);
        return toVO(entity);
    }

    /**
     * 重新生成报告（版本号递增）
     *
     * <p>仅允许对已存在报告进行重新生成；版本号自增，状态重置为 PENDING。</p>
     *
     * @param reportId 报告ID
     * @return 报告 VO（状态为 PENDING）
     */
    @Override
    public ReportVO regenerateReport(String reportId) {
        log.info("重新生成报告: reportId={}", reportId);
        ReportEntity entity = getReportEntityById(reportId);

        // 旧文件清理
        if (StrUtil.isNotBlank(entity.getFilePath())) {
            try {
                FileUtil.del(entity.getFilePath());
            } catch (Exception e) {
                log.warn("重新生成时清理旧文件失败: {}", entity.getFilePath(), e);
            }
        }

        // 版本号自增，状态重置
        Integer currentVersion = entity.getVersion();
        entity.setVersion(currentVersion == null ? 2 : currentVersion + 1);
        entity.setStatus(STATUS_PENDING);
        entity.setFilePath(null);
        entity.setFileSize(null);
        entity.setGeneratedAt(null);
        entity.setSummary(null);
        entity.setFailureReason(null);
        reportMapper.updateById(entity);

        // 触发异步生成
        self.generateReportAsync(entity.getReportId());
        log.info("重新生成报告已触发: reportId={}, version={}", reportId, entity.getVersion());
        return toVO(entity);
    }

    /**
     * 重试生成失败的报告
     *
     * <p>仅当状态为 FAILED 时允许重试，重置状态为 PENDING 并重新触发异步生成。</p>
     *
     * @param reportId 报告ID
     * @return 报告 VO（状态为 PENDING）
     */
    @Override
    public ReportVO retryFailed(String reportId) {
        log.info("重试失败报告: reportId={}", reportId);
        ReportEntity entity = getReportEntityById(reportId);

        if (!STATUS_FAILED.equals(entity.getStatus())) {
            throw BusinessException.of(ResultCode.FAIL, "仅失败状态的报告可重试，当前状态: " + entity.getStatus());
        }

        // 清理旧文件
        if (StrUtil.isNotBlank(entity.getFilePath())) {
            try {
                FileUtil.del(entity.getFilePath());
            } catch (Exception e) {
                log.warn("重试时清理旧文件失败: {}", entity.getFilePath(), e);
            }
        }

        entity.setStatus(STATUS_PENDING);
        entity.setFilePath(null);
        entity.setFileSize(null);
        entity.setGeneratedAt(null);
        entity.setFailureReason(null);
        reportMapper.updateById(entity);

        self.generateReportAsync(entity.getReportId());
        log.info("失败报告重试已触发: reportId={}", reportId);
        return toVO(entity);
    }

    /**
     * 报告统计（按状态/类型/格式分组）
     *
     * @return 统计 DTO
     */
    @Override
    public ReportStatsDTO getReportStats() {
        log.info("获取报告统计");
        ReportStatsDTO stats = new ReportStatsDTO();

        // 总数
        Long total = reportMapper.selectCount(null);
        stats.setTotal(total == null ? 0L : total);

        // 按状态分组
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String status : new String[]{STATUS_PENDING, STATUS_GENERATING, STATUS_COMPLETED, STATUS_FAILED}) {
            LambdaQueryWrapper<ReportEntity> w = new LambdaQueryWrapper<>();
            w.eq(ReportEntity::getStatus, status);
            Long count = reportMapper.selectCount(w);
            byStatus.put(status, count == null ? 0L : count);
        }
        stats.setByStatus(byStatus);
        stats.setPendingCount(byStatus.getOrDefault(STATUS_PENDING, 0L));
        stats.setGeneratingCount(byStatus.getOrDefault(STATUS_GENERATING, 0L));
        stats.setCompletedCount(byStatus.getOrDefault(STATUS_COMPLETED, 0L));
        stats.setFailedCount(byStatus.getOrDefault(STATUS_FAILED, 0L));

        // 完成率
        if (stats.getTotal() != null && stats.getTotal() > 0) {
            stats.setCompletionRate(stats.getCompletedCount() * 100.0 / stats.getTotal());
        } else {
            stats.setCompletionRate(0.0);
        }

        // 按类型分组
        stats.setByType(buildReportGroupStatsByField("report_type"));

        // 按格式分组
        stats.setByFormat(buildReportGroupStatsByField("format"));

        // 共享报告数
        LambdaQueryWrapper<ReportEntity> sharedWrapper = new LambdaQueryWrapper<>();
        sharedWrapper.eq(ReportEntity::getIsShared, 1);
        Long shared = reportMapper.selectCount(sharedWrapper);
        stats.setSharedCount(shared == null ? 0L : shared);

        return stats;
    }

    // ===================== 私有方法 =====================

    /**
     * 使用 iText + Thymeleaf 生成 PDF 报告
     *
     * <p>渲染流程：Thymeleaf 渲染 HTML 字符串 → iText {@link HtmlConverter} 转换为 PDF。
     * 中文字体使用 {@code STSong-Light} + {@code UniGB-UCS2-H} 编码。</p>
     *
     * @param report 报告实体
     * @param data   渲染数据
     * @return 生成的 PDF 文件绝对路径
     * @throws IOException 文件写入异常时抛出
     */
    private String generatePdf(ReportEntity report, Map<String, Object> data) throws IOException {
        String html = renderTemplate(report, data);
        String fileName = buildFileName(report, "pdf");
        File outputFile = ensureOutputFile(fileName);

        byte[] htmlBytes = html.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = new FileOutputStream(outputFile)) {
            HtmlConverter.convertToPdf(new ByteArrayInputStream(htmlBytes), os);
        }

        // 兜底：若 HtmlConverter 未生成内容，则使用 iText 原生 API 写入最小 PDF
        if (outputFile.length() == 0) {
            try (PdfWriter writer = new PdfWriter(outputFile);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                PdfFont font = PdfFontFactory.createFont(pdfFont + ",UniGB-UCS2-H");
                document.setFont(font);
                document.add(new Paragraph(StrUtil.nullToEmpty(report.getReportName())));
            }
        }
        return outputFile.getAbsolutePath();
    }

    /**
     * 使用 Apache POI 生成 Word 报告
     *
     * <p>渲染流程：Thymeleaf 渲染 HTML → 解析为段落（简化版）→ POI 写入 .docx。
     * 完整 HTML→Word 转换可后续扩展为 docx4j 或 POI 的 XWPFDocument 段落映射。</p>
     *
     * @param report 报告实体
     * @param data   渲染数据
     * @return 生成的 Word 文件绝对路径
     * @throws IOException 文件写入异常时抛出
     */
    private String generateWord(ReportEntity report, Map<String, Object> data) throws IOException {
        String html = renderTemplate(report, data);
        String fileName = buildFileName(report, "docx");
        File outputFile = ensureOutputFile(fileName);

        try (XWPFDocument doc = new XWPFDocument();
             OutputStream os = new FileOutputStream(outputFile)) {

            // 标题
            XWPFParagraph title = doc.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setText(report.getReportName());
            titleRun.setBold(true);
            titleRun.setFontSize(18);

            // 元数据
            addParagraph(doc, "报告编号：" + report.getReportId());
            addParagraph(doc, "任务编号：" + StrUtil.nullToEmpty(report.getTaskId()));
            addParagraph(doc, "生成时间：" + LocalDateTime.now().format(FORMATTER));

            // 主体内容（按行写入 HTML 文本，去掉标签作为简化实现）
            String plainText = html.replaceAll("<[^>]+>", StrUtil.EMPTY).trim();
            for (String line : plainText.split("\\r?\\n")) {
                if (StrUtil.isNotBlank(line)) {
                    addParagraph(doc, line.trim());
                }
            }

            doc.write(os);
        }
        return outputFile.getAbsolutePath();
    }

    /**
     * 使用 Thymeleaf 生成 HTML 报告
     *
     * @param report 报告实体
     * @param data   渲染数据
     * @return 生成的 HTML 文件绝对路径
     * @throws IOException 文件写入异常时抛出
     */
    private String generateHtml(ReportEntity report, Map<String, Object> data) throws IOException {
        String html = renderTemplate(report, data);
        String fileName = buildFileName(report, "html");
        File outputFile = ensureOutputFile(fileName);
        FileUtil.writeUtf8String(html, outputFile);
        return outputFile.getAbsolutePath();
    }

    /**
     * 渲染 Thymeleaf 模板
     *
     * @param report 报告实体
     * @param data   渲染数据
     * @return HTML 字符串
     */
    private String renderTemplate(ReportEntity report, Map<String, Object> data) {
        Context context = new Context();
        context.setVariable("reportId", report.getReportId());
        context.setVariable("reportName", report.getReportName());
        context.setVariable("taskId", report.getTaskId());
        context.setVariable("generatedAt", LocalDateTime.now());
        data.forEach(context::setVariable);

        String templateName = resolveTemplatePath(report);
        return templateEngine.process(templateName, context);
    }

    /**
     * 构造报告渲染数据
     *
     * <p>骨架实现：注入基础元数据与示例发现项。生产环境应通过 RPC/查询补充真实业务数据。</p>
     *
     * @param report 报告实体
     * @return 渲染数据 Map
     */
    private Map<String, Object> buildReportData(ReportEntity report) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskSummary", "本次任务共完成 5 个目标的渗透测试，发现高危漏洞 3 处、中危漏洞 7 处。");
        data.put("findings", buildSampleFindings());
        data.put("conclusion", "建议立即修复高危漏洞，并制定中危漏洞的整改计划。");
        return data;
    }

    /**
     * 构造示例发现项数据
     *
     * @return 发现项列表
     */
    private List<Map<String, Object>> buildSampleFindings() {
        List<Map<String, Object>> findings = new ArrayList<>();
        findings.add(buildFinding("SQL注入", "高危", "登录接口存在基于时间的SQL注入"));
        findings.add(buildFinding("XSS漏洞", "中危", "评论模块未对输出做转义"));
        findings.add(buildFinding("弱口令", "高危", "管理员账户使用默认密码 admin/123456"));
        return findings;
    }

    /**
     * 构造单个发现项
     *
     * @param name        发现项名称
     * @param riskLevel   风险等级
     * @param description 描述
     * @return 发现项 Map
     */
    private Map<String, Object> buildFinding(String name, String riskLevel, String description) {
        Map<String, Object> finding = new HashMap<>();
        finding.put("name", name);
        finding.put("riskLevel", riskLevel);
        finding.put("description", description);
        return finding;
    }

    /**
     * 解析模板路径
     *
     * @param report 报告实体
     * @return Thymeleaf 模板名称
     */
    private String resolveTemplatePath(ReportEntity report) {
        if (StrUtil.isNotBlank(report.getTemplateId())) {
            ReportTemplateEntity template = reportTemplateMapper.selectById(report.getTemplateId());
            if (template != null && StrUtil.isNotBlank(template.getTemplatePath())) {
                return template.getTemplatePath();
            }
        }
        return StrUtil.blankToDefault(resolveTemplateByType(report.getReportType()), defaultTemplate);
    }

    /**
     * 根据报告类型解析默认模板名
     *
     * @param reportType 报告类型
     * @return 模板名（不含 .html 后缀）
     */
    private String resolveTemplateByType(String reportType) {
        if (StrUtil.isBlank(reportType)) {
            return defaultTemplate;
        }
        return switch (reportType) {
            case "PENETRATION_TEST" -> "penetration-test";
            case "VULNERABILITY_SCAN" -> "vulnerability-scan";
            case "ATTACK_CHAIN" -> "attack-chain";
            case "TARGET_PROFILE" -> "target-profile";
            case "TASK_SUMMARY" -> "task-summary";
            default -> defaultTemplate;
        };
    }

    /**
     * 构造输出文件名
     *
     * @param report 报告实体
     * @param ext    文件扩展名
     * @return 文件名
     */
    private String buildFileName(ReportEntity report, String ext) {
        String safeName = StrUtil.isBlank(report.getReportName()) ? IdUtil.fastSimpleUUID() : report.getReportName();
        return safeName + "_" + LocalDateTime.now().format(FORMATTER) + "_" + report.getReportId() + "." + ext;
    }

    /**
     * 确保输出目录存在并返回目标文件
     *
     * @param fileName 文件名
     * @return 输出文件
     */
    private File ensureOutputFile(String fileName) {
        File dir = new File(outputPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BusinessException("创建报告输出目录失败: " + outputPath);
        }
        return new File(dir, fileName);
    }

    /**
     * 向 Word 文档追加一段普通文本
     *
     * @param doc  Word 文档
     * @param text 文本内容
     */
    private void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontSize(12);
    }

    /**
     * 实体转 VO
     *
     * @param entity 报告实体
     * @return 报告 VO
     */
    private ReportVO toVO(ReportEntity entity) {
        ReportVO vo = new ReportVO();
        vo.setReportId(entity.getReportId());
        vo.setReportName(entity.getReportName());
        vo.setReportType(entity.getReportType());
        vo.setTaskId(entity.getTaskId());
        vo.setTargetId(entity.getTargetId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setFormat(entity.getFormat());
        vo.setFilePath(entity.getFilePath());
        vo.setFileSize(entity.getFileSize());
        vo.setStatus(entity.getStatus());
        vo.setGeneratedBy(entity.getGeneratedBy());
        vo.setGeneratedAt(entity.getGeneratedAt());
        vo.setSummary(entity.getSummary());
        vo.setMetadata(entity.getMetadata());
        vo.setVersion(entity.getVersion());
        vo.setIsShared(entity.getIsShared());
        vo.setSharedWith(entity.getSharedWith());
        vo.setFailureReason(entity.getFailureReason());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 模板实体转 VO
     *
     * @param entity 模板实体
     * @return 模板 VO
     */
    private ReportTemplateVO toTemplateVO(ReportTemplateEntity entity) {
        ReportTemplateVO vo = new ReportTemplateVO();
        vo.setTemplateId(entity.getTemplateId());
        vo.setTemplateName(entity.getTemplateName());
        vo.setTemplateType(entity.getTemplateType());
        vo.setTemplatePath(entity.getTemplatePath());
        vo.setDescription(entity.getDescription());
        return vo;
    }

    /**
     * 根据报告ID获取实体，不存在时抛出 BusinessException
     *
     * @param reportId 报告ID
     * @return 报告实体
     */
    private ReportEntity getReportEntityById(String reportId) {
        ReportEntity entity = reportMapper.selectById(reportId);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "报告不存在: " + reportId);
        }
        return entity;
    }

    /**
     * 构造报告摘要（生成完成后调用）
     *
     * @param report 报告实体
     * @param data   渲染数据
     * @return 摘要字符串（最多 200 字符）
     */
    private String buildReportSummary(ReportEntity report, Map<String, Object> data) {
        Object taskSummary = data.get("taskSummary");
        String summary = taskSummary == null ? report.getReportName() : String.valueOf(taskSummary);
        if (summary.length() > 200) {
            summary = summary.substring(0, 200);
        }
        return summary;
    }

    /**
     * 按指定字段构造分组统计
     *
     * <p>由于 MyBatis-Plus LambdaQueryWrapper 不便做 group by，这里采用枚举所有取值的方式实现。</p>
     *
     * @param fieldName 字段名（report_type / format）
     * @return 分组统计 Map
     */
    private Map<String, Long> buildReportGroupStatsByField(String fieldName) {
        Map<String, Long> result = new LinkedHashMap<>();
        String[] values;
        if ("report_type".equals(fieldName)) {
            values = new String[]{"PENETRATION_TEST", "VULNERABILITY_SCAN", "ATTACK_CHAIN", "TARGET_PROFILE", "TASK_SUMMARY"};
        } else if ("format".equals(fieldName)) {
            values = new String[]{"PDF", "WORD", "HTML"};
        } else {
            return result;
        }
        for (String value : values) {
            LambdaQueryWrapper<ReportEntity> w = new LambdaQueryWrapper<>();
            if ("report_type".equals(fieldName)) {
                w.eq(ReportEntity::getReportType, value);
            } else {
                w.eq(ReportEntity::getFormat, value);
            }
            Long count = reportMapper.selectCount(w);
            result.put(value, count == null ? 0L : count);
        }
        return result;
    }
}
