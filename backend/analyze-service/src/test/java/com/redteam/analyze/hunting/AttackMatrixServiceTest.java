package com.redteam.analyze.hunting;

import com.redteam.analyze.hunting.entity.AttackTechniqueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AttackMatrixService 单元测试
 *
 * @author 红方团队
 */
class AttackMatrixServiceTest {

    private AttackMatrixService service;

    @BeforeEach
    void setUp() {
        service = new AttackMatrixService();
        service.loadDefaultMatrix();
    }

    // ==================== 战术 ====================

    @Test
    @DisplayName("tacticCount: 14 战术")
    void tacticCount_isFourteen() {
        assertEquals(14, service.tacticCount());
    }

    @Test
    @DisplayName("getAllTactics: 返回 14 战术含中文名")
    void getAllTactics_containsZhName() {
        List<Map<String, String>> tactics = service.getAllTactics();
        assertEquals(14, tactics.size());
        // 验证 execution 战术的中文名
        Map<String, String> execution = tactics.stream()
                .filter(t -> "execution".equals(t.get("id")))
                .findFirst()
                .orElseThrow();
        assertEquals("执行", execution.get("nameZh"));
    }

    // ==================== 技术 ====================

    @Test
    @DisplayName("techniqueCount: 内置技术数量大于 0")
    void techniqueCount_positive() {
        assertTrue(service.techniqueCount() > 0);
    }

    @Test
    @DisplayName("getAllTechniques: 返回全部技术")
    void getAllTechniques_returnsAll() {
        List<AttackTechniqueEntity> techniques = service.getAllTechniques();
        assertEquals(service.techniqueCount(), techniques.size());
    }

    @Test
    @DisplayName("getTechnique: T1059.001 返回 PowerShell 子技术")
    void getTechnique_subTechnique() {
        AttackTechniqueEntity tech = service.getTechnique("T1059.001");
        assertNotNull(tech);
        assertEquals("PowerShell", tech.getName());
        assertEquals("execution", tech.getTactic());
        assertEquals("执行", tech.getTacticName());
        assertTrue(tech.isSubTechnique());
    }

    @Test
    @DisplayName("getTechnique: 不存在返回 null")
    void getTechnique_notFound_returnsNull() {
        assertNull(service.getTechnique("T9999"));
        assertNull(service.getTechnique(null));
        assertNull(service.getTechnique(""));
    }

    // ==================== 按战术查询 ====================

    @Test
    @DisplayName("getTechniquesByTactic: execution 战术含 T1059 与子技术")
    void getTechniquesByTactic_execution() {
        List<AttackTechniqueEntity> techniques = service.getTechniquesByTactic("execution");
        assertFalse(techniques.isEmpty());
        assertTrue(techniques.stream().anyMatch(t -> "T1059".equals(t.getTechniqueId())));
        assertTrue(techniques.stream().anyMatch(t -> "T1059.001".equals(t.getTechniqueId())));
    }

    @Test
    @DisplayName("getTechniquesByTactic: 大小写不敏感")
    void getTechniquesByTactic_caseInsensitive() {
        List<AttackTechniqueEntity> upper = service.getTechniquesByTactic("EXECUTION");
        List<AttackTechniqueEntity> lower = service.getTechniquesByTactic("execution");
        assertEquals(upper.size(), lower.size());
    }

    @Test
    @DisplayName("getTechniquesByTactic: 空参数返回空列表")
    void getTechniquesByTactic_emptyParam_returnsEmpty() {
        assertTrue(service.getTechniquesByTactic(null).isEmpty());
        assertTrue(service.getTechniquesByTactic("").isEmpty());
    }

    // ==================== 搜索 ====================

    @Test
    @DisplayName("searchTechniques: 关键词 powershell 命中 T1059.001")
    void searchTechniques_keywordPowershell() {
        List<AttackTechniqueEntity> results = service.searchTechniques("powershell");
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(t -> "T1059.001".equals(t.getTechniqueId())));
    }

    @Test
    @DisplayName("searchTechniques: 技术 ID 检索 T1486")
    void searchTechniques_byTechniqueId() {
        List<AttackTechniqueEntity> results = service.searchTechniques("T1486");
        assertFalse(results.isEmpty());
        assertEquals("T1486", results.get(0).getTechniqueId());
    }

    @Test
    @DisplayName("searchTechniques: 中文关键词检索（如 持久化）")
    void searchTechniques_chineseKeyword() {
        List<AttackTechniqueEntity> results = service.searchTechniques("持久化");
        assertFalse(results.isEmpty());
        assertTrue(results.stream().allMatch(t -> "持久化".equals(t.getTacticName())));
    }

    @Test
    @DisplayName("searchTechniques: 空关键词返回空列表")
    void searchTechniques_emptyKeyword_returnsEmpty() {
        assertTrue(service.searchTechniques(null).isEmpty());
        assertTrue(service.searchTechniques("").isEmpty());
        assertTrue(service.searchTechniques("   ").isEmpty());
    }

    @Test
    @DisplayName("loadDefaultMatrix: 重复调用清空旧数据")
    void loadDefaultMatrix_idempotent() {
        int count1 = service.techniqueCount();
        service.loadDefaultMatrix();
        int count2 = service.techniqueCount();
        assertEquals(count1, count2);
    }
}
