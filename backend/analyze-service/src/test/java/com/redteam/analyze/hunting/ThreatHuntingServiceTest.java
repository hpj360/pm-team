package com.redteam.analyze.hunting;

import com.redteam.analyze.hunting.entity.HuntingHypothesisEntity;
import com.redteam.analyze.hunting.entity.HuntingHypothesisEntity.HuntingHit;
import com.redteam.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ThreatHuntingService 单元测试
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ThreatHuntingServiceTest {

    @Mock
    private AttackMatrixService attackMatrixService;

    @InjectMocks
    private ThreatHuntingService service;

    /**
     * 构造测试用 ATT&CK 技术
     */
    private com.redteam.analyze.hunting.entity.AttackTechniqueEntity tech(String id, String name, String tactic) {
        com.redteam.analyze.hunting.entity.AttackTechniqueEntity t =
                new com.redteam.analyze.hunting.entity.AttackTechniqueEntity();
        t.setTechniqueId(id);
        t.setName(name);
        t.setTactic(tactic);
        t.setTacticName(tactic);
        t.setDataSource("test");
        return t;
    }

    @BeforeEach
    void setUp() {
        // 默认 mock：常见技术返回非空
        when(attackMatrixService.getTechnique("T1059.001"))
                .thenReturn(tech("T1059.001", "PowerShell", "execution"));
        when(attackMatrixService.getTechnique("T1003.001"))
                .thenReturn(tech("T1003.001", "LSASS Memory", "credential-access"));
        when(attackMatrixService.getTechnique("T1071.001"))
                .thenReturn(tech("T1071.001", "Web Protocols", "command-and-control"));
        when(attackMatrixService.getTechnique("T1490"))
                .thenReturn(tech("T1490", "Inhibit System Recovery", "impact"));
        when(attackMatrixService.getTechnique("T9999"))
                .thenReturn(null);
    }

    // ==================== createHypothesis ====================

    @Test
    @DisplayName("createHypothesis: 描述为空抛异常")
    void create_nullDescription_throws() {
        assertThrows(BusinessException.class,
                () -> service.createHypothesis(null, "T1059.001", 1L));
        assertThrows(BusinessException.class,
                () -> service.createHypothesis("", "T1059.001", 1L));
    }

    @Test
    @DisplayName("createHypothesis: techniqueId 为空抛异常")
    void create_nullTechniqueId_throws() {
        assertThrows(BusinessException.class,
                () -> service.createHypothesis("假设描述", null, 1L));
        assertThrows(BusinessException.class,
                () -> service.createHypothesis("假设描述", "", 1L));
    }

    @Test
    @DisplayName("createHypothesis: 技术不存在抛异常")
    void create_techniqueNotFound_throws() {
        assertThrows(BusinessException.class,
                () -> service.createHypothesis("假设描述", "T9999", 1L));
    }

    @Test
    @DisplayName("createHypothesis: 正常创建返回 ID")
    void create_normal_returnsId() {
        String id = service.createHypothesis("检测 PowerShell 恶意执行", "T1059.001", 100L);
        assertNotNull(id);
        assertTrue(id.startsWith("hyp-"));
        HypothesisVO vo = service.getHypothesis(id);
        assertEquals("检测 PowerShell 恶意执行", vo.getDescription());
        assertEquals("T1059.001", vo.getTechniqueId());
        assertEquals("PowerShell", vo.getTechniqueName());
        assertEquals("execution", vo.getTactic());
        assertEquals(HuntingHypothesisEntity.STATUS_DRAFT, vo.getStatus());
    }

    // ==================== validateHypothesis ====================

    @Test
    @DisplayName("validateHypothesis: 命中种子数据标记 CONFIRMED")
    void validate_hit_confirmed() {
        // T1059.001 对应种子 f-001（powershell -enc）
        String id = service.createHypothesis("检测 PowerShell 恶意执行", "T1059.001", 1L);
        HuntingHypothesisEntity entity = service.validateHypothesis(id);
        assertEquals(HuntingHypothesisEntity.STATUS_CONFIRMED, entity.getStatus());
        assertFalse(entity.getHits().isEmpty());
        assertTrue(entity.getConfidence() > 0.0);
        // 命中应包含 f-001
        assertTrue(entity.getHits().stream().anyMatch(h -> "f-001".equals(h.getEntityId())));
        assertNotNull(entity.getValidatedTime());
    }

    @Test
    @DisplayName("validateHypothesis: 父子技术双向匹配")
    void validate_parentChildMatch() {
        // T1071.001（子技术）应同时匹配 T1071 父技术种子 e-001
        when(attackMatrixService.getTechnique("T1071"))
                .thenReturn(tech("T1071", "Application Layer Protocol", "command-and-control"));
        String id = service.createHypothesis("检测应用层协议 C2", "T1071", 1L);
        HuntingHypothesisEntity entity = service.validateHypothesis(id);
        // 应同时命中 n-001（T1071.001）和 e-001（T1071）
        assertTrue(entity.getHits().size() >= 2);
        assertTrue(entity.getHits().stream().anyMatch(h -> "n-001".equals(h.getEntityId())));
        assertTrue(entity.getHits().stream().anyMatch(h -> "e-001".equals(h.getEntityId())));
    }

    @Test
    @DisplayName("validateHypothesis: 无命中标记 REFUTED")
    void validate_noHit_refuted() {
        // T1490 有种子 f-003（shadowcopy delete vssadmin）
        // 用 T1003.001 测试：种子中有 f-004（mimikatz.exe sekurlsa）会命中
        // 改用一个无种子的技术：mock 一个新技术
        when(attackMatrixService.getTechnique("T8888"))
                .thenReturn(tech("T8888", "Non Existent", "unknown"));
        String id = service.createHypothesis("无命中假设", "T8888", 1L);
        HuntingHypothesisEntity entity = service.validateHypothesis(id);
        assertEquals(HuntingHypothesisEntity.STATUS_REFUTED, entity.getStatus());
        assertTrue(entity.getHits().isEmpty());
        assertEquals(0.0, entity.getConfidence());
    }

    @Test
    @DisplayName("validateHypothesis: 推荐 IOC 提取")
    void validate_recommendedIocsExtracted() {
        // T1071.001 命中 n-001（POST /gate.php to 45.155.205.233）
        String id = service.createHypothesis("检测 Web 协议 C2", "T1071.001", 1L);
        HuntingHypothesisEntity entity = service.validateHypothesis(id);
        assertFalse(entity.getRecommendedIocs().isEmpty());
        assertTrue(entity.getRecommendedIocs().stream().anyMatch(i -> i.contains("45.155.205.233")));
    }

    @Test
    @DisplayName("validateHypothesis: 不存在的假设抛异常")
    void validate_notFound_throws() {
        assertThrows(BusinessException.class, () -> service.validateHypothesis("non-existent"));
        assertThrows(BusinessException.class, () -> service.validateHypothesis(null));
    }

    // ==================== getHypothesis ====================

    @Test
    @DisplayName("getHypothesis: 返回含 ATT&CK 元数据的 VO")
    void getHypothesis_returnsVoWithMetadata() {
        String id = service.createHypothesis("LSASS 凭证转储", "T1003.001", 200L);
        HypothesisVO vo = service.getHypothesis(id);
        assertEquals("T1003.001", vo.getTechniqueId());
        assertEquals("LSASS Memory", vo.getTechniqueName());
        assertEquals("credential-access", vo.getTactic());
    }

    @Test
    @DisplayName("getHypothesis: 不存在抛异常")
    void getHypothesis_notFound_throws() {
        assertThrows(BusinessException.class, () -> service.getHypothesis("non-existent"));
    }

    // ==================== listHypotheses ====================

    @Test
    @DisplayName("listHypotheses: 返回全部假设（按创建时间倒序）")
    void listHypotheses_returnsAll() {
        service.createHypothesis("假设1", "T1059.001", 1L);
        service.createHypothesis("假设2", "T1003.001", 1L);
        List<HypothesisVO> list = service.listHypotheses();
        assertEquals(2, list.size());
    }

    // ==================== 置信度计算 ====================

    @Test
    @DisplayName("validateHypothesis: 置信度在 0-1 之间")
    void validate_confidenceInRange() {
        String id = service.createHypothesis("检测 PowerShell 恶意执行", "T1059.001", 1L);
        HuntingHypothesisEntity entity = service.validateHypothesis(id);
        assertTrue(entity.getConfidence() >= 0.0 && entity.getConfidence() <= 1.0);
    }

    @Test
    @DisplayName("validateHypothesis: 命中评分区分完全匹配与父子匹配")
    void validate_hitScoreDistinction() {
        // T1059.001 完全匹配 f-001（score=1.0），父技术 T1059 也会匹配但 score=0.7
        when(attackMatrixService.getTechnique("T1059"))
                .thenReturn(tech("T1059", "Command and Scripting Interpreter", "execution"));
        String id = service.createHypothesis("检测命令执行", "T1059", 1L);
        HuntingHypothesisEntity entity = service.validateHypothesis(id);
        // f-001 是 T1059.001，父技术匹配，score=0.7
        HuntingHit f001Hit = entity.getHits().stream()
                .filter(h -> "f-001".equals(h.getEntityId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0.7, f001Hit.getScore());
    }

    // ==================== 内部辅助方法 ====================

    @Test
    @DisplayName("findSeedIndices: 正确返回命中索引")
    void findSeedIndices_returnsMatches() {
        List<Integer> indices = service.findSeedIndices("T1059.001");
        assertFalse(indices.isEmpty());
        // 索引应在合法范围
        for (Integer idx : indices) {
            assertTrue(idx >= 0 && idx < service.seedDataCount());
        }
    }

    @Test
    @DisplayName("getHuntingData: 返回不可变列表")
    void getHuntingData_unmodifiable() {
        List<?> data = service.getHuntingData();
        assertEquals(service.seedDataCount(), data.size());
        // 不可变
        assertThrows(UnsupportedOperationException.class, () -> data.clear());
    }
}
