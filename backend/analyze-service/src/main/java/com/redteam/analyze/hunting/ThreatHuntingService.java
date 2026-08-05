package com.redteam.analyze.hunting;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.redteam.analyze.hunting.entity.AttackTechniqueEntity;
import com.redteam.analyze.hunting.entity.HuntingHypothesisEntity;
import com.redteam.analyze.hunting.entity.HuntingHypothesisEntity.HuntingHit;
import com.redteam.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 威胁狩猎服务
 *
 * <p>核心能力：</p>
 * <ol>
 *   <li>{@link #createHypothesis(String, String, Long)}：创建狩猎假设，关联 ATT&CK 技术</li>
 *   <li>{@link #validateHypothesis(String)}：自动检索含该技术指标的文件/实体/网络连接，产出命中清单 + 置信度 + 推荐 IOC</li>
 *   <li>{@link #getHypothesis(String)} / {@link #listHypotheses()}：查询假设</li>
 * </ol>
 *
 * <p>检索逻辑（简化版）：基于 ATT&CK 技术的数据源（{@code dataSource}）与关键词，
 * 在内置狩猎数据集（{@link #seedHuntingData}）中检索匹配项。</p>
 *
 * <p>状态机：{@code DRAFT → VALIDATING → CONFIRMED / REFUTED}</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThreatHuntingService {

    /**
     * ISO 时间格式化器
     */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 假设存储（id -> entity）
     */
    private final Map<String, HuntingHypothesisEntity> hypothesisStore = new ConcurrentHashMap<>();

    private final AttackMatrixService attackMatrixService;

    /**
     * 内置狩猎数据集（模拟文件 / 实体 / 网络连接三类狩猎源）
     *
     * <p>生产环境应替换为基于数据库 + 搜索引擎的检索实现。</p>
     */
    private final List<Map<String, Object>> seedHuntingData = new ArrayList<>();

    /**
     * 构造时初始化内置狩猎数据集
     */
    {
        // 文件类
        addSeed("FILE", "f-001", "malware_sample_001.exe", "powershell -enc SGVsbG8=", "T1059.001");
        addSeed("FILE", "f-002", "dropper.dll", "rundll32 entry point", "T1218.011");
        addSeed("FILE", "f-003", "ransomware.exe", "shadowcopy delete vssadmin", "T1490");
        addSeed("FILE", "f-004", "mimikatz.exe", "sekurlsa::logonpasswords", "T1003.001");
        addSeed("FILE", "f-005", "phishing.docx", "malicious macro execution", "T1566");

        // 网络类
        addSeed("NETWORK", "n-001", "HTTP C2 traffic", "POST /gate.php to 45.155.205.233", "T1071.001");
        addSeed("NETWORK", "n-002", "DNS tunneling", "exfiltration via DNS queries", "T1071.004");
        addSeed("NETWORK", "n-003", "WinRM lateral", "WinRM connection to 192.168.1.20", "T1021.006");

        // 实体类（IOC）
        addSeed("ENTITY", "e-001", "C2 IP 45.155.205.233", "observed in network traffic", "T1071");
        addSeed("ENTITY", "e-002", "evil domain malicious-update.example-evil.com", "resolved via DNS", "T1071.004");
        addSeed("ENTITY", "e-003", "dropped file hash sha256:abc123", "dropped by malware_sample_001", "T1105");
    }

    /**
     * 添加狩猎源数据
     */
    private void addSeed(String entityType, String entityId, String entityName,
                         String evidence, String techniqueId) {
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("entityType", entityType);
        seed.put("entityId", entityId);
        seed.put("entityName", entityName);
        seed.put("evidence", evidence);
        seed.put("techniqueId", techniqueId);
        seedHuntingData.add(seed);
    }

    /**
     * 创建狩猎假设
     *
     * @param description 假设描述
     * @param techniqueId ATT&CK 技术 ID（必填）
     * @param userId      创建人ID
     * @return 假设ID
     */
    public String createHypothesis(String description, String techniqueId, Long userId) {
        if (StrUtil.isBlank(description)) {
            throw new BusinessException("假设描述不能为空");
        }
        if (StrUtil.isBlank(techniqueId)) {
            throw new BusinessException("ATT&CK 技术 ID 不能为空");
        }
        AttackTechniqueEntity technique = attackMatrixService.getTechnique(techniqueId);
        if (technique == null) {
            throw new BusinessException("ATT&CK 技术不存在: " + techniqueId);
        }

        HuntingHypothesisEntity entity = new HuntingHypothesisEntity();
        entity.setId("hyp-" + IdUtil.fastSimpleUUID());
        entity.setDescription(description);
        entity.setTechniqueId(techniqueId);
        entity.setUserId(userId);
        entity.setStatus(HuntingHypothesisEntity.STATUS_DRAFT);
        entity.setConfidence(0.0);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        hypothesisStore.put(entity.getId(), entity);
        log.info("狩猎假设创建: id={}, techniqueId={}, userId={}", entity.getId(), techniqueId, userId);
        return entity.getId();
    }

    /**
     * 验证假设：自动检索含该技术指标的实体
     *
     * @param hypothesisId 假设ID
     * @return 验证后的假设实体
     */
    public HuntingHypothesisEntity validateHypothesis(String hypothesisId) {
        HuntingHypothesisEntity entity = getHypothesisInternal(hypothesisId);
        entity.setStatus(HuntingHypothesisEntity.STATUS_VALIDATING);
        entity.setUpdateTime(LocalDateTime.now());

        String techniqueId = entity.getTechniqueId();
        AttackTechniqueEntity technique = attackMatrixService.getTechnique(techniqueId);

        // 检索命中项：techniqueId 完全匹配 + 父技术匹配（子技术命中时父技术也命中）
        List<HuntingHit> hits = new ArrayList<>();
        List<String> recommendedIocs = new ArrayList<>();
        for (Map<String, Object> seed : seedHuntingData) {
            String seedTechId = (String) seed.get("techniqueId");
            if (matchesTechnique(seedTechId, techniqueId, technique)) {
                HuntingHit hit = new HuntingHit();
                hit.setEntityType((String) seed.get("entityType"));
                hit.setEntityId((String) seed.get("entityId"));
                hit.setEntityName((String) seed.get("entityName"));
                hit.setDescription("命中 ATT&CK 技术 " + techniqueId + " 的指标");
                hit.setScore(computeScore(seedTechId, techniqueId));
                hit.setEvidence((String) seed.get("evidence"));
                hits.add(hit);

                // 推荐 IOC：从 evidence 中提取
                collectIocs((String) seed.get("evidence"), recommendedIocs);
            }
        }

        entity.setHits(hits);
        entity.setRecommendedIocs(recommendedIocs);
        entity.setConfidence(computeConfidence(hits));
        entity.setValidatedTime(LocalDateTime.now());

        // 状态判定：有命中 → CONFIRMED，无命中 → REFUTED
        if (hits.isEmpty()) {
            entity.setStatus(HuntingHypothesisEntity.STATUS_REFUTED);
        } else {
            entity.setStatus(HuntingHypothesisEntity.STATUS_CONFIRMED);
        }
        entity.setUpdateTime(LocalDateTime.now());
        log.info("狩猎假设验证完成: id={}, hits={}, confidence={}, status={}",
                hypothesisId, hits.size(), entity.getConfidence(), entity.getStatus());
        return entity;
    }

    /**
     * 获取假设（含 ATT&CK 技术元数据）
     *
     * @param hypothesisId 假设ID
     * @return 假设 VO
     */
    public HypothesisVO getHypothesis(String hypothesisId) {
        HuntingHypothesisEntity entity = getHypothesisInternal(hypothesisId);
        return toVO(entity);
    }

    /**
     * 列出全部假设
     *
     * @return 假设 VO 列表
     */
    public List<HypothesisVO> listHypotheses() {
        return hypothesisStore.values().stream()
                .sorted((a, b) -> {
                    if (b.getCreateTime() == null) return 1;
                    if (a.getCreateTime() == null) return -1;
                    return b.getCreateTime().compareTo(a.getCreateTime());
                })
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取狩猎源数据集（供测试与展示）
     *
     * @return 狩猎源数据列表
     */
    public List<Map<String, Object>> getHuntingData() {
        return Collections.unmodifiableList(seedHuntingData);
    }

    // ==================== 内部方法 ====================

    /**
     * 获取假设（内部，不存在抛异常）
     */
    private HuntingHypothesisEntity getHypothesisInternal(String hypothesisId) {
        if (StrUtil.isBlank(hypothesisId)) {
            throw new BusinessException("假设ID不能为空");
        }
        HuntingHypothesisEntity entity = hypothesisStore.get(hypothesisId);
        if (entity == null) {
            throw new BusinessException("狩猎假设不存在: " + hypothesisId);
        }
        return entity;
    }

    /**
     * 技术匹配判定
     *
     * <p>规则：</p>
     * <ul>
     *   <li>seed 技术与目标技术 ID 完全相同 → 匹配</li>
     *   <li>目标技术是子技术，且 seed 是其父技术 → 匹配（父技术命中包含子技术场景）</li>
     *   <li>seed 是子技术，且目标是其父技术 → 匹配（子技术命中归属到父技术）</li>
     * </ul>
     */
    private boolean matchesTechnique(String seedTechId, String targetTechId, AttackTechniqueEntity targetTechnique) {
        if (StrUtil.isBlank(seedTechId) || StrUtil.isBlank(targetTechId)) {
            return false;
        }
        if (seedTechId.equalsIgnoreCase(targetTechId)) {
            return true;
        }
        // 父子关系判定：T1059.001 的父技术是 T1059
        if (seedTechId.contains(".")) {
            String parent = seedTechId.split("\\.")[0];
            if (parent.equalsIgnoreCase(targetTechId)) {
                return true;
            }
        }
        if (targetTechId.contains(".")) {
            String parent = targetTechId.split("\\.")[0];
            if (parent.equalsIgnoreCase(seedTechId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算命中评分
     */
    private double computeScore(String seedTechId, String targetTechId) {
        if (seedTechId.equalsIgnoreCase(targetTechId)) {
            return 1.0; // 完全匹配
        }
        return 0.7; // 父子匹配
    }

    /**
     * 计算置信度（基于命中数与平均评分）
     */
    private double computeConfidence(List<HuntingHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return 0.0;
        }
        double avgScore = hits.stream()
                .mapToDouble(h -> h.getScore() != null ? h.getScore() : 0.0)
                .average()
                .orElse(0.0);
        // 命中数加权（最多 5 个命中饱和）
        double countFactor = Math.min(hits.size() / 5.0, 1.0);
        // 综合置信度：0.6 * 评分 + 0.4 * 命中数因子
        return Math.round((0.6 * avgScore + 0.4 * countFactor) * 100.0) / 100.0;
    }

    /**
     * 从证据中收集 IOC（IP / 域名 / 哈希）
     */
    private void collectIocs(String evidence, List<String> iocs) {
        if (StrUtil.isBlank(evidence)) {
            return;
        }
        // 简化提取：通过关键词匹配
        String[] tokens = evidence.split("\\s+");
        for (String token : tokens) {
            // IP（粗略）
            if (token.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                if (!iocs.contains(token)) {
                    iocs.add(token);
                }
            }
            // 域名（粗略，包含点且含字母）
            if (token.contains(".") && token.matches(".*[a-zA-Z].*") && token.length() > 4
                    && !token.matches("\\d+\\.\\d+\\.\\d+\\.\\d+.*")) {
                // 去除尾部的标点
                String clean = token.replaceAll("[,.;:)]$", "");
                if (!iocs.contains(clean)) {
                    iocs.add(clean);
                }
            }
            // 哈希
            if (token.startsWith("sha256:")) {
                if (!iocs.contains(token)) {
                    iocs.add(token);
                }
            }
        }
    }

    /**
     * 实体转 VO
     */
    private HypothesisVO toVO(HuntingHypothesisEntity entity) {
        HypothesisVO vo = new HypothesisVO();
        vo.setId(entity.getId());
        vo.setDescription(entity.getDescription());
        vo.setTechniqueId(entity.getTechniqueId());
        // 补充 ATT&CK 技术元数据
        AttackTechniqueEntity technique = attackMatrixService.getTechnique(entity.getTechniqueId());
        if (technique != null) {
            vo.setTechniqueName(technique.getName());
            vo.setTactic(technique.getTactic());
            vo.setTacticName(technique.getTacticName());
        }
        vo.setUserId(entity.getUserId());
        vo.setUserName(entity.getUserName());
        vo.setStatus(entity.getStatus());
        vo.setConfidence(entity.getConfidence());
        vo.setHits(entity.getHits());
        vo.setRecommendedIocs(entity.getRecommendedIocs());
        vo.setValidatedTime(entity.getValidatedTime() != null ? entity.getValidatedTime().format(ISO_FORMATTER) : null);
        vo.setCreateTime(entity.getCreateTime() != null ? entity.getCreateTime().format(ISO_FORMATTER) : null);
        vo.setUpdateTime(entity.getUpdateTime() != null ? entity.getUpdateTime().format(ISO_FORMATTER) : null);
        return vo;
    }

    /**
     * 测试用：获取种子数据大小（可见）
     */
    int seedDataCount() {
        return seedHuntingData.size();
    }

    /**
     * 测试用：获取指定技术的命中种子索引列表
     */
    List<Integer> findSeedIndices(String techniqueId) {
        List<Integer> indices = new ArrayList<>();
        AttackTechniqueEntity technique = attackMatrixService.getTechnique(techniqueId);
        for (int i = 0; i < seedHuntingData.size(); i++) {
            String seedTechId = (String) seedHuntingData.get(i).get("techniqueId");
            if (matchesTechnique(seedTechId, techniqueId, technique)) {
                indices.add(i);
            }
        }
        return indices;
    }
}
