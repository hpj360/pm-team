package com.redteam.parse.service;

import com.redteam.common.entity.FileTagEntity;
import com.redteam.common.entity.TagDictEntity;
import com.redteam.common.mapper.FileTagMapper;
import com.redteam.common.mapper.TagDictMapper;
import com.redteam.parse.dto.NerEntityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 自动标签识别引擎单元测试
 *
 * <p>覆盖 REGEX/DICT/ML/ASSOC 四类规则引擎及 recognizeTags 主流程。
 * 使用 Mockito mock TagDictMapper + FileTagMapper，不依赖真实数据库。</p>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagRecognitionEngineTest {

    @Mock
    private TagDictMapper tagDictMapper;

    @Mock
    private FileTagMapper fileTagMapper;

    @InjectMocks
    private TagRecognitionEngine engine;

    @BeforeEach
    void setUp() {
        // 默认桩：任意 tagCode 均命中字典；文件无已有标签
        when(tagDictMapper.selectByCode(anyString())).thenReturn(buildDictEntity());
        when(fileTagMapper.selectByFileId(anyLong())).thenReturn(Collections.emptyList());
    }

    // ==================== REGEX 规则引擎 ====================

    @Nested
    @DisplayName("REGEX 规则引擎")
    class RegexEngineTest {

        @Test
        @DisplayName("recognizeByRegex: 识别 CVE 编号")
        void testRecognizeByRegex_CVE() {
            List<String> codes = engine.recognizeByRegex("漏洞 CVE-2024-12345 影响", null, null);
            assertTrue(codes.contains("L3.ENTITY.VULN.CVE"));
        }

        @Test
        @DisplayName("recognizeByRegex: 识别公网 IP 地址")
        void testRecognizeByRegex_IP() {
            List<String> codes = engine.recognizeByRegex("公网节点 8.8.8.8 被攻击", null, null);
            assertTrue(codes.contains("L3.ENTITY.IP.PUBLIC"));
            assertFalse(codes.contains("L3.ENTITY.IP.PRIVATE"));
        }

        @Test
        @DisplayName("recognizeByRegex: 识别域名")
        void testRecognizeByRegex_Domain() {
            List<String> codes = engine.recognizeByRegex("访问 example.com 站点", null, null);
            assertTrue(codes.contains("L3.ENTITY.DOMAIN"));
        }

        @Test
        @DisplayName("recognizeByRegex: 识别邮箱")
        void testRecognizeByRegex_Email() {
            List<String> codes = engine.recognizeByRegex("联系 user@example.com 获取", null, null);
            assertTrue(codes.contains("L3.ENTITY.USER.EMAIL"));
        }

        @Test
        @DisplayName("recognizeByRegex: 空文本返回空列表")
        void testRecognizeByRegex_EmptyText() {
            assertTrue(engine.recognizeByRegex("", null, null).isEmpty());
            assertTrue(engine.recognizeByRegex(null, null, null).isEmpty());
        }
    }

    // ==================== DICT 规则引擎 ====================

    @Nested
    @DisplayName("DICT 规则引擎")
    class DictEngineTest {

        @Test
        @DisplayName("recognizeByDict: 识别 APT 组织")
        void testRecognizeByDict_APT() {
            List<String> codes = engine.recognizeByDict("APT29 组织发起攻击");
            assertTrue(codes.contains("L5.INTEL.APT.APT29"));
        }

        @Test
        @DisplayName("recognizeByDict: 无匹配返回空列表")
        void testRecognizeByDict_NoMatch() {
            assertTrue(engine.recognizeByDict("这是一段普通文本内容").isEmpty());
        }
    }

    // ==================== ML 规则引擎 ====================

    @Nested
    @DisplayName("ML 规则引擎")
    class MlEngineTest {

        @Test
        @DisplayName("recognizeByML: NER IP 实体映射为公网 IP 标签")
        void testRecognizeByML_IP() {
            List<NerEntityVO> entities = List.of(buildNerVO("8.8.8.8", "IP"));
            List<String> codes = engine.recognizeByML(entities);
            assertTrue(codes.contains("L3.ENTITY.IP.PUBLIC"));
        }

        @Test
        @DisplayName("recognizeByML: 私网 IP 判定")
        void testRecognizeByML_PrivateIP() {
            List<NerEntityVO> entities = List.of(buildNerVO("192.168.1.1", "IP"));
            List<String> codes = engine.recognizeByML(entities);
            assertTrue(codes.contains("L3.ENTITY.IP.PRIVATE"));
            assertFalse(codes.contains("L3.ENTITY.IP.PUBLIC"));
        }

        @Test
        @DisplayName("recognizeByML: 空实体列表返回空")
        void testRecognizeByML_EmptyEntities() {
            assertTrue(engine.recognizeByML(null).isEmpty());
            assertTrue(engine.recognizeByML(Collections.emptyList()).isEmpty());
        }
    }

    // ==================== ASSOC 规则引擎 ====================

    @Nested
    @DisplayName("ASSOC 规则引擎")
    class AssocEngineTest {

        @Test
        @DisplayName("recognizeByAssoc: 包含 IP 触发目标画像场景标签")
        void testRecognizeByAssoc_TargetProfile() {
            List<String> codes = engine.recognizeByAssoc(1L, "目标 IP 8.8.8.8 节点", null);
            assertTrue(codes.contains("L4.SCENE.TARGET_PROFILE"));
        }

        @Test
        @DisplayName("recognizeByAssoc: pcap 文件触发网络地形场景标签")
        void testRecognizeByAssoc_PcapFile() {
            List<String> codes = engine.recognizeByAssoc(1L, "网络抓包 pcap 文件分析", null);
            assertTrue(codes.contains("L4.SCENE.NETWORK_TOPOLOGY"));
        }
    }

    // ==================== recognizeTags 主流程 ====================

    @Nested
    @DisplayName("recognizeTags 主流程")
    class RecognizeTagsTest {

        @Test
        @DisplayName("recognizeTags: 四类规则综合识别")
        void testRecognizeTags_AllRules() {
            String text = "CVE-2024-12345 漏洞, IP 8.8.8.8, domain evil.com, email a@b.com, APT29 组织, pcap 抓包";
            List<NerEntityVO> entities = List.of(buildNerVO("8.8.8.8", "IP"));

            List<FileTagEntity> result = engine.recognizeTags(1L, text, "report.pdf", "pdf", entities);

            Set<String> codes = result.stream()
                    .map(FileTagEntity::getTagCode)
                    .collect(Collectors.toSet());
            assertTrue(codes.contains("L3.ENTITY.VULN.CVE"));
            assertTrue(codes.contains("L3.ENTITY.IP.PUBLIC"));
            assertTrue(codes.contains("L3.ENTITY.DOMAIN"));
            assertTrue(codes.contains("L3.ENTITY.USER.EMAIL"));
            assertTrue(codes.contains("L1.FILE.TYPE.PDF"));
            assertTrue(codes.contains("L5.INTEL.APT.APT29"));
            assertTrue(codes.contains("L4.SCENE.TARGET_PROFILE"));
            assertTrue(codes.contains("L4.SCENE.NETWORK_TOPOLOGY"));
            assertTrue(codes.contains("L4.SCENE.VULNERABILITY_RECON"));
            // 全部为自动来源
            assertTrue(result.stream().allMatch(t -> "AUTO".equals(t.getSource())));
            // 批量插入被调用
            verify(fileTagMapper).batchInsert(anyList());
        }

        @Test
        @DisplayName("recognizeTags: 重复标签去重")
        void testRecognizeTags_Dedup() {
            // REGEX 与 ML 同时命中同一公网 IP，PUBLIC 应只出现一次
            List<NerEntityVO> entities = List.of(buildNerVO("8.8.8.8", "IP"));

            List<FileTagEntity> result = engine.recognizeTags(1L, "节点 8.8.8.8", null, null, entities);

            // PUBLIC 出现且仅出现一次（验证去重）
            long publicCount = result.stream()
                    .filter(t -> "L3.ENTITY.IP.PUBLIC".equals(t.getTagCode()))
                    .count();
            assertEquals(1, publicCount);
            // 无重复 tagCode
            Set<String> uniqueCodes = result.stream()
                    .map(FileTagEntity::getTagCode)
                    .collect(Collectors.toSet());
            assertEquals(uniqueCodes.size(), result.size());
            assertTrue(uniqueCodes.contains("L3.ENTITY.IP.PUBLIC"));
        }

        @Test
        @DisplayName("recognizeTags: 全空输入返回空列表且不触达 Mapper")
        void testRecognizeTags_EmptyInput() {
            List<FileTagEntity> result = engine.recognizeTags(1L, "", null, null, null);

            assertTrue(result.isEmpty());
            verifyNoInteractions(tagDictMapper);
            verifyNoInteractions(fileTagMapper);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造字典实体（id=1L）
     */
    private TagDictEntity buildDictEntity() {
        TagDictEntity entity = new TagDictEntity();
        entity.setId(1L);
        entity.setTagCode("ANY");
        return entity;
    }

    /**
     * 构造 NER 实体 VO
     */
    private NerEntityVO buildNerVO(String text, String type) {
        NerEntityVO vo = new NerEntityVO();
        vo.setEntityText(text);
        vo.setEntityType(type);
        vo.setConfidence(0.95f);
        return vo;
    }
}
