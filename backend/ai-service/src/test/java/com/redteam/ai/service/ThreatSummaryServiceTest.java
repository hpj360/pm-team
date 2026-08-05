package com.redteam.ai.service;

import com.redteam.ai.client.LlmClient;
import com.redteam.ai.config.LlmConfig;
import com.redteam.common.api.dto.NerEntityVO;
import com.redteam.common.entity.ThreatSummaryEntity;
import com.redteam.common.mapper.ThreatSummaryMapper;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ThreatSummaryService} 单元测试
 *
 * <p>使用 Mockito 模拟 {@link LlmClient} 与 {@link ThreatSummaryMapper}，
 * 覆盖 LLM 正常、不可用、JSON 解析失败、空文本、长文本截断、查询摘要六类场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThreatSummaryServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private ThreatSummaryMapper summaryMapper;

    private ThreatSummaryService threatSummaryService;

    private LlmConfig llmConfig;

    @BeforeEach
    void setUp() {
        llmConfig = new LlmConfig();
        llmConfig.setModel("qwen2.5:7b");

        threatSummaryService = new ThreatSummaryService();
        ReflectionTestUtils.setField(threatSummaryService, "llmClient", llmClient);
        ReflectionTestUtils.setField(threatSummaryService, "summaryMapper", summaryMapper);
        ReflectionTestUtils.setField(threatSummaryService, "llmConfig", llmConfig);

        // insert 时模拟 MyBatis-Plus 自动回填主键
        when(summaryMapper.insert(any(ThreatSummaryEntity.class))).thenAnswer(invocation -> {
            ThreatSummaryEntity e = invocation.getArgument(0);
            e.setId(1L);
            return 1;
        });
        when(summaryMapper.updateById(any(ThreatSummaryEntity.class))).thenReturn(1);
    }

    /**
     * 用例 1: LLM 返回正常 JSON，应解析为结构化字段且 status=1
     */
    @Test
    @DisplayName("testGenerateSummary_Success - LLM 返回正常 JSON 应解析成功")
    void testGenerateSummary_Success() {
        String llmResponse = "{\"summary\":\"文件包含可疑IP 192.168.1.1 进行端口扫描\","
                + "\"keyFindings\":[\"发现恶意IP\",\"端口扫描行为\",\"可疑通信\"],"
                + "\"suggestedActions\":[\"封禁IP\",\"加强防火墙规则\"]}";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        List<NerEntityVO> nerEntities = buildNerEntities();
        ThreatSummaryEntity result = threatSummaryService.generateSummary(
                100L, "文件内容包含 192.168.1.1 端口扫描行为",
                "scan.log", "txt", nerEntities, Arrays.asList("恶意", "扫描"));

        // 验证最终状态：成功
        assertEquals(1, result.getStatus());
        assertEquals(100L, result.getFileId());
        assertEquals("qwen2.5:7b", result.getModel());
        assertTrue(result.getSummary().contains("192.168.1.1"));
        assertTrue(result.getKeyFindings().contains("发现恶意IP"));
        assertTrue(result.getSuggestedActions().contains("封禁IP"));
        assertNotNull(result.getTokensUsed());
        // 验证 insert + update 各调用一次
        verify(summaryMapper).insert(any(ThreatSummaryEntity.class));
        verify(summaryMapper).updateById(any(ThreatSummaryEntity.class));
    }

    /**
     * 用例 2: LLM 不可用（返回 null）应降级为 status=2 并记录错误信息
     */
    @Test
    @DisplayName("testGenerateSummary_LlmUnavailable - LLM 不可用应降级失败")
    void testGenerateSummary_LlmUnavailable() {
        when(llmClient.chat(anyString(), anyString())).thenReturn(null);

        ThreatSummaryEntity result = threatSummaryService.generateSummary(
                101L, "测试文本", "test.txt", "txt",
                Collections.emptyList(), Collections.emptyList());

        // 验证最终状态：失败
        assertEquals(2, result.getStatus());
        assertEquals(101L, result.getFileId());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("LLM"));
        // 验证 insert + update 各调用一次
        verify(summaryMapper).insert(any(ThreatSummaryEntity.class));
        verify(summaryMapper).updateById(any(ThreatSummaryEntity.class));
    }

    /**
     * 用例 3: JSON 解析失败时，原始文本应作为 summary，status=1
     */
    @Test
    @DisplayName("testGenerateSummary_JsonParseError - JSON 解析失败原文作为 summary")
    void testGenerateSummary_JsonParseError() {
        String llmResponse = "这不是一个有效的JSON，而是一段普通文本响应。";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        ThreatSummaryEntity result = threatSummaryService.generateSummary(
                102L, "测试文本", "test.txt", "txt",
                Collections.emptyList(), Collections.emptyList());

        // 验证最终状态：成功（JSON 解析失败但 LLM 返回了内容）
        assertEquals(1, result.getStatus());
        // 原始文本作为 summary
        assertEquals(llmResponse, result.getSummary());
        // 关键发现、建议行动为空数组字符串
        assertEquals("[]", result.getKeyFindings());
        assertEquals("[]", result.getSuggestedActions());
    }

    /**
     * 用例 4: 空文本输入应正常处理（不抛异常）
     */
    @Test
    @DisplayName("testGenerateSummary_EmptyText - 空文本输入应正常处理")
    void testGenerateSummary_EmptyText() {
        String llmResponse = "{\"summary\":\"未发现明显威胁\","
                + "\"keyFindings\":[\"无安全威胁\"],"
                + "\"suggestedActions\":[]}";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        ThreatSummaryEntity result = threatSummaryService.generateSummary(
                103L, "", "empty.txt", "txt",
                null, null);

        // 验证最终状态：成功
        assertEquals(1, result.getStatus());
        assertEquals("未发现明显威胁", result.getSummary());
        // 空输入不应抛异常
        verify(summaryMapper).insert(any(ThreatSummaryEntity.class));
    }

    /**
     * 用例 5: 查询已有摘要应正确委托给 Mapper
     */
    @Test
    @DisplayName("testGetByFileId - 查询已有摘要")
    void testGetByFileId() {
        ThreatSummaryEntity mockEntity = new ThreatSummaryEntity();
        mockEntity.setId(10L);
        mockEntity.setFileId(200L);
        mockEntity.setStatus(1);
        mockEntity.setSummary("测试摘要");
        when(summaryMapper.selectByFileId(eq(200L))).thenReturn(mockEntity);

        ThreatSummaryEntity result = threatSummaryService.getByFileId(200L);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(200L, result.getFileId());
        assertEquals("测试摘要", result.getSummary());
        verify(summaryMapper).selectByFileId(200L);
    }

    /**
     * 用例 6: 长文本应截断到前 4000 字符（通过验证调用 LLM 时传入的 systemPrompt 不包含截断后的尾部唯一标记）
     */
    @Test
    @DisplayName("testGenerateSummary_LongText - 长文本截断到 4000 字符")
    void testGenerateSummary_LongText() {
        // 构造长文本：前 4000 字符为 'A'，第 4000 字符之后放置唯一标记
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4000; i++) {
            sb.append('A');
        }
        // 在 4000 字符之后添加唯一标记，截断后该标记不应出现在 systemPrompt 中
        String uniqueTailMarker = "UNIQUE_TAIL_MARKER_AFTER_TRUNCATION_2026";
        sb.append(uniqueTailMarker);
        // 再补一段字符使总长度明显大于 4000
        for (int i = 0; i < 500; i++) {
            sb.append('Z');
        }
        String longText = sb.toString();
        assertTrue(longText.length() > 4000);

        when(llmClient.chat(anyString(), anyString())).thenReturn("{\"summary\":\"ok\"}");

        threatSummaryService.generateSummary(
                104L, longText, "big.log", "txt",
                Collections.emptyList(), Collections.emptyList());

        // 通过 ArgumentCaptor 捕获传给 LLM 的 systemPrompt
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).chat(promptCaptor.capture(), anyString());

        String systemPrompt = promptCaptor.getValue();
        // 截断后的 systemPrompt 不应包含位于 4000 字符之后的唯一标记
        assertTrue(!systemPrompt.contains(uniqueTailMarker),
                "systemPrompt 不应包含截断后的尾部唯一标记");
        // 同时验证 systemPrompt 中确实包含原始文本的开头部分
        assertTrue(systemPrompt.contains("AAAA"),
                "systemPrompt 应包含原始文本的开头部分");
    }


    /**
     * 构造测试用 NER 实体列表
     *
     * @return NER 实体列表
     */
    private List<NerEntityVO> buildNerEntities() {
        NerEntityVO ip = new NerEntityVO();
        ip.setEntityText("192.168.1.1");
        ip.setEntityType("IP");
        ip.setConfidence(0.95f);

        NerEntityVO cve = new NerEntityVO();
        cve.setEntityText("CVE-2024-1234");
        cve.setEntityType("CVE");
        cve.setConfidence(0.88f);

        return Arrays.asList(ip, cve);
    }
}
