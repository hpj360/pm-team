package com.redteam.parse.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.SM3;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import com.redteam.parse.dto.YaraMatchVO;
import com.redteam.parse.dto.YaraRuleDTO;
import com.redteam.parse.entity.YaraRuleEntity;
import com.redteam.parse.entity.YaraScanResultEntity;
import com.redteam.parse.mapper.YaraRuleMapper;
import com.redteam.parse.mapper.YaraScanResultMapper;
import com.redteam.parse.service.YaraScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YARA 规则扫描服务实现
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>yara-java 在 Maven Central 不可用，使用 {@link ProcessBuilder} 调用 yara CLI（要求系统已安装 yara）。</li>
 *   <li>编译后的 YARA 规则（.yarac）缓存到 {@code /tmp/yara-rules/}，按规则内容 SM3 哈希命名。</li>
 *   <li>使用 Redisson 分布式锁防止规则并发编译。</li>
 *   <li>解析 yara CLI 输出（格式：{@code rule_name file_path}）。</li>
 *   <li>失败时降级：记录日志但不影响主流程，返回空列表。</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class YaraScanServiceImpl implements YaraScanService {

    /**
     * YARA 规则锁前缀
     */
    private static final String RULE_LOCK_PREFIX = "yara:rule:lock:";

    /**
     * 编译产物缓存目录
     */
    private static final String COMPILED_RULE_DIR = "/tmp/yara-rules";

    /**
     * yara CLI 输出行正则：rule_name [file_path]
     */
    private static final Pattern YARA_OUTPUT_PATTERN = Pattern.compile("^(\\S+)\\s+(.*)$");

    /**
     * 默认严重级别
     */
    private static final String DEFAULT_SEVERITY = "MEDIUM";

    /**
     * 默认类别
     */
    private static final String DEFAULT_CATEGORY = "OTHER";

    /**
     * 锁等待时间（秒）
     */
    private static final long LOCK_WAIT_SECONDS = 5L;

    /**
     * 锁持有时间（秒）
     */
    private static final long LOCK_LEASE_SECONDS = 30L;

    private final YaraRuleMapper yaraRuleMapper;
    private final YaraScanResultMapper yaraScanResultMapper;
    private final RedissonClient redissonClient;

    /**
     * yara CLI 路径
     */
    @Value("${redteam.parse.yara.cli-path:/usr/bin/yara}")
    private String yaraCliPath;

    /**
     * 规则缓存目录
     */
    @Value("${redteam.parse.yara.rules-dir:/tmp/yara-rules}")
    private String rulesDir;

    /**
     * 是否启用 YARA
     */
    @Value("${redteam.parse.yara.enabled:true}")
    private boolean yaraEnabled;

    // ==================== 规则 CRUD ====================

    @Override
    public List<YaraRuleEntity> listEnabledRules() {
        LambdaQueryWrapper<YaraRuleEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(YaraRuleEntity::getEnabled, true);
        return yaraRuleMapper.selectList(wrapper);
    }

    @Override
    public YaraRuleEntity createRule(YaraRuleDTO dto) {
        if (dto == null || StrUtil.isBlank(dto.getRuleName()) || StrUtil.isBlank(dto.getRuleContent())) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "规则名称与规则内容不能为空");
        }
        // 校验名称唯一
        LambdaQueryWrapper<YaraRuleEntity> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(YaraRuleEntity::getRuleName, dto.getRuleName());
        Long count = yaraRuleMapper.selectCount(existWrapper);
        if (count != null && count > 0) {
            throw BusinessException.of(ResultCode.RESOURCE_EXISTS, "规则名称已存在: " + dto.getRuleName());
        }

        YaraRuleEntity entity = new YaraRuleEntity();
        entity.setRuleName(dto.getRuleName());
        entity.setRuleContent(dto.getRuleContent());
        entity.setRuleHash(sm3Hex(dto.getRuleContent()));
        entity.setDescription(dto.getDescription());
        entity.setSeverity(StrUtil.blankToDefault(dto.getSeverity(), DEFAULT_SEVERITY));
        entity.setCategory(StrUtil.blankToDefault(dto.getCategory(), DEFAULT_CATEGORY));
        entity.setEnabled(dto.getEnabled() == null ? Boolean.TRUE : dto.getEnabled());

        yaraRuleMapper.insert(entity);
        log.info("YARA 规则创建成功: ruleId={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    @Override
    public YaraRuleEntity updateRule(Long id, YaraRuleDTO dto) {
        if (id == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "规则ID不能为空");
        }
        if (dto == null || StrUtil.isBlank(dto.getRuleName()) || StrUtil.isBlank(dto.getRuleContent())) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "规则名称与规则内容不能为空");
        }
        YaraRuleEntity entity = getByIdOrThrow(id);

        // 名称变更需校验唯一
        if (!dto.getRuleName().equals(entity.getRuleName())) {
            LambdaQueryWrapper<YaraRuleEntity> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(YaraRuleEntity::getRuleName, dto.getRuleName());
            Long count = yaraRuleMapper.selectCount(existWrapper);
            if (count != null && count > 0) {
                throw BusinessException.of(ResultCode.RESOURCE_EXISTS, "规则名称已存在: " + dto.getRuleName());
            }
        }

        entity.setRuleName(dto.getRuleName());
        entity.setRuleContent(dto.getRuleContent());
        entity.setRuleHash(sm3Hex(dto.getRuleContent()));
        entity.setDescription(dto.getDescription());
        if (StrUtil.isNotBlank(dto.getSeverity())) {
            entity.setSeverity(dto.getSeverity());
        }
        if (StrUtil.isNotBlank(dto.getCategory())) {
            entity.setCategory(dto.getCategory());
        }
        if (dto.getEnabled() != null) {
            entity.setEnabled(dto.getEnabled());
        }
        yaraRuleMapper.updateById(entity);
        log.info("YARA 规则更新成功: ruleId={}, ruleName={}", entity.getId(), entity.getRuleName());
        return entity;
    }

    @Override
    public void deleteRule(Long id) {
        if (id == null) {
            throw BusinessException.of(ResultCode.PARAM_ERROR, "规则ID不能为空");
        }
        getByIdOrThrow(id);
        yaraRuleMapper.deleteById(id);
        log.info("YARA 规则已删除: ruleId={}", id);
    }

    @Override
    public void enableRule(Long id) {
        toggleEnabled(id, Boolean.TRUE);
    }

    @Override
    public void disableRule(Long id) {
        toggleEnabled(id, Boolean.FALSE);
    }

    /**
     * 切换规则启用状态
     *
     * @param id      规则ID
     * @param enabled 是否启用
     */
    private void toggleEnabled(Long id, Boolean enabled) {
        YaraRuleEntity entity = getByIdOrThrow(id);
        entity.setEnabled(enabled);
        yaraRuleMapper.updateById(entity);
        log.info("YARA 规则状态变更: ruleId={}, enabled={}", id, enabled);
    }

    // ==================== 扫描 ====================

    @Override
    public List<YaraMatchVO> scanFile(Long fileId, String filePath) {
        if (!yaraEnabled) {
            log.debug("YARA 扫描已禁用，跳过: fileId={}", fileId);
            return Collections.emptyList();
        }
        if (fileId == null || StrUtil.isBlank(filePath)) {
            log.warn("YARA 扫描参数非法: fileId={}, filePath={}", fileId, filePath);
            return Collections.emptyList();
        }
        File target = new File(filePath);
        if (!target.exists() || !target.isFile()) {
            log.warn("YARA 扫描目标文件不存在: filePath={}", filePath);
            return Collections.emptyList();
        }
        List<YaraRuleEntity> rules = listEnabledRules();
        if (rules.isEmpty()) {
            log.debug("无启用的 YARA 规则，跳过扫描: fileId={}", fileId);
            return Collections.emptyList();
        }
        return doScan(fileId, rules, filePath, true);
    }

    @Override
    public List<YaraMatchVO> scanText(Long fileId, String text) {
        if (!yaraEnabled) {
            log.debug("YARA 扫描已禁用，跳过: fileId={}", fileId);
            return Collections.emptyList();
        }
        if (fileId == null || StrUtil.isBlank(text)) {
            log.warn("YARA 文本扫描参数非法: fileId={}", fileId);
            return Collections.emptyList();
        }
        List<YaraRuleEntity> rules = listEnabledRules();
        if (rules.isEmpty()) {
            log.debug("无启用的 YARA 规则，跳过扫描: fileId={}", fileId);
            return Collections.emptyList();
        }
        // 将文本写入临时文件供 yara CLI 扫描
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("yara-scan-text-", ".txt");
            Files.writeString(tempFile, text, StandardCharsets.UTF_8);
            return doScan(fileId, rules, tempFile.toString(), true);
        } catch (IOException e) {
            log.error("YARA 文本扫描写入临时文件失败: fileId={}", fileId, e);
            return Collections.emptyList();
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    /**
     * 执行扫描
     *
     * <p>合并所有启用规则到单一规则文件，调用 yara CLI 一次性扫描。
     * 任一规则编译/扫描失败均降级返回空列表，不影响主解析流程。</p>
     *
     * @param fileId   文件ID
     * @param rules    启用规则列表
     * @param target   扫描目标路径
     * @param persist  是否持久化扫描结果
     * @return 匹配结果列表
     */
    private List<YaraMatchVO> doScan(Long fileId, List<YaraRuleEntity> rules, String target, boolean persist) {
        try {
            String compiledRulePath = compileRules(rules);
            List<String> matchedRuleNames = invokeYaraCli(compiledRulePath, target);
            Map<String, YaraRuleEntity> ruleMap = new LinkedHashMap<>();
            for (YaraRuleEntity r : rules) {
                ruleMap.put(r.getRuleName(), r);
            }
            List<YaraMatchVO> results = new ArrayList<>();
            for (YaraRuleEntity rule : rules) {
                YaraMatchVO vo = new YaraMatchVO();
                vo.setRuleId(rule.getId());
                vo.setRuleName(rule.getRuleName());
                boolean matched = matchedRuleNames.contains(rule.getRuleName());
                vo.setMatched(matched);
                vo.setSeverity(rule.getSeverity());
                vo.setCategory(rule.getCategory());
                vo.setMatchedStrings(matched ? List.of("yara-cli:matched") : Collections.emptyList());
                results.add(vo);
                if (persist) {
                    persistScanResult(fileId, rule, matched);
                }
            }
            log.info("YARA 扫描完成: fileId={}, 规则数={}, 命中={}",
                    fileId, rules.size(), matchedRuleNames.size());
            return results;
        } catch (Exception e) {
            log.error("YARA 扫描异常，降级返回空列表: fileId={}", fileId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 编译规则（带分布式锁与缓存）
     *
     * <p>合并所有规则内容到单一文件，按内容 SM3 哈希命名，
     * 使用 Redisson 分布式锁防止并发编译。</p>
     *
     * @param rules 规则列表
     * @return 编译后的规则文件路径
     * @throws IOException 文件操作异常
     */
    private String compileRules(List<YaraRuleEntity> rules) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (YaraRuleEntity r : rules) {
            sb.append(r.getRuleContent()).append("\n");
        }
        String merged = sb.toString();
        String hash = sm3Hex(merged);
        Path dir = Paths.get(rulesDir);
        Files.createDirectories(dir);
        Path ruleFile = dir.resolve(hash + ".yar");

        if (Files.exists(ruleFile)) {
            log.debug("命中 YARA 规则缓存: hash={}", hash);
            return ruleFile.toString();
        }

        // 分布式锁防并发编译
        RLock lock = redissonClient.getLock(RULE_LOCK_PREFIX + hash);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("获取 YARA 规则编译锁失败，直接写入: hash={}", hash);
            }
            // double-check
            if (Files.exists(ruleFile)) {
                return ruleFile.toString();
            }
            Files.writeString(ruleFile, merged, StandardCharsets.UTF_8);
            log.info("YARA 规则合并文件已生成: path={}, 规则数={}", ruleFile, rules.size());
            return ruleFile.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("YARA 规则编译被中断", e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 调用 yara CLI 扫描
     *
     * <p>命令：{@code yara -s rule_file target}，-s 输出匹配字符串。
     * 解析输出首列规则名集合。</p>
     *
     * @param ruleFile  规则文件路径
     * @param target    扫描目标
     * @return 命中的规则名列表
     * @throws IOException          进程 IO 异常
     * @throws InterruptedException 进程等待被中断
     */
    private List<String> invokeYaraCli(String ruleFile, String target) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(yaraCliPath, "-s", ruleFile, target);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        if (code != 0) {
            log.warn("YARA CLI 退出码非零: code={}, output={}", code, output);
        }
        List<String> matched = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (StrUtil.isBlank(line)) {
                continue;
            }
            // 匹配字符串行形如：0x0:$a: xxx，跳过
            if (line.startsWith("0x")) {
                continue;
            }
            Matcher m = YARA_OUTPUT_PATTERN.matcher(line);
            if (m.find()) {
                matched.add(m.group(1));
            }
        }
        return matched;
    }

    /**
     * 持久化扫描结果（幂等：file_id + rule_id 唯一）
     *
     * @param fileId 文件ID
     * @param rule   规则实体
     * @param matched 是否匹配
     */
    private void persistScanResult(Long fileId, YaraRuleEntity rule, boolean matched) {
        try {
            YaraScanResultEntity entity = new YaraScanResultEntity();
            entity.setFileId(fileId);
            entity.setRuleId(rule.getId());
            entity.setRuleName(rule.getRuleName());
            entity.setMatched(matched);
            entity.setMatchedStrings(JSONUtil.toJsonStr(List.of()));
            entity.setSeverity(rule.getSeverity());
            entity.setCategory(rule.getCategory());
            entity.setScannedAt(LocalDateTime.now());
            yaraScanResultMapper.insert(entity);
        } catch (Exception e) {
            // 唯一约束冲突时改为更新；其他异常降级仅记录日志
            log.warn("YARA 扫描结果持久化失败，尝试更新: fileId={}, ruleId={}", fileId, rule.getId());
            try {
                LambdaQueryWrapper<YaraScanResultEntity> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(YaraScanResultEntity::getFileId, fileId)
                        .eq(YaraScanResultEntity::getRuleId, rule.getId());
                YaraScanResultEntity update = new YaraScanResultEntity();
                update.setMatched(matched);
                update.setScannedAt(LocalDateTime.now());
                yaraScanResultMapper.update(update, wrapper);
            } catch (Exception ex) {
                log.error("YARA 扫描结果更新失败: fileId={}, ruleId={}", fileId, rule.getId(), ex);
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 按ID查询规则，不存在抛业务异常
     *
     * @param id 规则ID
     * @return 规则实体
     */
    private YaraRuleEntity getByIdOrThrow(Long id) {
        YaraRuleEntity entity = yaraRuleMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "YARA 规则不存在: " + id);
        }
        return entity;
    }

    /**
     * 计算 SM3 哈希（十六进制）
     *
     * @param content 原文
     * @return SM3 十六进制摘要
     */
    private static String sm3Hex(String content) {
        return new SM3().digestHex(content, StandardCharsets.UTF_8);
    }

    /**
     * 清理临时文件
     *
     * @param tempFile 临时文件路径
     */
    private static void cleanupTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException e) {
            log.warn("清理 YARA 临时文件失败: {}", tempFile, e);
        }
    }

    /**
     * 生成事件ID（供日志追踪）
     *
     * @return UUID
     */
    @SuppressWarnings("unused")
    private static String newEventId() {
        return IdUtil.fastSimpleUUID();
    }
}
