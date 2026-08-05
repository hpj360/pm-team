package com.redteam.analyze.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.redteam.analyze.entity.AnalyzeResultEntity;
import com.redteam.analyze.entity.AnalyzeTaskEntity;
import com.redteam.analyze.mapper.AnalyzeResultMapper;
import com.redteam.analyze.mapper.AnalyzeTaskMapper;
import com.redteam.analyze.producer.AnalyzeEventProducer;
import com.redteam.analyze.service.FileAnalyzeService;
import com.redteam.common.api.dto.AnalyzeResultDTO;
import com.redteam.common.api.dto.FileAnalyzeDTO;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文件分析服务实现类
 *
 * <p>实现以下核心能力：</p>
 * <ul>
 *   <li>{@link #extractKeywords}：基于词频统计 + 停用词过滤的 TF-IDF 简化版关键词提取。</li>
 *   <li>{@link #recognizeEntities}：基于正则的实体识别（IP/域名/URL/邮箱/SHA256/MD5/CVE），与 parse-service NerService 对齐。</li>
 *   <li>{@link #analyzeSentiment}：基于情感词典的情感分析，返回正面/负面/中性 + 得分。</li>
 *   <li>{@link #generateSummary}：基于句子重要度的抽取式摘要。</li>
 *   <li>{@link #generateEmbedding}：向量嵌入生成，优先调用 search-service，失败降级到本地哈希嵌入。</li>
 *   <li>{@link #analyze}：主流程，整合上述方法，支持异步。</li>
 *   <li>{@link #analyzeAsync}：发送 Kafka 消息异步处理。</li>
 *   <li>{@link #getAnalyzeResult}：从数据库获取分析结果。</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileAnalyzeServiceImpl implements FileAnalyzeService {

    // ==================== 敏感信息正则 ====================

    /**
     * 邮箱正则
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /**
     * 手机号正则
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");

    /**
     * 身份证号正则
     */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");

    /**
     * 银行卡号正则
     */
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("\\d{16,19}");

    /**
     * IP 地址正则
     */
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    /**
     * 域名正则
     */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+");

    // ==================== 实体识别正则（与 parse-service NerService 对齐） ====================

    /**
     * URL 正则
     */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+");

    /**
     * SHA256 正则（64 位十六进制）
     */
    private static final Pattern SHA256_PATTERN = Pattern.compile("\\b[a-fA-F0-9]{64}\\b");

    /**
     * MD5 正则（32 位十六进制）
     */
    private static final Pattern MD5_PATTERN = Pattern.compile("\\b[a-fA-F0-9]{32}\\b");

    /**
     * CVE 编号正则
     */
    private static final Pattern CVE_PATTERN = Pattern.compile("CVE-\\d{4}-\\d{4,7}");

    // ==================== 实体类型编码 ====================

    /**
     * 实体类型：IP
     */
    private static final int ENTITY_TYPE_IP = 1;

    /**
     * 实体类型：域名
     */
    private static final int ENTITY_TYPE_DOMAIN = 2;

    /**
     * 实体类型：URL
     */
    private static final int ENTITY_TYPE_URL = 3;

    /**
     * 实体类型：邮箱
     */
    private static final int ENTITY_TYPE_EMAIL = 4;

    /**
     * 实体类型：MD5
     */
    private static final int ENTITY_TYPE_MD5 = 5;

    /**
     * 实体类型：SHA256
     */
    private static final int ENTITY_TYPE_SHA256 = 6;

    /**
     * 实体类型：CVE
     */
    private static final int ENTITY_TYPE_CVE = 7;

    // ==================== 分析状态 ====================

    /**
     * 状态：待分析
     */
    private static final int STATUS_PENDING = 0;

    /**
     * 状态：分析中
     */
    private static final int STATUS_ANALYZING = 1;

    /**
     * 状态：已完成
     */
    private static final int STATUS_COMPLETED = 2;

    /**
     * 状态：失败
     */
    private static final int STATUS_FAILED = 3;

    /**
     * 最大文本长度（避免对超大文本做正则爆炸）
     */
    private static final int MAX_TEXT_LENGTH = 1_000_000;

    /**
     * 向量嵌入 Redis 缓存 Key 前缀
     */
    private static final String EMBEDDING_CACHE_PREFIX = "analyze:embedding:";

    /**
     * 停用词集合（中英文常见）
     */
    private static final Set<String> STOP_WORDS = new HashSet<>(java.util.Arrays.asList(
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "这", "那", "与", "或", "及", "但", "而", "对", "为", "以", "于", "等", "被",
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of",
            "with", "by", "from", "is", "are", "was", "were", "be", "been", "being",
            "this", "that", "these", "those", "it", "its", "as", "if", "then", "than",
            "has", "have", "had", "do", "does", "did", "will", "would", "can", "could"
    ));

    /**
     * 正面情感词典
     */
    private static final Set<String> POSITIVE_WORDS = new HashSet<>(java.util.Arrays.asList(
            "好", "优秀", "成功", "安全", "正常", "完成", "良好", "稳定", "高效", "可靠",
            "good", "great", "success", "safe", "secure", "normal", "complete", "excellent",
            "perfect", "positive", "beneficial", "effective", "stable"
    ));

    /**
     * 负面情感词典
     */
    private static final Set<String> NEGATIVE_WORDS = new HashSet<>(java.util.Arrays.asList(
            "坏", "失败", "攻击", "漏洞", "恶意", "病毒", "错误", "异常", "危险", "风险",
            "bad", "fail", "failure", "attack", "vulnerability", "malware", "virus",
            "error", "abnormal", "danger", "risk", "threat", "exploit", "malicious", "dangerous"
    ));

    /**
     * 句子分隔正则
     */
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[。！？.!?;；\n]+");

    private final AnalyzeResultMapper analyzeResultMapper;

    private final AnalyzeTaskMapper analyzeTaskMapper;

    private final AnalyzeEventProducer analyzeEventProducer;

    private final StringRedisTemplate redisTemplate;

    /**
     * 向量化服务 API 地址
     */
    @Value("${redteam.analyze.embedding.api-url:http://localhost:8083/api/search/embed}")
    private String embeddingApiUrl;

    /**
     * 是否启用向量化 HTTP 调用
     */
    @Value("${redteam.analyze.embedding.enabled:true}")
    private boolean embeddingEnabled;

    /**
     * 向量缓存 TTL（秒）
     */
    @Value("${redteam.analyze.embedding.cache-ttl-seconds:86400}")
    private long embeddingCacheTtl;

    /**
     * 分析文件，主流程
     *
     * @param analyzeDTO 分析请求
     * @return 分析结果
     */
    @Override
    public AnalyzeResultDTO analyze(FileAnalyzeDTO analyzeDTO) {
        if (analyzeDTO == null || analyzeDTO.getFileId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分析请求参数非法：fileId 不能为空");
        }
        log.info("开始分析文件: fileId={}, analyzeType={}", analyzeDTO.getFileId(), analyzeDTO.getAnalyzeType());

        AnalyzeResultDTO result = new AnalyzeResultDTO();
        result.setFileId(analyzeDTO.getFileId());
        result.setAnalyzeType(analyzeDTO.getAnalyzeType());
        result.setStatus(STATUS_ANALYZING);
        result.setProgress(0);
        result.setCreateTime(LocalDateTime.now());

        long startTime = System.currentTimeMillis();
        try {
            String textContent = resolveTextContent(analyzeDTO);

            Integer analyzeType = analyzeDTO.getAnalyzeType();
            if (analyzeType == null || analyzeType == 5) {
                // 全文分析
                result.setSensitiveInfos(extractSensitiveInfo(textContent));
                result.setProgress(40);
                result.setKeywords(extractKeywords(textContent, 10));
                result.setProgress(60);
                result.setEntities(recognizeEntities(textContent));
                result.setProgress(80);
                result.setSentiment(analyzeSentiment(textContent));
                result.setSummary(generateSummary(textContent, 200));
                result.setProgress(100);
            } else {
                switch (analyzeType) {
                    case 1 -> {
                        result.setSensitiveInfos(extractSensitiveInfo(textContent));
                        result.setProgress(100);
                    }
                    case 2 -> {
                        result.setKeywords(extractKeywords(textContent, 10));
                        result.setProgress(100);
                    }
                    case 3 -> {
                        result.setEntities(recognizeEntities(textContent));
                        result.setProgress(100);
                    }
                    case 4 -> {
                        result.setSentiment(analyzeSentiment(textContent));
                        result.setProgress(100);
                    }
                    default -> {
                        result.setSummary(generateSummary(textContent, 200));
                        result.setProgress(100);
                    }
                }
            }

            // 生成向量嵌入
            if (Boolean.TRUE.equals(analyzeDTO.getGenerateEmbedding())) {
                String embeddingId = generateEmbedding(textContent);
                result.setEmbeddingId(embeddingId);
            }

            result.setStatus(STATUS_COMPLETED);
            result.setProgress(100);
            result.setFinishTime(LocalDateTime.now());
            log.info("文件分析完成: fileId={}", analyzeDTO.getFileId());
        } catch (Exception e) {
            log.error("文件分析失败: fileId={}", analyzeDTO.getFileId(), e);
            result.setStatus(STATUS_FAILED);
            result.setErrorMessage(e.getMessage());
            result.setFinishTime(LocalDateTime.now());
        }

        result.setDuration(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 异步分析文件：创建任务并发送 Kafka 消息
     *
     * @param analyzeDTO 分析请求
     * @return 任务ID
     */
    @Override
    public Long analyzeAsync(FileAnalyzeDTO analyzeDTO) {
        if (analyzeDTO == null || analyzeDTO.getFileId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分析请求参数非法：fileId 不能为空");
        }
        log.info("提交异步分析任务: fileId={}", analyzeDTO.getFileId());

        AnalyzeTaskEntity task = new AnalyzeTaskEntity();
        task.setFileId(analyzeDTO.getFileId());
        task.setAnalyzeType(analyzeDTO.getAnalyzeType() == null ? 5 : analyzeDTO.getAnalyzeType());
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);
        task.setTextContent(analyzeDTO.getTextContent());
        task.setFilePath(analyzeDTO.getFilePath());
        task.setGenerateEmbedding(Boolean.TRUE.equals(analyzeDTO.getGenerateEmbedding()) ? 1 : 0);

        analyzeTaskMapper.insert(task);
        Long taskId = task.getId();
        log.info("异步分析任务已创建: taskId={}, fileId={}", taskId, analyzeDTO.getFileId());

        analyzeEventProducer.sendAnalyzeRequestEvent(taskId, analyzeDTO.getFileId(), task.getAnalyzeType());
        return taskId;
    }

    /**
     * 获取分析结果
     *
     * @param taskId 任务ID
     * @return 分析结果
     */
    @Override
    public AnalyzeResultDTO getAnalyzeResult(Long taskId) {
        if (taskId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "任务ID不能为空");
        }
        log.info("获取分析结果: taskId={}", taskId);

        AnalyzeResultEntity entity = analyzeResultMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AnalyzeResultEntity>()
                        .eq(AnalyzeResultEntity::getTaskId, taskId)
                        .orderByDesc(AnalyzeResultEntity::getId)
                        .last("LIMIT 1"));

        if (entity == null) {
            throw new BusinessException(ResultCode.ANALYZE_TASK_NOT_FOUND, "分析任务不存在: " + taskId);
        }
        return convertToDTO(entity);
    }

    /**
     * 处理异步分析任务（由事件监听器触发）
     *
     * @param taskId 任务ID
     */
    @Override
    public void processAnalyzeTask(Long taskId) {
        if (taskId == null) {
            log.warn("处理分析任务 taskId 为空");
            return;
        }
        log.info("处理异步分析任务: taskId={}", taskId);

        AnalyzeTaskEntity task = analyzeTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("分析任务不存在: taskId={}", taskId);
            return;
        }

        // 幂等：已完成的任务不再重复处理
        if (task.getStatus() != null && (task.getStatus() == STATUS_COMPLETED || task.getStatus() == STATUS_FAILED)) {
            log.info("任务已处理过，跳过: taskId={}, status={}", taskId, task.getStatus());
            return;
        }

        // 更新为分析中
        task.setStatus(STATUS_ANALYZING);
        analyzeTaskMapper.updateById(task);

        FileAnalyzeDTO dto = new FileAnalyzeDTO();
        dto.setFileId(task.getFileId());
        dto.setAnalyzeType(task.getAnalyzeType());
        dto.setTextContent(task.getTextContent());
        dto.setFilePath(task.getFilePath());
        dto.setGenerateEmbedding(task.getGenerateEmbedding() != null && task.getGenerateEmbedding() == 1);

        try {
            AnalyzeResultDTO result = analyze(dto);

            // 持久化结果
            AnalyzeResultEntity resultEntity = new AnalyzeResultEntity();
            resultEntity.setTaskId(taskId);
            resultEntity.setFileId(task.getFileId());
            resultEntity.setAnalyzeType(task.getAnalyzeType());
            resultEntity.setStatus(result.getStatus());
            resultEntity.setProgress(result.getProgress());
            resultEntity.setResultJson(JSONUtil.toJsonStr(result));
            resultEntity.setErrorMessage(result.getErrorMessage());
            resultEntity.setDuration(result.getDuration());
            resultEntity.setFinishTime(LocalDateTime.now());
            analyzeResultMapper.insert(resultEntity);

            // 更新任务状态
            task.setStatus(result.getStatus());
            task.setProgress(result.getProgress());
            task.setErrorMessage(result.getErrorMessage());
            analyzeTaskMapper.updateById(task);

            if (result.getStatus() == STATUS_COMPLETED) {
                analyzeEventProducer.sendAnalyzeCompletedEvent(taskId, task.getFileId(),
                        result.getDuration(), result.getEmbeddingId());
            } else {
                analyzeEventProducer.sendAnalyzeFailedEvent(taskId, task.getFileId(), result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("处理异步分析任务失败: taskId={}", taskId, e);
            task.setStatus(STATUS_FAILED);
            task.setErrorMessage(e.getMessage());
            analyzeTaskMapper.updateById(task);
            analyzeEventProducer.sendAnalyzeFailedEvent(taskId, task.getFileId(), e.getMessage());
        }
    }

    /**
     * 提取敏感信息
     *
     * @param text 文本内容
     * @return 敏感信息列表
     */
    @Override
    public List<AnalyzeResultDTO.SensitiveInfo> extractSensitiveInfo(String text) {
        log.info("提取敏感信息");
        List<AnalyzeResultDTO.SensitiveInfo> result = new ArrayList<>();
        if (StrUtil.isBlank(text)) {
            return result;
        }

        // 提取邮箱
        collectSensitive(text, EMAIL_PATTERN, 1, 0.95, result);
        // 提取手机号
        collectSensitive(text, PHONE_PATTERN, 2, 0.9, result);
        // 提取身份证号
        collectSensitive(text, ID_CARD_PATTERN, 3, 0.85, result);
        // 提取银行卡号
        collectSensitive(text, BANK_CARD_PATTERN, 4, 0.8, result);
        // 提取IP地址
        collectSensitive(text, IP_PATTERN, 5, 0.9, result);
        // 提取域名
        collectSensitive(text, DOMAIN_PATTERN, 6, 0.85, result);

        return result;
    }

    /**
     * 提取关键词（基于词频统计 + 停用词过滤 + TopN）
     *
     * @param text 文本内容
     * @param topN 返回数量
     * @return 关键词列表
     */
    @Override
    public List<AnalyzeResultDTO.KeywordInfo> extractKeywords(String text, Integer topN) {
        int limit = topN == null || topN <= 0 ? 10 : topN;
        log.info("提取关键词: topN={}", limit);
        List<AnalyzeResultDTO.KeywordInfo> result = new ArrayList<>();
        if (StrUtil.isBlank(text)) {
            return result;
        }

        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return result;
        }

        // 词频统计
        Map<String, Integer> freq = new HashMap<>();
        for (String token : tokens) {
            if (STOP_WORDS.contains(token) || token.length() < 2) {
                continue;
            }
            freq.merge(token, 1, Integer::sum);
        }
        if (freq.isEmpty()) {
            return result;
        }

        int maxFreq = freq.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    AnalyzeResultDTO.KeywordInfo info = new AnalyzeResultDTO.KeywordInfo();
                    info.setKeyword(e.getKey());
                    info.setFrequency(e.getValue());
                    info.setWeight(e.getValue() * 1.0 / maxFreq);
                    return info;
                })
                .collect(Collectors.toList());
    }

    /**
     * 实体识别（基于正则，与 parse-service NerService 对齐）
     *
     * @param text 文本内容
     * @return 实体列表
     */
    @Override
    public List<AnalyzeResultDTO.EntityInfo> recognizeEntities(String text) {
        log.info("实体识别");
        List<AnalyzeResultDTO.EntityInfo> result = new ArrayList<>();
        if (StrUtil.isBlank(text)) {
            return result;
        }
        String truncated = truncate(text);

        // 使用占用区间避免重复识别，优先级：URL > EMAIL > SHA256 > MD5 > CVE > IP > DOMAIN
        Map<Integer, Integer> occupied = new LinkedHashMap<>();
        Map<String, AnalyzeResultDTO.EntityInfo> aggregated = new LinkedHashMap<>();

        collectEntity(truncated, URL_PATTERN, ENTITY_TYPE_URL, "URL", occupied, aggregated);
        collectEntity(truncated, EMAIL_PATTERN, ENTITY_TYPE_EMAIL, "邮箱", occupied, aggregated);
        collectEntity(truncated, SHA256_PATTERN, ENTITY_TYPE_SHA256, "SHA256", occupied, aggregated);
        collectEntity(truncated, MD5_PATTERN, ENTITY_TYPE_MD5, "MD5", occupied, aggregated);
        collectEntity(truncated, CVE_PATTERN, ENTITY_TYPE_CVE, "CVE", occupied, aggregated);
        collectEntity(truncated, IP_PATTERN, ENTITY_TYPE_IP, "IP地址", occupied, aggregated);
        collectEntity(truncated, DOMAIN_PATTERN, ENTITY_TYPE_DOMAIN, "域名", occupied, aggregated);

        return new ArrayList<>(aggregated.values());
    }

    /**
     * 情感分析（基于情感词典）
     *
     * @param text 文本内容
     * @return 情感分析结果
     */
    @Override
    public AnalyzeResultDTO.SentimentInfo analyzeSentiment(String text) {
        log.info("情感分析");
        AnalyzeResultDTO.SentimentInfo sentiment = new AnalyzeResultDTO.SentimentInfo();
        if (StrUtil.isBlank(text)) {
            sentiment.setSentiment(3);
            sentiment.setScore(0.0);
            sentiment.setConfidence(0.5);
            return sentiment;
        }

        List<String> tokens = tokenize(text);
        int positive = 0;
        int negative = 0;
        for (String token : tokens) {
            if (POSITIVE_WORDS.contains(token)) {
                positive++;
            } else if (NEGATIVE_WORDS.contains(token)) {
                negative++;
            }
        }

        int total = positive + negative;
        double score;
        if (total == 0) {
            score = 0.0;
        } else {
            score = (positive - negative) * 1.0 / total;
        }

        int sentimentType;
        if (score > 0.1) {
            sentimentType = 1; // 正面
        } else if (score < -0.1) {
            sentimentType = 2; // 负面
        } else {
            sentimentType = 3; // 中性
        }

        // 置信度：情感词越多越高，最多 1.0
        double confidence = Math.min(1.0, total * 0.2 + 0.3);

        sentiment.setSentiment(sentimentType);
        sentiment.setScore(score);
        sentiment.setConfidence(confidence);
        return sentiment;
    }

    /**
     * 生成文本摘要（基于句子重要度的抽取式摘要）
     *
     * @param text   文本内容
     * @param length 摘要长度
     * @return 摘要
     */
    @Override
    public String generateSummary(String text, Integer length) {
        int maxLen = length == null || length <= 0 ? 200 : length;
        log.info("生成摘要: length={}", maxLen);
        if (StrUtil.isBlank(text)) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }

        // 句子分割
        String[] sentences = SENTENCE_PATTERN.split(text);
        List<String> validSentences = new ArrayList<>();
        for (String s : sentences) {
            String trimmed = s.trim();
            if (StrUtil.isNotBlank(trimmed)) {
                validSentences.add(trimmed);
            }
        }
        if (validSentences.isEmpty()) {
            return text.substring(0, Math.min(text.length(), maxLen));
        }
        if (validSentences.size() == 1) {
            return truncateStr(validSentences.get(0), maxLen);
        }

        // 计算全局词频
        Map<String, Integer> globalFreq = new HashMap<>();
        for (String s : validSentences) {
            for (String token : tokenize(s)) {
                if (STOP_WORDS.contains(token) || token.length() < 2) {
                    continue;
                }
                globalFreq.merge(token, 1, Integer::sum);
            }
        }

        // 句子评分 = 句子词频和 / sqrt(句子词数)
        List<SentenceScore> scored = new ArrayList<>();
        for (int i = 0; i < validSentences.size(); i++) {
            String s = validSentences.get(i);
            List<String> tokens = tokenize(s);
            double sum = 0;
            int validCount = 0;
            for (String token : tokens) {
                if (STOP_WORDS.contains(token) || token.length() < 2) {
                    continue;
                }
                sum += globalFreq.getOrDefault(token, 0);
                validCount++;
            }
            double score = validCount == 0 ? 0 : sum / Math.sqrt(validCount);
            scored.add(new SentenceScore(i, s, score));
        }

        // 取评分最高的若干句（按原文顺序输出）
        int sentenceCount = Math.max(1, Math.min(5, maxLen / 40));
        StringBuilder sb = new StringBuilder();
        scored.stream()
                .sorted(Comparator.comparingDouble(SentenceScore::score).reversed())
                .limit(sentenceCount)
                .sorted(Comparator.comparingInt(SentenceScore::index))
                .forEach(ss -> {
                    if (sb.length() > 0) {
                        sb.append("。");
                    }
                    sb.append(ss.sentence);
                });

        return truncateStr(sb.toString(), maxLen);
    }

    /**
     * 生成向量嵌入
     *
     * <p>优先调用 search-service 向量化 API，失败降级到本地哈希嵌入。</p>
     *
     * @param text 文本内容
     * @return 向量ID
     */
    @Override
    public String generateEmbedding(String text) {
        log.info("生成向量嵌入");
        if (StrUtil.isBlank(text)) {
            String uuid = IdUtil.fastSimpleUUID();
            log.warn("文本为空，返回空嵌入ID: {}", uuid);
            return uuid;
        }

        // 优先调用 search-service
        if (embeddingEnabled) {
            try {
                String embeddingId = embedViaRemote(text);
                if (StrUtil.isNotBlank(embeddingId)) {
                    return embeddingId;
                }
            } catch (Exception e) {
                log.warn("外部向量化服务调用失败，降级到本地嵌入: {}", e.getMessage());
            }
        }

        // 降级：本地哈希嵌入，存入 Redis
        return embedViaLocal(text);
    }

    /**
     * 批量生成向量嵌入
     *
     * @param texts 文本列表
     * @return 向量ID列表
     */
    @Override
    public List<String> batchGenerateEmbedding(List<String> texts) {
        log.info("批量生成向量嵌入: size={}", texts == null ? 0 : texts.size());
        List<String> embeddingIds = new ArrayList<>();
        if (texts == null || texts.isEmpty()) {
            return embeddingIds;
        }
        for (String text : texts) {
            embeddingIds.add(generateEmbedding(text));
        }
        return embeddingIds;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 解析文本内容：优先 textContent，其次 filePath 读取文件
     *
     * @param analyzeDTO 分析请求
     * @return 文本内容
     */
    private String resolveTextContent(FileAnalyzeDTO analyzeDTO) {
        if (StrUtil.isNotBlank(analyzeDTO.getTextContent())) {
            return analyzeDTO.getTextContent();
        }
        if (StrUtil.isNotBlank(analyzeDTO.getFilePath())) {
            try {
                Path path = Paths.get(analyzeDTO.getFilePath());
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("读取文件内容失败: filePath={}", analyzeDTO.getFilePath(), e);
                throw new BusinessException(ResultCode.FILE_PARSE_ERROR, "读取文件内容失败: " + e.getMessage());
            }
        }
        // 无文本内容时返回空串，允许分析空文本
        log.warn("分析请求未提供文本内容: fileId={}", analyzeDTO.getFileId());
        return "";
    }

    /**
     * 按正则收集敏感信息
     *
     * @param text       文本
     * @param pattern    正则
     * @param type       类型
     * @param confidence 置信度
     * @param result     结果列表
     */
    private void collectSensitive(String text, Pattern pattern, int type, double confidence,
                                  List<AnalyzeResultDTO.SensitiveInfo> result) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            AnalyzeResultDTO.SensitiveInfo info = new AnalyzeResultDTO.SensitiveInfo();
            info.setType(type);
            info.setContent(m.group());
            info.setPosition(m.start());
            info.setConfidence(confidence);
            result.add(info);
        }
    }

    /**
     * 按正则收集实体，跳过已占用区间，并按实体文本聚合计数
     *
     * @param text       文本
     * @param pattern    正则
     * @param type       实体类型编码
     * @param label      实体标签
     * @param occupied   已占用区间
     * @param aggregated 聚合结果
     */
    private void collectEntity(String text, Pattern pattern, int type, String label,
                               Map<Integer, Integer> occupied,
                               Map<String, AnalyzeResultDTO.EntityInfo> aggregated) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (isOverlapped(start, end, occupied)) {
                continue;
            }
            occupied.put(start, end);
            String entityText = m.group();
            String key = type + ":" + entityText;
            AnalyzeResultDTO.EntityInfo info = aggregated.get(key);
            if (info == null) {
                info = new AnalyzeResultDTO.EntityInfo();
                info.setType(type);
                info.setName(entityText);
                info.setCount(1);
                aggregated.put(key, info);
            } else {
                info.setCount(info.getCount() + 1);
            }
        }
    }

    /**
     * 判断区间是否与已占用区间重叠
     *
     * @param start    起始位置
     * @param end      结束位置
     * @param occupied 已占用区间
     * @return 是否重叠
     */
    private boolean isOverlapped(int start, int end, Map<Integer, Integer> occupied) {
        for (Map.Entry<Integer, Integer> e : occupied.entrySet()) {
            if (start < e.getValue() && end > e.getKey()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 文本分词：英文按非字母数字拆分小写化，中文按字符二元组
     *
     * @param text 文本
     * @return 词元列表
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (StrUtil.isBlank(text)) {
            return tokens;
        }
        StringBuilder asciiBuf = new StringBuilder();
        StringBuilder cjkBuf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCjk(c)) {
                flushAscii(asciiBuf, tokens);
                cjkBuf.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushCjk(cjkBuf, tokens);
                asciiBuf.append(c);
            } else {
                flushAscii(asciiBuf, tokens);
                flushCjk(cjkBuf, tokens);
            }
        }
        flushAscii(asciiBuf, tokens);
        flushCjk(cjkBuf, tokens);
        return tokens;
    }

    /**
     * 判断字符是否为 CJK 字符
     *
     * @param c 字符
     * @return 是否 CJK
     */
    private boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    /**
     * 刷新 ASCII 缓冲区为词元
     *
     * @param buf    缓冲区
     * @param tokens 词元列表
     */
    private void flushAscii(StringBuilder buf, List<String> tokens) {
        if (buf.length() > 0) {
            tokens.add(buf.toString().toLowerCase());
            buf.setLength(0);
        }
    }

    /**
     * 刷新 CJK 缓冲区为二元组词元
     *
     * @param buf    缓冲区
     * @param tokens 词元列表
     */
    private void flushCjk(StringBuilder buf, List<String> tokens) {
        if (buf.length() == 0) {
            return;
        }
        if (buf.length() == 1) {
            tokens.add(buf.toString());
        } else {
            for (int i = 0; i < buf.length() - 1; i++) {
                tokens.add(buf.substring(i, i + 2));
            }
        }
        buf.setLength(0);
    }

    /**
     * 截断文本至最大长度
     *
     * @param text 原文本
     * @return 截断后的文本
     */
    private String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH);
    }

    /**
     * 截断字符串到指定长度
     *
     * @param s      字符串
     * @param maxLen 最大长度
     * @return 截断后字符串
     */
    private String truncateStr(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }

    /**
     * 通过 HTTP 调用 search-service 生成向量嵌入
     *
     * @param text 文本
     * @return 向量ID
     */
    private String embedViaRemote(String text) {
        RestClient client = RestClient.builder()
                .baseUrl(embeddingApiUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
        JSONObject body = new JSONObject();
        body.set("text", text);
        String resp = client.post()
                .body(body.toString())
                .retrieve()
                .body(String.class);
        if (StrUtil.isBlank(resp)) {
            return null;
        }
        JSONObject json = JSONUtil.parseObj(resp);
        String embeddingId = json.getStr("embeddingId");
        if (StrUtil.isBlank(embeddingId)) {
            embeddingId = json.getStr("embedding_id");
        }
        return embeddingId;
    }

    /**
     * 本地哈希嵌入降级方案
     *
     * <p>生成 UUID 作为嵌入ID，向量缓存到 Redis（基于 SHA-256 的确定性向量）。</p>
     *
     * @param text 文本
     * @return 向量ID
     */
    private String embedViaLocal(String text) {
        String uuid = IdUtil.fastSimpleUUID();
        try {
            byte[] hash = DigestUtil.sha256(text.getBytes(StandardCharsets.UTF_8));
            List<Float> vector = new ArrayList<>(64);
            for (int i = 0; i < 64; i++) {
                byte b = hash[i % hash.length];
                vector.add((b & 0xFF) / 127.5f - 1.0f);
            }
            redisTemplate.opsForValue().set(EMBEDDING_CACHE_PREFIX + uuid,
                    JSONUtil.toJsonStr(vector), embeddingCacheTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("本地嵌入缓存写入失败: {}", e.getMessage());
        }
        return uuid;
    }

    /**
     * 实体转 DTO
     *
     * @param entity 分析结果实体
     * @return 分析结果 DTO
     */
    private AnalyzeResultDTO convertToDTO(AnalyzeResultEntity entity) {
        AnalyzeResultDTO dto = new AnalyzeResultDTO();
        dto.setTaskId(entity.getTaskId());
        dto.setFileId(entity.getFileId());
        dto.setAnalyzeType(entity.getAnalyzeType());
        dto.setStatus(entity.getStatus());
        dto.setProgress(entity.getProgress());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setDuration(entity.getDuration());
        dto.setCreateTime(entity.getCreateTime());
        dto.setFinishTime(entity.getFinishTime());

        if (StrUtil.isNotBlank(entity.getResultJson())) {
            try {
                AnalyzeResultDTO parsed = JSONUtil.toBean(entity.getResultJson(), AnalyzeResultDTO.class);
                dto.setSensitiveInfos(parsed.getSensitiveInfos());
                dto.setKeywords(parsed.getKeywords());
                dto.setEntities(parsed.getEntities());
                dto.setSentiment(parsed.getSentiment());
                dto.setSummary(parsed.getSummary());
                dto.setEmbeddingId(parsed.getEmbeddingId());
            } catch (Exception e) {
                log.warn("解析结果 JSON 失败: taskId={}", entity.getTaskId(), e);
            }
        }
        return dto;
    }

    /**
     * 句子评分内部类
     */
    private static class SentenceScore {

        /**
         * 句子索引
         */
        private final int index;

        /**
         * 句子文本
         */
        private final String sentence;

        /**
         * 评分
         */
        private final double score;

        /**
         * 构造方法
         *
         * @param index    索引
         * @param sentence 句子
         * @param score    评分
         */
        SentenceScore(int index, String sentence, double score) {
            this.index = index;
            this.sentence = sentence;
            this.score = score;
        }

        /**
         * 获取索引
         *
         * @return 索引
         */
        public int index() {
            return index;
        }

        /**
         * 获取评分
         *
         * @return 评分
         */
        public double score() {
            return score;
        }
    }
}
