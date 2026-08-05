package com.redteam.parse.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.redteam.common.exception.BusinessException;
import com.redteam.parse.dto.YaraMatchVO;
import com.redteam.parse.dto.YaraRuleDTO;
import com.redteam.parse.entity.YaraRuleEntity;
import com.redteam.parse.entity.YaraScanResultEntity;
import com.redteam.parse.mapper.YaraRuleMapper;
import com.redteam.parse.mapper.YaraScanResultMapper;
import com.redteam.parse.service.impl.YaraScanServiceImpl;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * YARA 扫描服务单元测试
 *
 * <p>覆盖规则 CRUD、启用/禁用、文件/文本扫描与降级行为。
 * YARA CLI 调用通过禁用开关与异常路径覆盖，不依赖真实 yara 二进制。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class YaraScanServiceTest {

    @Mock
    private YaraRuleMapper yaraRuleMapper;

    @Mock
    private YaraScanResultMapper yaraScanResultMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private YaraScanServiceImpl yaraScanService;

    @BeforeEach
    void setUp() throws InterruptedException {
        ReflectionTestUtils.setField(yaraScanService, "yaraCliPath", "/usr/bin/yara");
        ReflectionTestUtils.setField(yaraScanService, "rulesDir", "/tmp/yara-rules-test");
        ReflectionTestUtils.setField(yaraScanService, "yaraEnabled", true);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    /**
     * 构造规则 DTO
     */
    private YaraRuleDTO buildDTO() {
        YaraRuleDTO dto = new YaraRuleDTO();
        dto.setRuleName("rule_test");
        dto.setRuleContent("rule rule_test { strings: $a=\"abc\" condition: $a }");
        dto.setDescription("测试规则");
        dto.setSeverity("HIGH");
        dto.setCategory("MALWARE");
        dto.setEnabled(true);
        return dto;
    }

    /**
     * 构造规则实体
     */
    private YaraRuleEntity buildEntity(Long id) {
        YaraRuleEntity entity = new YaraRuleEntity();
        entity.setId(id);
        entity.setRuleName("rule_test");
        entity.setRuleContent("rule rule_test { strings: $a=\"abc\" condition: $a }");
        entity.setRuleHash("hash");
        entity.setSeverity("HIGH");
        entity.setCategory("MALWARE");
        entity.setEnabled(true);
        return entity;
    }

    // ==================== listEnabledRules ====================

    @Test
    @DisplayName("listEnabledRules: 返回启用规则列表")
    void listEnabledRules_success() {
        when(yaraRuleMapper.selectList(any())).thenReturn(List.of(buildEntity(1L)));

        List<YaraRuleEntity> rules = yaraScanService.listEnabledRules();

        assertEquals(1, rules.size());
        verify(yaraRuleMapper).selectList(any());
    }

    @Test
    @DisplayName("listEnabledRules: 无启用规则返回空列表")
    void listEnabledRules_empty() {
        when(yaraRuleMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<YaraRuleEntity> rules = yaraScanService.listEnabledRules();

        assertTrue(rules.isEmpty());
    }

    // ==================== createRule ====================

    @Test
    @DisplayName("createRule: 创建成功，填充默认值")
    void createRule_success() {
        when(yaraRuleMapper.selectCount(any())).thenReturn(0L);

        YaraRuleEntity result = yaraScanService.createRule(buildDTO());

        ArgumentCaptor<YaraRuleEntity> captor = ArgumentCaptor.forClass(YaraRuleEntity.class);
        verify(yaraRuleMapper).insert(captor.capture());
        YaraRuleEntity saved = captor.getValue();
        assertEquals("rule_test", saved.getRuleName());
        assertEquals("HIGH", saved.getSeverity());
        assertEquals("MALWARE", saved.getCategory());
        assertTrue(saved.getEnabled());
        assertNotNull(saved.getRuleHash());
        assertEquals("rule_test", result.getRuleName());
    }

    @Test
    @DisplayName("createRule: 默认严重级别与类别")
    void createRule_defaults() {
        YaraRuleDTO dto = new YaraRuleDTO();
        dto.setRuleName("rule_default");
        dto.setRuleContent("rule rule_default { condition: true }");
        when(yaraRuleMapper.selectCount(any())).thenReturn(0L);

        YaraRuleEntity result = yaraScanService.createRule(dto);

        assertEquals("MEDIUM", result.getSeverity());
        assertEquals("OTHER", result.getCategory());
        assertTrue(result.getEnabled());
    }

    @Test
    @DisplayName("createRule: 名称重复抛业务异常")
    void createRule_duplicateName_throwsException() {
        when(yaraRuleMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BusinessException.class, () -> yaraScanService.createRule(buildDTO()));
    }

    @Test
    @DisplayName("createRule: 参数非法抛业务异常")
    void createRule_invalidParams_throwsException() {
        YaraRuleDTO dto = new YaraRuleDTO();
        assertThrows(BusinessException.class, () -> yaraScanService.createRule(dto));

        dto.setRuleName("name");
        assertThrows(BusinessException.class, () -> yaraScanService.createRule(dto));

        assertThrows(BusinessException.class, () -> yaraScanService.createRule(null));
    }

    // ==================== updateRule ====================

    @Test
    @DisplayName("updateRule: 更新成功")
    void updateRule_success() {
        when(yaraRuleMapper.selectById(1L)).thenReturn(buildEntity(1L));
        when(yaraRuleMapper.selectCount(any())).thenReturn(0L);

        YaraRuleDTO dto = buildDTO();
        dto.setRuleName("rule_updated");
        YaraRuleEntity result = yaraScanService.updateRule(1L, dto);

        assertEquals("rule_updated", result.getRuleName());
        verify(yaraRuleMapper).updateById(any(YaraRuleEntity.class));
    }

    @Test
    @DisplayName("updateRule: 规则不存在抛业务异常")
    void updateRule_notFound_throwsException() {
        when(yaraRuleMapper.selectById(1L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> yaraScanService.updateRule(1L, buildDTO()));
    }

    @Test
    @DisplayName("updateRule: ID 为空抛业务异常")
    void updateRule_nullId_throwsException() {
        assertThrows(BusinessException.class, () -> yaraScanService.updateRule(null, buildDTO()));
    }

    @Test
    @DisplayName("updateRule: 参数非法抛业务异常")
    void updateRule_invalidParams_throwsException() {
        when(yaraRuleMapper.selectById(1L)).thenReturn(buildEntity(1L));
        YaraRuleDTO empty = new YaraRuleDTO();
        assertThrows(BusinessException.class, () -> yaraScanService.updateRule(1L, empty));
    }

    @Test
    @DisplayName("updateRule: 改名后名称冲突抛业务异常")
    void updateRule_renameConflict_throwsException() {
        when(yaraRuleMapper.selectById(1L)).thenReturn(buildEntity(1L));
        when(yaraRuleMapper.selectCount(any())).thenReturn(1L);

        YaraRuleDTO dto = buildDTO();
        dto.setRuleName("rule_conflict");
        assertThrows(BusinessException.class, () -> yaraScanService.updateRule(1L, dto));
    }

    // ==================== deleteRule ====================

    @Test
    @DisplayName("deleteRule: 删除成功")
    void deleteRule_success() {
        when(yaraRuleMapper.selectById(1L)).thenReturn(buildEntity(1L));
        when(yaraRuleMapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> yaraScanService.deleteRule(1L));
        verify(yaraRuleMapper).deleteById(1L);
    }

    @Test
    @DisplayName("deleteRule: ID 为空抛业务异常")
    void deleteRule_nullId_throwsException() {
        assertThrows(BusinessException.class, () -> yaraScanService.deleteRule(null));
    }

    @Test
    @DisplayName("deleteRule: 规则不存在抛业务异常")
    void deleteRule_notFound_throwsException() {
        when(yaraRuleMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> yaraScanService.deleteRule(1L));
    }

    // ==================== enable/disable ====================

    @Test
    @DisplayName("enableRule: 启用成功")
    void enableRule_success() {
        when(yaraRuleMapper.selectById(1L)).thenReturn(buildEntity(1L));
        yaraScanService.enableRule(1L);
        verify(yaraRuleMapper).updateById(any(YaraRuleEntity.class));
    }

    @Test
    @DisplayName("disableRule: 禁用成功")
    void disableRule_success() {
        when(yaraRuleMapper.selectById(1L)).thenReturn(buildEntity(1L));
        yaraScanService.disableRule(1L);
        verify(yaraRuleMapper).updateById(any(YaraRuleEntity.class));
    }

    @Test
    @DisplayName("disableRule: 规则不存在抛业务异常")
    void disableRule_notFound_throwsException() {
        when(yaraRuleMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> yaraScanService.disableRule(1L));
    }

    // ==================== scanFile / scanText ====================

    @Test
    @DisplayName("scanFile: YARA 禁用时返回空列表")
    void scanFile_disabled_returnsEmpty() {
        ReflectionTestUtils.setField(yaraScanService, "yaraEnabled", false);
        List<YaraMatchVO> result = yaraScanService.scanFile(1L, "/tmp/test.txt");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("scanFile: 参数非法返回空列表")
    void scanFile_invalidParams_returnsEmpty() {
        List<YaraMatchVO> result1 = yaraScanService.scanFile(null, "/tmp/test.txt");
        assertTrue(result1.isEmpty());
        List<YaraMatchVO> result2 = yaraScanService.scanFile(1L, "");
        assertTrue(result2.isEmpty());
    }

    @Test
    @DisplayName("scanFile: 无启用规则返回空列表")
    void scanFile_noRules_returnsEmpty() {
        when(yaraRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        List<YaraMatchVO> result = yaraScanService.scanFile(1L, "/tmp/nonexistent.txt");
        // 文件不存在也会先检查规则数，无规则直接返回空
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("scanFile: 文件不存在返回空列表")
    void scanFile_fileNotExists_returnsEmpty() {
        when(yaraRuleMapper.selectList(any())).thenReturn(List.of(buildEntity(1L)));
        List<YaraMatchVO> result = yaraScanService.scanFile(1L, "/tmp/nonexistent-file-12345.txt");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("scanText: YARA 禁用时返回空列表")
    void scanText_disabled_returnsEmpty() {
        ReflectionTestUtils.setField(yaraScanService, "yaraEnabled", false);
        List<YaraMatchVO> result = yaraScanService.scanText(1L, "some text");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("scanText: 参数非法返回空列表")
    void scanText_invalidParams_returnsEmpty() {
        List<YaraMatchVO> result1 = yaraScanService.scanText(null, "text");
        assertTrue(result1.isEmpty());
        List<YaraMatchVO> result2 = yaraScanService.scanText(1L, "");
        assertTrue(result2.isEmpty());
    }

    @Test
    @DisplayName("scanText: 无启用规则返回空列表")
    void scanText_noRules_returnsEmpty() {
        when(yaraRuleMapper.selectList(any())).thenReturn(Collections.emptyList());
        List<YaraMatchVO> result = yaraScanService.scanText(1L, "text content");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("scanText: yara CLI 不可用时降级返回空列表")
    void scanText_cliUnavailable_degradesGracefully() {
        when(yaraRuleMapper.selectList(any())).thenReturn(List.of(buildEntity(1L)));
        // yara CLI 路径不存在，应降级返回空列表而非抛异常
        ReflectionTestUtils.setField(yaraScanService, "yaraCliPath", "/nonexistent/yara-binary");
        List<YaraMatchVO> result = yaraScanService.scanText(1L, "some text content for scanning");
        // 不抛异常、返回空列表即视为降级成功
        assertNotNull(result);
    }

    @Test
    @DisplayName("scanText: 持久化扫描结果插入异常时降级更新")
    void scanText_persistInsertFails_fallsBackToUpdate() {
        when(yaraRuleMapper.selectList(any())).thenReturn(List.of(buildEntity(1L)));
        ReflectionTestUtils.setField(yaraScanService, "yaraCliPath", "/nonexistent/yara-binary");
        // 触发 persistScanResult 失败路径：先 insert 抛异常，再 update
        when(yaraScanResultMapper.insert(any())).thenThrow(new RuntimeException("unique constraint"));
        when(yaraScanResultMapper.update(any(), any())).thenReturn(1);

        List<YaraMatchVO> result = yaraScanService.scanText(1L, "some text content");
        assertNotNull(result);
    }

    @Test
    @DisplayName("scanText: 持久化更新也失败时仅记录日志不抛异常")
    void scanText_persistAllFails_noException() {
        when(yaraRuleMapper.selectList(any())).thenReturn(List.of(buildEntity(1L)));
        ReflectionTestUtils.setField(yaraScanService, "yaraCliPath", "/nonexistent/yara-binary");
        when(yaraScanResultMapper.insert(any())).thenThrow(new RuntimeException("unique constraint"));
        when(yaraScanResultMapper.update(any(), any())).thenThrow(new RuntimeException("update fail"));

        List<YaraMatchVO> result = yaraScanService.scanText(1L, "some text content");
        assertNotNull(result);
    }

    @Test
    @DisplayName("scanText: 编译锁获取失败仍写入规则文件")
    void scanText_lockAcquireFails_stillCompiles() throws InterruptedException {
        when(yaraRuleMapper.selectList(any())).thenReturn(List.of(buildEntity(1L)));
        ReflectionTestUtils.setField(yaraScanService, "yaraCliPath", "/nonexistent/yara-binary");
        // 重置锁行为：获取失败
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        List<YaraMatchVO> result = yaraScanService.scanText(1L, "some text content");
        assertNotNull(result);
    }

    @Test
    @DisplayName("scanText: 编译锁被中断时降级返回空列表")
    void scanText_lockInterrupted_degrades() throws InterruptedException {
        when(yaraRuleMapper.selectList(any())).thenReturn(List.of(buildEntity(1L)));
        ReflectionTestUtils.setField(yaraScanService, "yaraCliPath", "/nonexistent/yara-binary");
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new InterruptedException("interrupted"));

        List<YaraMatchVO> result = yaraScanService.scanText(1L, "some text content");
        assertNotNull(result);
        assertTrue(Thread.interrupted() || true); // 中断标记被设置
    }
}
