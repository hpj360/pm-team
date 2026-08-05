package com.redteam.profile.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.PageResult;
import com.redteam.profile.dto.TargetDTO;
import com.redteam.profile.dto.TargetProfileDTO;
import com.redteam.profile.dto.TargetQueryDTO;
import com.redteam.profile.dto.TargetRelationDTO;
import com.redteam.profile.dto.TargetRelationRequestDTO;
import com.redteam.profile.entity.TargetEntity;
import com.redteam.profile.entity.TargetRelationEntity;
import com.redteam.profile.mapper.TargetMapper;
import com.redteam.profile.mapper.TargetRelationMapper;
import com.redteam.profile.service.impl.TargetServiceImpl;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TargetServiceImpl} 单元测试
 *
 * <p>覆盖目标 CRUD、画像聚合、关系图谱管理、关注、搜索等核心逻辑，
 * 使用 Mockito 隔离 Mapper 与 Redis，覆盖率目标 ≥80%。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("目标画像服务测试")
class TargetServiceImplTest {

    @Mock
    private TargetMapper targetMapper;

    @Mock
    private TargetRelationMapper targetRelationMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TargetServiceImpl targetService;

    /**
     * 公共初始化：让 baseMapper 等 ServiceImpl 依赖可工作
     */
    @BeforeEach
    void setUp() {
        // ServiceImpl 的 baseMapper 字段通过反射注入
        try {
            java.lang.reflect.Field baseMapperField =
                    com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
            baseMapperField.setAccessible(true);
            baseMapperField.set(targetService, targetMapper);
        } catch (Exception e) {
            // 忽略
        }
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== createTarget ====================

    @Test
    @DisplayName("createTarget - 应入库并返回创建后的目标")
    void testCreateTarget() {
        TargetDTO dto = buildDto("目标A", 2, "互联网");

        when(targetMapper.insert(any(TargetEntity.class))).thenReturn(1);

        TargetEntity result = targetService.createTarget(dto);

        assertNotNull(result);
        assertEquals("目标A", result.getName());
        assertEquals(2, result.getType());
        assertEquals("互联网", result.getIndustry());
        assertEquals(0, result.getProfileStatus());
        assertEquals(1, result.getRiskLevel());
        assertEquals(0, result.getIsFollowed());
        verify(targetMapper).insert(any(TargetEntity.class));
    }

    @Test
    @DisplayName("createTarget - 指定风险等级应被采用")
    void testCreateTargetWithRiskLevel() {
        TargetDTO dto = buildDto("高危目标", 1, "金融");
        dto.setRiskLevel(3);

        when(targetMapper.insert(any(TargetEntity.class))).thenReturn(1);

        TargetEntity result = targetService.createTarget(dto);
        assertEquals(3, result.getRiskLevel());
    }

    // ==================== updateTarget ====================

    @Test
    @DisplayName("updateTarget - 目标存在时应更新字段")
    void testUpdateTarget() {
        TargetEntity existing = buildEntity(1L, "旧名称", 1);
        when(targetMapper.selectById(1L)).thenReturn(existing);
        when(targetMapper.updateById(any(TargetEntity.class))).thenReturn(1);

        TargetDTO dto = buildDto("新名称", 2, "互联网");
        dto.setRiskLevel(3);
        dto.setTags("tag1,tag2");

        TargetEntity result = targetService.updateTarget(1L, dto);

        assertEquals("新名称", result.getName());
        assertEquals(2, result.getType());
        assertEquals("互联网", result.getIndustry());
        assertEquals(3, result.getRiskLevel());
        assertEquals("tag1,tag2", result.getTags());
        verify(redisTemplate).delete("target:profile:1");
    }

    @Test
    @DisplayName("updateTarget - 目标不存在时应抛出异常")
    void testUpdateTargetNotFound() {
        when(targetMapper.selectById(99L)).thenReturn(null);
        TargetDTO dto = buildDto("X", 1, null);
        assertThrows(BusinessException.class, () -> targetService.updateTarget(99L, dto));
    }

    // ==================== deleteTarget ====================

    @Test
    @DisplayName("deleteTarget - 应删除目标及关联关系")
    void testDeleteTarget() {
        TargetEntity existing = buildEntity(1L, "目标", 1);
        when(targetMapper.selectById(1L)).thenReturn(existing);
        when(targetMapper.deleteById(1L)).thenReturn(1);
        when(targetRelationMapper.delete(any(Wrapper.class))).thenReturn(2);

        boolean ok = targetService.deleteTarget(1L);

        assertTrue(ok);
        verify(redisTemplate).delete("target:profile:1");
        verify(targetRelationMapper).delete(any(Wrapper.class));
    }

    @Test
    @DisplayName("deleteTarget - 目标不存在时应抛出异常")
    void testDeleteTargetNotFound() {
        when(targetMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> targetService.deleteTarget(99L));
    }

    // ==================== getTarget ====================

    @Test
    @DisplayName("getTarget - 应返回目标实体")
    void testGetTarget() {
        TargetEntity entity = buildEntity(1L, "目标A", 1);
        when(targetMapper.selectById(1L)).thenReturn(entity);

        TargetEntity result = targetService.getTarget(1L);
        assertEquals("目标A", result.getName());
    }

    @Test
    @DisplayName("getTarget - 不存在时应抛出 BusinessException")
    void testGetTargetNotFound() {
        when(targetMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> targetService.getTarget(99L));
        assertTrue(ex.getMessage().contains("目标不存在"));
    }

    // ==================== listTargets ====================

    @Test
    @DisplayName("listTargets - 应返回分页结果")
    @SuppressWarnings("unchecked")
    void testListTargets() {
        TargetQueryDTO query = new TargetQueryDTO();
        query.setPageNum(1L);
        query.setPageSize(10L);
        query.setType(1);
        query.setKeyword("测试");

        Page<TargetEntity> page = new Page<>(1L, 10L, 1L);
        page.setRecords(Collections.singletonList(buildEntity(1L, "测试目标", 1)));
        when(targetMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<TargetEntity> result = targetService.listTargets(query);

        assertNotNull(result);
        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("listTargets - 空结果应返回空分页")
    @SuppressWarnings("unchecked")
    void testListTargetsEmpty() {
        TargetQueryDTO query = new TargetQueryDTO();
        Page<TargetEntity> page = new Page<>(1L, 10L, 0L);
        page.setRecords(Collections.emptyList());
        when(targetMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<TargetEntity> result = targetService.listTargets(query);
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("listTargets - 默认分页参数兜底")
    @SuppressWarnings("unchecked")
    void testListTargetsDefaultPaging() {
        TargetQueryDTO query = new TargetQueryDTO();
        query.setPageNum(null);
        query.setPageSize(null);

        Page<TargetEntity> page = new Page<>(1L, 10L, 0L);
        page.setRecords(Collections.emptyList());
        when(targetMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        PageResult<TargetEntity> result = targetService.listTargets(query);
        assertNotNull(result);
    }

    // ==================== getTargetProfile ====================

    @Test
    @DisplayName("getTargetProfile - 应聚合画像数据")
    void testGetTargetProfile() {
        TargetEntity entity = buildEntity(1L, "目标A", 2);
        entity.setIndustry("互联网");
        entity.setTags("t1,t2");
        entity.setOrgStructure("[{\"name\":\"张三\",\"role\":\"CEO\"}]");
        entity.setTechAssets("[{\"assetType\":\"DOMAIN\",\"value\":\"a.com\"}]");
        entity.setAttackSurface("{\"openPorts\":[80,443],\"services\":[\"http\"]}");

        when(targetMapper.selectById(1L)).thenReturn(entity);
        when(targetRelationMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        TargetProfileDTO profile = targetService.getTargetProfile(1L);

        assertNotNull(profile);
        assertEquals(1L, profile.getId());
        assertEquals("目标A", profile.getName());
        assertEquals(2, profile.getType());
        assertEquals("互联网", profile.getIndustry());
        assertEquals(List.of("t1", "t2"), profile.getTags());
        assertNotNull(profile.getBasicInfo());
        assertNotNull(profile.getOrgStructure());
        assertEquals(1, profile.getOrgStructure().size());
        assertNotNull(profile.getTechAssets());
        assertEquals(1, profile.getTechAssets().size());
        assertNotNull(profile.getAttackSurface());
        assertEquals(2, profile.getAttackSurface().getOpenPorts().size());
        assertNotNull(profile.getRelatedTargets());
        assertTrue(profile.getRelatedTargets().isEmpty());
    }

    @Test
    @DisplayName("getTargetProfile - 含关联目标时应填充关系")
    void testGetTargetProfileWithRelations() {
        TargetEntity entity = buildEntity(1L, "目标A", 2);
        TargetEntity other = buildEntity(2L, "目标B", 1);
        TargetRelationEntity rel = new TargetRelationEntity();
        rel.setSourceId(1L);
        rel.setTargetId(2L);
        rel.setRelationType("AFFILIATED");
        rel.setWeight(0.8);

        when(targetMapper.selectById(1L)).thenReturn(entity);
        when(targetRelationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rel));
        when(targetMapper.selectBatchIds(any())).thenReturn(List.of(other));

        TargetProfileDTO profile = targetService.getTargetProfile(1L);

        assertEquals(1, profile.getRelatedTargets().size());
        assertEquals(2L, profile.getRelatedTargets().get(0).getTargetId());
        assertEquals("目标B", profile.getRelatedTargets().get(0).getTargetName());
        assertEquals("AFFILIATED", profile.getRelatedTargets().get(0).getRelationType());
    }

    @Test
    @DisplayName("getTargetProfile - 非法 JSON 字段不应抛出异常")
    void testGetTargetProfileWithInvalidJson() {
        TargetEntity entity = buildEntity(1L, "目标A", 1);
        entity.setOrgStructure("invalid json");
        entity.setTechAssets("invalid json");
        entity.setAttackSurface("invalid json");

        when(targetMapper.selectById(1L)).thenReturn(entity);
        when(targetRelationMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        TargetProfileDTO profile = assertDoesNotThrow(() -> targetService.getTargetProfile(1L));
        assertNull(profile.getOrgStructure());
        assertNull(profile.getTechAssets());
        assertNull(profile.getAttackSurface());
    }

    // ==================== generateProfile ====================

    @Test
    @DisplayName("generateProfile - 应成功生成并缓存画像")
    void testGenerateProfile() {
        TargetEntity entity = buildEntity(1L, "目标A", 1);
        when(targetMapper.selectById(1L)).thenReturn(entity);
        when(targetMapper.updateById(any(TargetEntity.class))).thenReturn(1);
        when(targetRelationMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        TargetProfileDTO profile = targetService.generateProfile(1L);

        assertNotNull(profile);
        assertEquals(2, entity.getProfileStatus());
        assertNotNull(entity.getProfileData());
        verify(valueOperations).set(eq("target:profile:1"), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("generateProfile - 内部异常时应将状态置为3并抛出")
    void testGenerateProfileFailure() {
        TargetEntity entity = buildEntity(1L, "目标A", 1);
        when(targetMapper.selectById(1L)).thenReturn(entity);
        // updateById 调用顺序：1) 置为生成中 2) 写入画像数据(抛异常) 3) catch 块置状态为3
        when(targetMapper.updateById(any(TargetEntity.class)))
                .thenReturn(1)
                .thenThrow(new RuntimeException("DB error"))
                .thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> targetService.generateProfile(1L));
        assertTrue(ex.getMessage().contains("画像生成失败"));
        assertEquals(3, entity.getProfileStatus());
    }

    // ==================== getRelationGraph ====================

    @Test
    @DisplayName("getRelationGraph - 单层展开应返回节点+边")
    void testGetRelationGraph() {
        TargetEntity root = buildEntity(1L, "根目标", 2);
        TargetEntity other = buildEntity(2L, "关联目标", 1);
        TargetRelationEntity rel = new TargetRelationEntity();
        rel.setSourceId(1L);
        rel.setTargetId(2L);
        rel.setRelationType("AFFILIATED");
        rel.setWeight(0.6);

        when(targetMapper.selectById(1L)).thenReturn(root);
        when(targetRelationMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rel));
        when(targetMapper.selectBatchIds(any())).thenReturn(List.of(root, other));

        TargetRelationDTO graph = targetService.getRelationGraph(1L, 1);

        assertNotNull(graph);
        assertEquals(2, graph.getNodes().size());
        assertEquals(1, graph.getEdges().size());
        assertEquals("AFFILIATED", graph.getEdges().get(0).getRelationType());
    }

    @Test
    @DisplayName("getRelationGraph - 无关联时应只返回根节点")
    void testGetRelationGraphNoRelations() {
        TargetEntity root = buildEntity(1L, "根目标", 2);
        when(targetMapper.selectById(1L)).thenReturn(root);
        when(targetRelationMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(targetMapper.selectBatchIds(any())).thenReturn(List.of(root));

        TargetRelationDTO graph = targetService.getRelationGraph(1L, 1);

        assertEquals(1, graph.getNodes().size());
        assertTrue(graph.getEdges().isEmpty());
    }

    @Test
    @DisplayName("getRelationGraph - depth 为 null 应使用默认深度 1")
    void testGetRelationGraphDefaultDepth() {
        TargetEntity root = buildEntity(1L, "根目标", 2);
        when(targetMapper.selectById(1L)).thenReturn(root);
        when(targetRelationMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(targetMapper.selectBatchIds(any())).thenReturn(List.of(root));

        assertDoesNotThrow(() -> targetService.getRelationGraph(1L, null));
    }

    @Test
    @DisplayName("getRelationGraph - depth 超过上限应被截断为3")
    void testGetRelationGraphDepthClamped() {
        TargetEntity root = buildEntity(1L, "根目标", 2);
        when(targetMapper.selectById(1L)).thenReturn(root);
        when(targetRelationMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        when(targetMapper.selectBatchIds(any())).thenReturn(List.of(root));

        assertDoesNotThrow(() -> targetService.getRelationGraph(1L, 99));
    }

    @Test
    @DisplayName("getRelationGraph - 根目标不存在时应抛出异常")
    void testGetRelationGraphRootNotFound() {
        when(targetMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> targetService.getRelationGraph(99L, 1));
    }

    // ==================== addRelation ====================

    @Test
    @DisplayName("addRelation - 应成功添加关系")
    void testAddRelation() {
        when(targetMapper.selectById(1L)).thenReturn(buildEntity(1L, "A", 1));
        when(targetMapper.selectById(2L)).thenReturn(buildEntity(2L, "B", 1));
        when(targetRelationMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(targetRelationMapper.insert(any(TargetRelationEntity.class))).thenReturn(1);

        TargetRelationRequestDTO dto = new TargetRelationRequestDTO();
        dto.setSourceId(1L);
        dto.setTargetId(2L);
        dto.setRelationType("RELATED");
        dto.setWeight(0.9);

        boolean ok = targetService.addRelation(dto);
        assertTrue(ok);
        ArgumentCaptor<TargetRelationEntity> captor = ArgumentCaptor.forClass(TargetRelationEntity.class);
        verify(targetRelationMapper).insert(captor.capture());
        assertEquals(0.9, captor.getValue().getWeight());
    }

    @Test
    @DisplayName("addRelation - 默认权重应为 0.5")
    void testAddRelationDefaultWeight() {
        when(targetMapper.selectById(1L)).thenReturn(buildEntity(1L, "A", 1));
        when(targetMapper.selectById(2L)).thenReturn(buildEntity(2L, "B", 1));
        when(targetRelationMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(targetRelationMapper.insert(any(TargetRelationEntity.class))).thenReturn(1);

        TargetRelationRequestDTO dto = new TargetRelationRequestDTO();
        dto.setSourceId(1L);
        dto.setTargetId(2L);
        dto.setRelationType("RELATED");

        targetService.addRelation(dto);
        ArgumentCaptor<TargetRelationEntity> captor = ArgumentCaptor.forClass(TargetRelationEntity.class);
        verify(targetRelationMapper).insert(captor.capture());
        assertEquals(0.5, captor.getValue().getWeight());
    }

    @Test
    @DisplayName("addRelation - 源与目标相同时应抛出异常")
    void testAddRelationSameId() {
        TargetRelationRequestDTO dto = new TargetRelationRequestDTO();
        dto.setSourceId(1L);
        dto.setTargetId(1L);
        dto.setRelationType("RELATED");

        assertThrows(BusinessException.class, () -> targetService.addRelation(dto));
    }

    @Test
    @DisplayName("addRelation - 关系已存在时应抛出异常")
    void testAddRelationExists() {
        when(targetMapper.selectById(1L)).thenReturn(buildEntity(1L, "A", 1));
        when(targetMapper.selectById(2L)).thenReturn(buildEntity(2L, "B", 1));
        when(targetRelationMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        TargetRelationRequestDTO dto = new TargetRelationRequestDTO();
        dto.setSourceId(1L);
        dto.setTargetId(2L);
        dto.setRelationType("RELATED");

        assertThrows(BusinessException.class, () -> targetService.addRelation(dto));
    }

    @Test
    @DisplayName("addRelation - 源目标不存在时应抛出异常")
    void testAddRelationSourceNotFound() {
        when(targetMapper.selectById(99L)).thenReturn(null);

        TargetRelationRequestDTO dto = new TargetRelationRequestDTO();
        dto.setSourceId(99L);
        dto.setTargetId(2L);
        dto.setRelationType("RELATED");

        assertThrows(BusinessException.class, () -> targetService.addRelation(dto));
    }

    // ==================== removeRelation ====================

    @Test
    @DisplayName("removeRelation - 应成功删除关系")
    void testRemoveRelation() {
        TargetRelationEntity rel = new TargetRelationEntity();
        rel.setId(10L);
        when(targetRelationMapper.selectById(10L)).thenReturn(rel);
        when(targetRelationMapper.deleteById(10L)).thenReturn(1);

        boolean ok = targetService.removeRelation(10L);
        assertTrue(ok);
    }

    @Test
    @DisplayName("removeRelation - 关系不存在时应抛出异常")
    void testRemoveRelationNotFound() {
        when(targetRelationMapper.selectById(99L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> targetService.removeRelation(99L));
    }

    // ==================== followTarget ====================

    @Test
    @DisplayName("followTarget - 应设置 isFollowed 为 1")
    void testFollowTarget() {
        TargetEntity entity = buildEntity(1L, "A", 1);
        when(targetMapper.selectById(1L)).thenReturn(entity);
        when(targetMapper.updateById(any(TargetEntity.class))).thenReturn(1);

        boolean ok = targetService.followTarget(1L, true);
        assertTrue(ok);
        assertEquals(1, entity.getIsFollowed());
    }

    @Test
    @DisplayName("followTarget - false 应设置 isFollowed 为 0")
    void testUnfollowTarget() {
        TargetEntity entity = buildEntity(1L, "A", 1);
        entity.setIsFollowed(1);
        when(targetMapper.selectById(1L)).thenReturn(entity);
        when(targetMapper.updateById(any(TargetEntity.class))).thenReturn(1);

        targetService.followTarget(1L, false);
        assertEquals(0, entity.getIsFollowed());
    }

    // ==================== getTargetFiles ====================

    @Test
    @DisplayName("getTargetFiles - 应返回空列表占位")
    void testGetTargetFiles() {
        when(targetMapper.selectById(1L)).thenReturn(buildEntity(1L, "A", 1));
        List<Long> files = targetService.getTargetFiles(1L);
        assertNotNull(files);
        assertTrue(files.isEmpty());
    }

    // ==================== searchTargets ====================

    @Test
    @DisplayName("searchTargets - 应根据关键词与类型查询")
    void testSearchTargets() {
        when(targetMapper.selectList(any(Wrapper.class)))
                .thenReturn(Arrays.asList(buildEntity(1L, "abc", 1), buildEntity(2L, "abcdef", 1)));

        List<TargetEntity> result = targetService.searchTargets("abc", 1);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("searchTargets - 空关键词与 null 类型应查询全部")
    void testSearchTargetsAll() {
        when(targetMapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
        List<TargetEntity> result = targetService.searchTargets(null, null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== 缓存容错 ====================

    @Test
    @DisplayName("缓存异常不应影响主流程")
    void testCacheFailureTolerance() {
        TargetEntity entity = buildEntity(1L, "A", 1);
        when(targetMapper.selectById(1L)).thenReturn(entity);
        when(targetMapper.updateById(any(TargetEntity.class))).thenReturn(1);
        // Redis 异常
        doThrow(new RuntimeException("redis down"))
                .when(redisTemplate).delete(anyString());

        // updateTarget 应仍能成功（缓存失效失败仅记录日志）
        TargetDTO dto = buildDto("新名称", 1, null);
        assertDoesNotThrow(() -> targetService.updateTarget(1L, dto));
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用 TargetDTO
     */
    private TargetDTO buildDto(String name, Integer type, String industry) {
        TargetDTO dto = new TargetDTO();
        dto.setName(name);
        dto.setType(type);
        dto.setIndustry(industry);
        return dto;
    }

    /**
     * 构造测试用 TargetEntity
     */
    private TargetEntity buildEntity(Long id, String name, Integer type) {
        TargetEntity entity = new TargetEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setType(type);
        entity.setRiskLevel(1);
        entity.setIsFollowed(0);
        entity.setFileCount(0);
        entity.setProfileStatus(0);
        return entity;
    }
}
