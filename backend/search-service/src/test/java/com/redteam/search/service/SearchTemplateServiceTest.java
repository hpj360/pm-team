package com.redteam.search.service;

import com.redteam.common.api.dto.SearchTemplateDTO;
import com.redteam.common.api.dto.SearchTemplateVO;
import com.redteam.common.entity.SearchTemplateEntity;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.SearchTemplateMapper;
import com.redteam.common.util.UserContext;
import com.redteam.search.service.impl.SearchTemplateServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 搜索模板服务单元测试
 *
 * <p>使用 Mockito mock {@link SearchTemplateMapper}，验证 {@link SearchTemplateServiceImpl}
 * 的保存、查询、删除及所有权校验逻辑。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchTemplateServiceTest {

    @Mock
    private SearchTemplateMapper searchTemplateMapper;

    @InjectMocks
    private SearchTemplateServiceImpl searchTemplateService;

    /**
     * 每个测试前设置用户上下文
     */
    @BeforeEach
    void setUp() {
        UserContext.setUserId(100L);
    }

    /**
     * 每个测试后清理用户上下文
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==================== saveTemplate ====================

    /**
     * 保存模板成功
     */
    @Test
    @DisplayName("保存模板成功：应调用 insert 并返回 VO")
    void testSaveTemplate_Success() {
        SearchTemplateDTO dto = new SearchTemplateDTO();
        dto.setName("渗透测试模板");
        dto.setParamsJson("{\"keyword\":\"sqlmap\",\"mode\":\"boolean\"}");

        // 模拟 insert 填充自增ID
        when(searchTemplateMapper.insert(any(SearchTemplateEntity.class))).thenAnswer(invocation -> {
            SearchTemplateEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            return 1;
        });

        SearchTemplateVO vo = searchTemplateService.saveTemplate(dto);

        // 验证 mapper 调用
        ArgumentCaptor<SearchTemplateEntity> captor = ArgumentCaptor.forClass(SearchTemplateEntity.class);
        verify(searchTemplateMapper, times(1)).insert(captor.capture());

        SearchTemplateEntity saved = captor.getValue();
        assertEquals(100L, saved.getUserId(), "保存的 userId 应为当前用户");
        assertEquals("渗透测试模板", saved.getName(), "保存的 name 应与入参一致");
        assertEquals("{\"keyword\":\"sqlmap\",\"mode\":\"boolean\"}", saved.getParamsJson(),
                "保存的 paramsJson 应与入参一致");

        // 验证返回 VO
        assertNotNull(vo, "返回 VO 不应为空");
        assertEquals(1L, vo.getId(), "返回 VO 的 id 应为 1");
        assertEquals("渗透测试模板", vo.getName(), "返回 VO 的 name 应一致");
        assertEquals("{\"keyword\":\"sqlmap\",\"mode\":\"boolean\"}", vo.getParamsJson(),
                "返回 VO 的 paramsJson 应一致");
    }

    /**
     * 名称为空应校验失败
     */
    @Test
    @DisplayName("保存模板：名称为空应抛出 BusinessException")
    void testSaveTemplate_EmptyName() {
        SearchTemplateDTO dto = new SearchTemplateDTO();
        dto.setName("");
        dto.setParamsJson("{\"keyword\":\"test\"}");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> searchTemplateService.saveTemplate(dto),
                "名称为空应抛出 BusinessException");
        assertTrue(ex.getMessage().contains("模板名称不能为空"), "异常消息应包含'模板名称不能为空'");
        verify(searchTemplateMapper, never()).insert(any(SearchTemplateEntity.class));
    }

    /**
     * paramsJson 为空应校验失败
     */
    @Test
    @DisplayName("保存模板：paramsJson 为空应抛出 BusinessException")
    void testSaveTemplate_EmptyParamsJson() {
        SearchTemplateDTO dto = new SearchTemplateDTO();
        dto.setName("测试模板");
        dto.setParamsJson("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> searchTemplateService.saveTemplate(dto),
                "paramsJson 为空应抛出 BusinessException");
        assertTrue(ex.getMessage().contains("搜索条件不能为空"), "异常消息应包含'搜索条件不能为空'");
        verify(searchTemplateMapper, never()).insert(any(SearchTemplateEntity.class));
    }

    // ==================== listTemplates ====================

    /**
     * 按用户查询模板列表
     */
    @Test
    @DisplayName("查询模板列表：应按当前用户ID查询")
    void testListTemplates_ByUserId() {
        SearchTemplateEntity e1 = buildEntity(1L, 100L, "模板一", "{\"k\":\"v1\"}");
        SearchTemplateEntity e2 = buildEntity(2L, 100L, "模板二", "{\"k\":\"v2\"}");
        when(searchTemplateMapper.selectByUserId(eq(100L))).thenReturn(Arrays.asList(e1, e2));

        List<SearchTemplateVO> result = searchTemplateService.listTemplates();

        // 验证按当前用户ID查询
        verify(searchTemplateMapper, times(1)).selectByUserId(eq(100L));
        assertEquals(2, result.size(), "应返回 2 条模板");
        assertEquals(1L, result.get(0).getId(), "第一条 id 应为 1");
        assertEquals("模板一", result.get(0).getName(), "第一条 name 应为'模板一'");
        assertEquals(2L, result.get(1).getId(), "第二条 id 应为 2");
        assertEquals("模板二", result.get(1).getName(), "第二条 name 应为'模板二'");
    }

    /**
     * 查询模板列表为空
     */
    @Test
    @DisplayName("查询模板列表：无数据时应返回空列表")
    void testListTemplates_Empty() {
        when(searchTemplateMapper.selectByUserId(eq(100L))).thenReturn(Collections.emptyList());

        List<SearchTemplateVO> result = searchTemplateService.listTemplates();

        assertNotNull(result, "返回列表不应为 null");
        assertTrue(result.isEmpty(), "返回列表应为空");
        verify(searchTemplateMapper, times(1)).selectByUserId(eq(100L));
    }

    // ==================== deleteTemplate ====================

    /**
     * 删除自己的模板成功
     */
    @Test
    @DisplayName("删除模板成功：删除自己的模板应调用 deleteById")
    void testDeleteTemplate_Success() {
        SearchTemplateEntity entity = buildEntity(1L, 100L, "我的模板", "{\"k\":\"v\"}");
        when(searchTemplateMapper.selectById(eq(1L))).thenReturn(entity);
        when(searchTemplateMapper.deleteById(eq(1L))).thenReturn(1);

        searchTemplateService.deleteTemplate(1L);

        verify(searchTemplateMapper, times(1)).selectById(eq(1L));
        verify(searchTemplateMapper, times(1)).deleteById(eq(1L));
    }

    /**
     * 删除他人模板应抛异常
     */
    @Test
    @DisplayName("删除模板：删除他人模板应抛出 BusinessException 且不调用 deleteById")
    void testDeleteTemplate_NotOwner() {
        // 模板归属用户 999L，当前用户为 100L
        SearchTemplateEntity entity = buildEntity(1L, 999L, "他人模板", "{\"k\":\"v\"}");
        when(searchTemplateMapper.selectById(eq(1L))).thenReturn(entity);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> searchTemplateService.deleteTemplate(1L),
                "删除他人模板应抛出 BusinessException");
        assertTrue(ex.getMessage().contains("无权删除"), "异常消息应包含'无权删除'");
        // 不应执行删除
        verify(searchTemplateMapper, never()).deleteById(anyLong());
    }

    /**
     * 删除不存在的模板应抛异常
     */
    @Test
    @DisplayName("删除模板：模板不存在应抛出 BusinessException")
    void testDeleteTemplate_NotFound() {
        when(searchTemplateMapper.selectById(eq(99L))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> searchTemplateService.deleteTemplate(99L),
                "模板不存在应抛出 BusinessException");
        assertTrue(ex.getMessage().contains("不存在"), "异常消息应包含'不存在'");
        verify(searchTemplateMapper, never()).deleteById(anyLong());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用实体
     */
    private SearchTemplateEntity buildEntity(Long id, Long userId, String name, String paramsJson) {
        SearchTemplateEntity entity = new SearchTemplateEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName(name);
        entity.setParamsJson(paramsJson);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
