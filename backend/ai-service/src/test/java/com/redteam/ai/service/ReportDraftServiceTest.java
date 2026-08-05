package com.redteam.ai.service;

import com.redteam.ai.client.LlmClient;
import com.redteam.ai.config.LlmConfig;
import com.redteam.ai.vo.ReportDraft;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ReportDraftService} 单元测试
 *
 * <p>使用 Mockito 模拟 {@link LlmClient}，覆盖 LLM 正常返回、不可用降级、
 * JSON 解析失败降级、查询已有草稿四类场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportDraftServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private RestTemplate restTemplate;

    private ReportDraftService reportDraftService;

    private LlmConfig llmConfig;

    @BeforeEach
    void setUp() {
        llmConfig = new LlmConfig();
        llmConfig.setModel("qwen2.5:7b");

        reportDraftService = new ReportDraftService();
        ReflectionTestUtils.setField(reportDraftService, "llmClient", llmClient);
        ReflectionTestUtils.setField(reportDraftService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(reportDraftService, "llmConfig", llmConfig);
        ReflectionTestUtils.setField(reportDraftService, "reportServiceUrl", "http://localhost:8092");
    }

    /**
     * 用例 1: LLM 正常返回有效 JSON，应解析出 conclusion 与 recommendations
     */
    @Test
    @DisplayName("testGenerateDraft_Success - LLM 正常返回应解析成功")
    void testGenerateDraft_Success() {
        Long reportId = 1001L;
        when(llmClient.isAvailable()).thenReturn(true);
        String llmResponse = "{\"conclusion\":\"本期报告共分析 50 份文件，发现多处可疑通信行为。"
                + "标签分布显示恶意文件占比 30%。\","
                + "\"recommendations\":[\"封禁恶意IP\",\"加强防火墙规则\",\"定期更新特征库\"]}";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        ReportDraft result = reportDraftService.generateDraft(
                reportId,
                "{\"fileCount\":50}",
                "[\"file1.log\",\"file2.log\"]",
                "{\"恶意文件\":15,\"正常文件\":35}");

        // 验证 LLM 被使用
        assertTrue(result.isLlmUsed());
        assertEquals(reportId, result.getReportId());
        assertEquals("qwen2.5:7b", result.getModel());
        // 结论应包含 LLM 返回的关键内容
        assertNotNull(result.getConclusion());
        assertTrue(result.getConclusion().contains("50 份文件"));
        // 建议行动应解析为列表
        assertNotNull(result.getRecommendations());
        assertEquals(3, result.getRecommendations().size());
        assertTrue(result.getRecommendations().contains("封禁恶意IP"));
        // token 用量应被记录
        assertNotNull(result.getTokensUsed());
        assertTrue(result.getTokensUsed() > 0);
        // 创建时间不应为空
        assertNotNull(result.getCreatedAt());
        // 验证 LLM 被调用一次
        verify(llmClient).chat(anyString(), anyString());
    }

    /**
     * 用例 2: LLM 不可用（isAvailable=false），应降级为模板文本
     */
    @Test
    @DisplayName("testGenerateDraft_LlmUnavailable - LLM 不可用应降级返回模板")
    void testGenerateDraft_LlmUnavailable() {
        Long reportId = 1002L;
        when(llmClient.isAvailable()).thenReturn(false);

        ReportDraft result = reportDraftService.generateDraft(
                reportId,
                "{\"fileCount\":20}",
                "[\"file1.log\"]",
                "{\"恶意\":6,\"扫描\":4}");

        // 验证 LLM 未被使用
        assertFalse(result.isLlmUsed());
        assertEquals(reportId, result.getReportId());
        // 结论应为模板文本（包含文件数与标签数）
        assertNotNull(result.getConclusion());
        assertTrue(result.getConclusion().contains("20 份文件"),
                "模板文本应包含文件数");
        assertTrue(result.getConclusion().contains("2 个关键标签"),
                "模板文本应包含标签数");
        assertTrue(result.getConclusion().contains("详细分析请参考报告正文"));
        // 建议行动应为空
        assertNotNull(result.getRecommendations());
        assertTrue(result.getRecommendations().isEmpty());
        // 应记录错误信息
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("LLM"));
        // 验证 LLM chat 未被调用
        verify(llmClient, org.mockito.Mockito.never()).chat(anyString(), anyString());
    }

    /**
     * 用例 3: JSON 解析失败时，原始文本应作为 conclusion，recommendations 为空
     */
    @Test
    @DisplayName("testGenerateDraft_JsonParseError - JSON 解析失败原文作为 conclusion")
    void testGenerateDraft_JsonParseError() {
        Long reportId = 1003L;
        when(llmClient.isAvailable()).thenReturn(true);
        String nonJsonResponse = "本期报告分析了 30 份文件，发现若干威胁行为，建议加强监控。这不是一个有效的JSON。";
        when(llmClient.chat(anyString(), anyString())).thenReturn(nonJsonResponse);

        ReportDraft result = reportDraftService.generateDraft(
                reportId,
                "{\"fileCount\":30}",
                "[\"file1.log\"]",
                "{\"恶意\":10}");

        // 验证 LLM 被使用（JSON 解析失败但 LLM 返回了内容）
        assertTrue(result.isLlmUsed());
        assertEquals(reportId, result.getReportId());
        // 原始文本作为 conclusion
        assertEquals(nonJsonResponse, result.getConclusion());
        // 建议行动为空
        assertNotNull(result.getRecommendations());
        assertTrue(result.getRecommendations().isEmpty());
        // 模型与 token 用量应被记录
        assertEquals("qwen2.5:7b", result.getModel());
        assertNotNull(result.getTokensUsed());
    }

    /**
     * 用例 4: 查询已有草稿应正确返回缓存结果
     */
    @Test
    @DisplayName("testGetDraft - 查询已有草稿")
    void testGetDraft() {
        Long reportId = 1004L;
        // 先生成一份草稿
        when(llmClient.isAvailable()).thenReturn(true);
        String llmResponse = "{\"conclusion\":\"测试结论段落\","
                + "\"recommendations\":[\"建议1\",\"建议2\"]}";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);
        reportDraftService.generateDraft(reportId, "{}", "[]", "{}");

        // 查询已缓存的草稿
        ReportDraft result = reportDraftService.getDraft(reportId);

        assertNotNull(result);
        assertEquals(reportId, result.getReportId());
        assertEquals("测试结论段落", result.getConclusion());
        assertEquals(2, result.getRecommendations().size());
        assertTrue(result.isLlmUsed());

        // 验证 systemPrompt 中包含统计数据
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(promptCaptor.capture(), anyString());
        String systemPrompt = promptCaptor.getValue();
        assertTrue(systemPrompt.contains("红方安全报告撰写专家"),
                "systemPrompt 应包含角色定义");
        assertTrue(systemPrompt.contains("{}") || systemPrompt.contains("统计数据"),
                "systemPrompt 应包含统计数据占位");
    }

    /**
     * 用例 5: 查询不存在的草稿应返回 null
     */
    @Test
    @DisplayName("testGetDraft_NotFound - 查询不存在的草稿返回 null")
    void testGetDraft_NotFound() {
        Long reportId = 9999L;
        ReportDraft result = reportDraftService.getDraft(reportId);
        assertNull(result);
    }

    /**
     * 用例 6: 验证系统 Prompt 包含所有输入数据
     */
    @Test
    @DisplayName("testGenerateDraft_PromptContainsData - Prompt 应包含统计数据与文件列表")
    void testGenerateDraft_PromptContainsData() {
        Long reportId = 1005L;
        when(llmClient.isAvailable()).thenReturn(true);
        when(llmClient.chat(anyString(), anyString())).thenReturn(
                "{\"conclusion\":\"ok\",\"recommendations\":[]}");

        String statsJson = "{\"fileCount\":42,\"iocCount\":10}";
        String fileListJson = "[{\"name\":\"a.log\"},{\"name\":\"b.log\"}]";
        String tagDistributionJson = "{\"恶意\":12,\"扫描\":8}";

        reportDraftService.generateDraft(reportId, statsJson, fileListJson, tagDistributionJson);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(promptCaptor.capture(), anyString());

        String systemPrompt = promptCaptor.getValue();
        // Prompt 应包含所有输入 JSON
        assertTrue(systemPrompt.contains(statsJson),
                "systemPrompt 应包含统计数据 JSON");
        assertTrue(systemPrompt.contains(fileListJson),
                "systemPrompt 应包含文件列表 JSON");
        assertTrue(systemPrompt.contains(tagDistributionJson),
                "systemPrompt 应包含标签分布 JSON");
        // Prompt 应包含输出格式要求
        assertTrue(systemPrompt.contains("conclusion"));
        assertTrue(systemPrompt.contains("recommendations"));
    }
}
