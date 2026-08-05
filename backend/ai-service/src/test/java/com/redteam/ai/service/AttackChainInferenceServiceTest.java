package com.redteam.ai.service;

import com.redteam.ai.client.LlmClient;
import com.redteam.ai.config.LlmConfig;
import com.redteam.common.entity.AttackChainEntity;
import com.redteam.common.mapper.AttackChainMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AttackChainInferenceService} 单元测试
 *
 * <p>覆盖推理成功、LLM 不可用降级、profile-service 不可用降级、JSON 解析失败降级、
 * 查询已有结果五类场景。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
class AttackChainInferenceServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private AttackChainMapper attackChainMapper;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AttackChainInferenceService service;

    /**
     * 测试前注入 LlmConfig（非 Mock）与 profileServiceUrl
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "llmConfig", new LlmConfig());
        ReflectionTestUtils.setField(service, "profileServiceUrl", "http://localhost:8085");
    }

    /**
     * 推理成功 - LLM 正常返回有效 JSON，应解析出攻击路径与置信度
     */
    @Test
    @DisplayName("推理成功 - LLM 正常返回应解析攻击路径")
    void testInferAttackChain_Success() {
        Long fileId = 1001L;
        when(llmClient.isAvailable()).thenReturn(true);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"relations\":[]}");
        String llmResponse = "{\"attackPaths\":[{\"path\":\"入口→横向→目标\",\"steps\":[\"步骤1\"],\"entities\":[\"e1\"]}],\"confidence\":\"HIGH\",\"reasoning\":\"基于实体关系推理\"}";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        AttackChainEntity result = service.inferAttackChain(fileId, buildNerEntities(), buildTags(), "文件上下文");

        assertNotNull(result);
        assertEquals(1, result.getStatus());
        assertEquals("HIGH", result.getConfidence());
        assertTrue(result.getAttackPaths().contains("入口→横向→目标"));
        assertEquals("基于实体关系推理", result.getReasoning());
        assertEquals("qwen2.5:7b", result.getModel());
        verify(attackChainMapper).insert(any(AttackChainEntity.class));
        verify(attackChainMapper).updateById(any(AttackChainEntity.class));
    }

    /**
     * LLM 不可用降级 - isAvailable 返回 false，应标记 status=2 且不调用 LLM
     */
    @Test
    @DisplayName("LLM 不可用 - 应降级返回失败状态")
    void testInferAttackChain_LlmUnavailable() {
        Long fileId = 1002L;
        when(llmClient.isAvailable()).thenReturn(false);

        AttackChainEntity result = service.inferAttackChain(fileId, buildNerEntities(), buildTags(), "文件上下文");

        assertNotNull(result);
        assertEquals(2, result.getStatus());
        assertEquals("LLM 服务不可用", result.getErrorMessage());
        verify(llmClient, never()).chat(anyString(), anyString());
        verify(restTemplate, never()).postForObject(anyString(), any(), eq(String.class));
    }

    /**
     * profile-service 不可用降级 - RestTemplate 抛异常，应用空关系数据继续推理
     */
    @Test
    @DisplayName("profile-service 不可用 - 应降级继续推理")
    void testInferAttackChain_ProfileServiceDown() {
        Long fileId = 1003L;
        when(llmClient.isAvailable()).thenReturn(true);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("profile-service 连接拒绝"));
        String llmResponse = "{\"attackPaths\":[{\"path\":\"A→B\",\"steps\":[\"s1\"],\"entities\":[\"e1\"]}],\"confidence\":\"MEDIUM\",\"reasoning\":\"关系数据缺失但可推理\"}";
        when(llmClient.chat(anyString(), anyString())).thenReturn(llmResponse);

        AttackChainEntity result = service.inferAttackChain(fileId, buildNerEntities(), buildTags(), "文件上下文");

        assertNotNull(result);
        assertEquals(1, result.getStatus());
        assertEquals("MEDIUM", result.getConfidence());
        assertEquals("关系数据缺失但可推理", result.getReasoning());
        verify(llmClient).chat(anyString(), anyString());
    }

    /**
     * JSON 解析失败降级 - LLM 返回非 JSON 文本，应原文作为 reasoning、attackPaths 为空数组
     */
    @Test
    @DisplayName("JSON 解析失败 - 应原文作为 reasoning")
    void testInferAttackChain_JsonParseError() {
        Long fileId = 1004L;
        when(llmClient.isAvailable()).thenReturn(true);
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn("{\"relations\":[]}");
        String nonJsonResponse = "抱歉，当前数据不足以推理出攻击链。";
        when(llmClient.chat(anyString(), anyString())).thenReturn(nonJsonResponse);

        AttackChainEntity result = service.inferAttackChain(fileId, buildNerEntities(), buildTags(), "文件上下文");

        assertNotNull(result);
        assertEquals(1, result.getStatus());
        assertEquals("[]", result.getAttackPaths());
        assertEquals("LOW", result.getConfidence());
        assertEquals(nonJsonResponse, result.getReasoning());
    }

    /**
     * 查询已有推理结果 - Mapper 返回实体时应透传
     */
    @Test
    @DisplayName("getByFileId - 应返回已有推理结果")
    void testGetByFileId() {
        Long fileId = 1005L;
        AttackChainEntity mockEntity = new AttackChainEntity();
        mockEntity.setId(10L);
        mockEntity.setFileId(fileId);
        mockEntity.setStatus(1);
        mockEntity.setConfidence("HIGH");
        when(attackChainMapper.selectByFileId(fileId)).thenReturn(mockEntity);

        AttackChainEntity result = service.getByFileId(fileId);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(fileId, result.getFileId());
        assertEquals(1, result.getStatus());
        assertEquals("HIGH", result.getConfidence());
        verify(attackChainMapper).selectByFileId(fileId);
    }

    /**
     * 查询已有推理结果 - Mapper 抛异常时应返回 null（不向外抛）
     */
    @Test
    @DisplayName("getByFileId - 异常时应返回 null")
    void testGetByFileId_Exception() {
        Long fileId = 1006L;
        when(attackChainMapper.selectByFileId(fileId)).thenThrow(new RuntimeException("DB 异常"));

        AttackChainEntity result = service.getByFileId(fileId);

        assertNull(result);
    }

    /**
     * 构造 NER 实体列表
     */
    private List<Map<String, Object>> buildNerEntities() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> entity = new HashMap<>();
        entity.put("value", "192.168.1.1");
        entity.put("type", "IP");
        list.add(entity);
        return list;
    }

    /**
     * 构造标签列表
     */
    private List<String> buildTags() {
        List<String> tags = new ArrayList<>();
        tags.add("恶意文件");
        tags.add("渗透工具");
        return tags;
    }
}
