package com.redteam.profile.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redteam.common.result.ResultCode;
import com.redteam.profile.dto.TargetDTO;
import com.redteam.profile.dto.TargetProfileDTO;
import com.redteam.profile.dto.TargetQueryDTO;
import com.redteam.profile.dto.TargetRelationDTO;
import com.redteam.profile.dto.TargetRelationRequestDTO;
import com.redteam.profile.entity.TargetEntity;
import com.redteam.profile.entity.TargetRelationEntity;
import com.redteam.profile.mapper.TargetMapper;
import com.redteam.profile.mapper.TargetRelationMapper;
import com.redteam.profile.service.TargetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 目标画像服务集成测试
 *
 * <p>验证 TargetController → TargetService → TargetMapper 端到端请求链路，
 * 使用 @MockBean 隔离 Mapper 与 Redis，保留 Spring 容器装配、参数校验、JSON 序列化等真实行为。</p>
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>目标 CRUD 接口：创建、查询、更新、删除</li>
 *   <li>分页查询与关键词搜索</li>
 *   <li>目标画像聚合接口</li>
 *   <li>关系图谱管理：创建关系、查询关系</li>
 *   <li>异常路径：参数校验失败、目标不存在</li>
 * </ul>
 *
 * @author 红方团队
 */
@SpringJUnitConfig
@Import(ProfileIntegrationTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.redis.host=localhost",
        "spring.redis.port=6379"
})
@DisplayName("目标画像服务集成测试")
class ProfileIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TargetService targetService(org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
                                           com.redteam.profile.mapper.TargetRelationMapper targetRelationMapper) {
            return new com.redteam.profile.service.impl.TargetServiceImpl(redisTemplate, targetRelationMapper);
        }

        @Bean
        public com.redteam.profile.controller.TargetController targetController(TargetService targetService) {
            return new com.redteam.profile.controller.TargetController(targetService);
        }

        @Bean
        public com.redteam.common.exception.GlobalExceptionHandler globalExceptionHandler() {
            return new com.redteam.common.exception.GlobalExceptionHandler();
        }

        @Bean
        public org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate() {
            return org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        }
    }

    @MockBean
    private TargetMapper targetMapper;

    @MockBean
    private TargetRelationMapper targetRelationMapper;

    @MockBean
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    @Autowired
    private TargetService targetService;

    @Autowired
    private com.redteam.profile.controller.TargetController targetController;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        // 使用 standaloneSetup 避免 @SpringJUnitConfig 不创建 WebApplicationContext 的问题
        mockMvc = MockMvcBuilders.standaloneSetup(targetController)
                .setControllerAdvice(new com.redteam.common.exception.GlobalExceptionHandler())
                .build();
        // 注入 baseMapper（ServiceImpl 父类字段）
        try {
            java.lang.reflect.Field baseMapperField =
                    com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
            baseMapperField.setAccessible(true);
            baseMapperField.set(targetService, targetMapper);
        } catch (Exception ignored) {
            // 忽略
        }
        // 注入 redisTemplate
        try {
            java.lang.reflect.Field redisField =
                    com.redteam.profile.service.impl.TargetServiceImpl.class.getDeclaredField("redisTemplate");
            redisField.setAccessible(true);
            redisField.set(targetService, redisTemplate);
        } catch (Exception ignored) {
            // 忽略 - 字段可能不存在
        }
    }

    // ===================== POST /api/v1/targets =====================

    @Test
    @DisplayName("集成 - 创建目标应返回完整实体")
    void testCreateTargetFlow() throws Exception {
        TargetDTO dto = new TargetDTO();
        dto.setName("测试目标A");
        dto.setType(2);
        dto.setIndustry("互联网");
        dto.setAttackSurface("Web,API");

        TargetEntity entity = buildEntity(1L, "测试目标A");
        when(targetMapper.insert(any(TargetEntity.class))).thenReturn(1);
        when(targetMapper.selectById(any())).thenReturn(entity);

        mockMvc.perform(post("/api/v1/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("集成 - 创建目标缺少必填字段应返回 400")
    void testCreateTargetValidation() throws Exception {
        TargetDTO dto = new TargetDTO();
        // 缺少 name 字段

        mockMvc.perform(post("/api/v1/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ===================== GET /api/v1/targets/{id} =====================

    @Test
    @DisplayName("集成 - 获取目标详情应返回完整实体")
    void testGetTargetFlow() throws Exception {
        TargetEntity entity = buildEntity(1L, "测试目标A");
        when(targetMapper.selectById(1L)).thenReturn(entity);

        mockMvc.perform(get("/api/v1/targets/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试目标A"));
    }

    @Test
    @DisplayName("集成 - 获取不存在目标应返回业务错误码")
    void testGetTargetNotFound() throws Exception {
        when(targetMapper.selectById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/targets/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.TARGET_NOT_FOUND.getCode()));
    }

    // ===================== PUT /api/v1/targets/{id} =====================

    @Test
    @DisplayName("集成 - 更新目标应返回更新后的实体")
    void testUpdateTargetFlow() throws Exception {
        TargetDTO dto = new TargetDTO();
        dto.setName("更新后的目标");
        dto.setType(2);
        dto.setIndustry("金融");

        TargetEntity existing = buildEntity(1L, "旧名称");
        when(targetMapper.selectById(1L)).thenReturn(existing);
        when(targetMapper.updateById(any(TargetEntity.class))).thenReturn(1);

        mockMvc.perform(put("/api/v1/targets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("更新后的目标"));
    }

    // ===================== DELETE /api/v1/targets/{id} =====================

    @Test
    @DisplayName("集成 - 删除目标应返回成功")
    void testDeleteTargetFlow() throws Exception {
        TargetEntity entity = buildEntity(1L, "测试目标A");
        when(targetMapper.selectById(1L)).thenReturn(entity);
        when(targetMapper.deleteById(1L)).thenReturn(1);
        when(targetRelationMapper.delete(any())).thenReturn(0);

        mockMvc.perform(delete("/api/v1/targets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== GET /api/v1/targets =====================

    @Test
    @DisplayName("集成 - 分页查询目标应返回分页结构")
    void testListTargetsFlow() throws Exception {
        TargetEntity e1 = buildEntity(1L, "目标A");
        TargetEntity e2 = buildEntity(2L, "目标B");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<TargetEntity> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1L, 10L, 2L);
        page.setRecords(Arrays.asList(e1, e2));

        when(targetMapper.selectPage(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/targets")
                        .param("current", "1")
                        .param("size", "10")
                        .param("keyword", "目标"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2));
    }

    // ===================== GET /api/v1/targets/{id}/profile =====================

    @Test
    @DisplayName("集成 - 获取目标画像应返回聚合 DTO")
    void testGetTargetProfileFlow() throws Exception {
        TargetEntity entity = buildEntity(1L, "测试目标A");
        entity.setIndustry("互联网");
        when(targetMapper.selectById(1L)).thenReturn(entity);
        when(targetRelationMapper.selectList(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/targets/1/profile"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== POST /api/v1/targets/relations =====================

    @Test
    @DisplayName("集成 - 创建目标关系应返回成功")
    void testCreateRelationFlow() throws Exception {
        TargetRelationRequestDTO dto = new TargetRelationRequestDTO();
        dto.setSourceId(1L);
        dto.setTargetId(2L);
        dto.setRelationType("SUBSIDIARY");

        when(targetMapper.selectById(1L)).thenReturn(buildEntity(1L, "源目标"));
        when(targetMapper.selectById(2L)).thenReturn(buildEntity(2L, "目标"));
        when(targetRelationMapper.selectCount(any())).thenReturn(0L);
        when(targetRelationMapper.insert(any(TargetRelationEntity.class))).thenReturn(1);

        mockMvc.perform(post("/api/v1/targets/relations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== GET /api/v1/targets/{id}/relation-graph =====================

    @Test
    @DisplayName("集成 - 查询目标关系图谱应返回关系 DTO")
    void testGetRelationsFlow() throws Exception {
        TargetEntity root = buildEntity(1L, "根目标");
        when(targetMapper.selectById(1L)).thenReturn(root);
        when(targetRelationMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(targetMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(root));

        mockMvc.perform(get("/api/v1/targets/1/relation-graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    // ===================== 辅助方法 =====================

    private TargetEntity buildEntity(Long id, String name) {
        TargetEntity entity = new TargetEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        return entity;
    }
}
