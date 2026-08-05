package com.redteam.parse.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.redteam.parse.dto.NerEntityVO;
import com.redteam.parse.service.NerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.Batchifier;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * security-BERT NER 实体识别服务实现
 *
 * <p>实现要点：</p>
 * <ul>
 *   <li>使用 DJL 加载 security-BERT 模型进行推理；模型加载失败时降级到正则兜底方案。</li>
 *   <li>正则兜底识别：IP/域名/URL/邮箱/MD5/SHA256/CVE 等红方关注实体。</li>
 *   <li>识别结果缓存到 Redis（key: ner:result:{fileId}，TTL 1 小时）。</li>
 *   <li>模型加载失败不影响主流程，自动切换到正则方案。</li>
 *   <li>定时重试模型加载（每 5 分钟），恢复后自动切回模型推理。</li>
 *   <li>Micrometer 指标：推理延迟、降级次数、推理总次数。</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Service
public class NerServiceImpl implements NerService {

    /**
     * Redis 缓存 Key 前缀
     */
    private static final String CACHE_KEY_PREFIX = "ner:result:";

    /**
     * 实体类型：IP 地址
     */
    private static final String TYPE_IP = "IP";

    /**
     * 实体类型：域名
     */
    private static final String TYPE_DOMAIN = "DOMAIN";

    /**
     * 实体类型：URL
     */
    private static final String TYPE_URL = "URL";

    /**
     * 实体类型：邮箱
     */
    private static final String TYPE_EMAIL = "EMAIL";

    /**
     * 实体类型：MD5 哈希
     */
    private static final String TYPE_HASH_MD5 = "HASH_MD5";

    /**
     * 实体类型：SHA256 哈希
     */
    private static final String TYPE_HASH_SHA256 = "HASH_SHA256";

    /**
     * 实体类型：CVE 编号
     */
    private static final String TYPE_CVE = "CVE";

    /**
     * 正则表达式：IP 地址
     */
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    /**
     * 正则表达式：URL
     */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+");

    /**
     * 正则表达式：邮箱
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /**
     * 正则表达式：SHA256（优先于 MD5，64 位十六进制）
     */
    private static final Pattern SHA256_PATTERN = Pattern.compile("\\b[a-fA-F0-9]{64}\\b");

    /**
     * 正则表达式：MD5（32 位十六进制）
     */
    private static final Pattern MD5_PATTERN = Pattern.compile("\\b[a-fA-F0-9]{32}\\b");

    /**
     * 正则表达式：CVE 编号
     */
    private static final Pattern CVE_PATTERN = Pattern.compile("CVE-\\d{4}-\\d{4,7}");

    /**
     * 正则表达式：域名（需排除纯 IP 与邮箱已匹配部分）
     */
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "\\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}\\b");

    /**
     * 最大文本长度（避免对超大文本做正则爆炸）
     */
    private static final int MAX_TEXT_LENGTH = 1_000_000;

    /**
     * DJL Predictor 模型是否就绪
     */
    private volatile boolean modelReady = false;

    /**
     * 模型加载失败的降级标记
     */
    private volatile boolean modelLoadFailed = false;

    /**
     * 最近一次模型错误信息（用于健康检查）
     */
    private volatile String lastError = null;

    /**
     * DJL Predictor（包级可见，便于测试注入）
     */
    Predictor<String, List<NerEntityVO>> predictor;

    /**
     * DJL ZooModel（保持打开状态，Predictor 依赖其资源）
     */
    private ZooModel<String, List<NerEntityVO>> zooModel;

    private final StringRedisTemplate redisTemplate;

    /**
     * Micrometer 指标注册表
     */
    private final MeterRegistry meterRegistry;

    /**
     * 推理延迟计时器
     */
    private Timer inferenceLatencyTimer;

    /**
     * 降级次数计数器
     */
    private Counter fallbackCountCounter;

    /**
     * 推理总次数计数器
     */
    private Counter inferenceCountCounter;

    /**
     * 模型本地路径
     */
    @Value("${redteam.parse.ner.model-path:models/security-bert}")
    private String modelPath;

    /**
     * 是否使用正则兜底（默认 true，模型加载失败自动启用）
     */
    @Value("${redteam.parse.ner.use-regex-fallback:true}")
    private boolean useRegexFallback;

    /**
     * 缓存 TTL（秒）
     */
    @Value("${redteam.parse.ner.cache-ttl-seconds:3600}")
    private long cacheTtlSeconds;

    /**
     * 置信度阈值
     */
    @Value("${redteam.parse.ner.confidence-threshold:0.7}")
    private float confidenceThreshold;

    /**
     * 构造方法
     *
     * @param redisTemplate Redis 模板
     * @param meterRegistry Micrometer 指标注册表（可为 null，测试环境用 SimpleMeterRegistry 兜底）
     */
    public NerServiceImpl(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        initMetrics();
    }

    /**
     * 初始化 Micrometer 指标
     */
    private void initMetrics() {
        if (this.meterRegistry == null) {
            return;
        }
        this.inferenceLatencyTimer = Timer.builder("ner_inference_latency_seconds")
                .description("NER 推理延迟（秒）")
                .register(this.meterRegistry);
        this.fallbackCountCounter = Counter.builder("ner_fallback_count_total")
                .description("NER 降级到正则兜底的次数")
                .register(this.meterRegistry);
        this.inferenceCountCounter = Counter.builder("ner_inference_count_total")
                .description("NER 推理总次数")
                .register(this.meterRegistry);
    }

    /**
     * 初始化：尝试预加载模型
     */
    @PostConstruct
    @Override
    public void preloadModel() {
        if (!useRegexFallback) {
            try {
                loadDjlModel();
                modelReady = true;
                modelLoadFailed = false;
                lastError = null;
                log.info("security-BERT NER 模型加载成功: path={}", modelPath);
            } catch (Throwable t) {
                // 捕获 Throwable 防止 native 库加载失败导致 bean 初始化失败
                modelLoadFailed = true;
                modelReady = false;
                lastError = t.getMessage();
                log.warn("security-BERT 模型加载失败，降级到正则兜底方案: path={}, cause={}",
                        modelPath, t.getMessage());
            }
        } else {
            log.info("配置启用正则兜底（use-regex-fallback=true），跳过模型加载");
            modelReady = false;
        }
    }

    /**
     * 加载 DJL security-BERT NER 模型
     *
     * <p>使用 DJL {@link Criteria} 构建 ONNX Runtime 引擎的模型加载条件，
     * 配置自定义 {@link SecurityBertNerTranslator} 翻译器处理 token 分类任务。
     * 加载成功后创建 {@link Predictor} 供推理使用。</p>
     *
     * @throws Exception 模型加载异常（路径不存在、引擎不可用、模型解析失败等）
     */
    private void loadDjlModel() throws Exception {
        Path path = Paths.get(modelPath);
        if (!Files.exists(path)) {
            throw new IOException("模型路径不存在: " + modelPath);
        }

        log.info("开始加载 DJL security-BERT NER 模型: path={}, engine=OnnxRuntime", modelPath);

        Path tokenizerPath = path.resolve("tokenizer.json");
        Criteria<String, List<NerEntityVO>> criteria = Criteria.builder()
                .setTypes(String.class, (Class<List<NerEntityVO>>) (Class<?>) List.class)
                .optModelPath(path)
                .optEngine("OnnxRuntime")
                .optTranslator(new SecurityBertNerTranslator(tokenizerPath))
                .optOption("hasParameter", "true")
                .build();

        // 关闭旧的模型资源（重试场景）
        closeModel();
        zooModel = criteria.loadModel();
        predictor = zooModel.newPredictor();
    }

    /**
     * 关闭模型资源
     */
    private void closeModel() {
        if (predictor != null) {
            try {
                predictor.close();
            } catch (Exception e) {
                log.warn("关闭 Predictor 失败: {}", e.getMessage());
            }
        }
        if (zooModel != null) {
            try {
                zooModel.close();
            } catch (Exception e) {
                log.warn("关闭 ZooModel 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 定时重试模型加载（每 5 分钟）
     *
     * <p>当模型加载失败（{@code modelLoadFailed=true}）时自动尝试重新加载。
     * 重试成功则恢复模型就绪状态；失败则更新错误信息等待下次重试。</p>
     */
    @Scheduled(fixedDelay = 300_000)
    public void retryModelLoad() {
        if (!modelLoadFailed) {
            return;
        }
        log.info("定时重试加载 security-BERT 模型: path={}", modelPath);
        try {
            loadDjlModel();
            modelReady = true;
            modelLoadFailed = false;
            lastError = null;
            log.info("security-BERT 模型重试加载成功，恢复模型推理模式");
        } catch (Throwable t) {
            lastError = t.getMessage();
            log.warn("security-BERT 模型重试加载失败: {}", t.getMessage());
        }
    }

    @Override
    public List<NerEntityVO> extractEntities(String text) {
        if (StrUtil.isBlank(text)) {
            return new ArrayList<>();
        }
        String truncated = truncate(text);

        // 记录推理总次数
        if (inferenceCountCounter != null) {
            inferenceCountCounter.increment();
        }

        long startTime = System.nanoTime();
        boolean usedFallback = false;

        if (modelReady && !modelLoadFailed && predictor != null) {
            try {
                List<NerEntityVO> result = extractByModel(truncated);
                recordLatency(startTime);
                return result;
            } catch (Exception e) {
                log.warn("模型推理失败，降级到正则: {}", e.getMessage());
                modelReady = false;
                lastError = e.getMessage();
                usedFallback = true;
            }
        } else {
            usedFallback = true;
        }

        List<NerEntityVO> result = extractByRegex(truncated);
        recordLatency(startTime);

        if (usedFallback && fallbackCountCounter != null) {
            fallbackCountCounter.increment();
        }
        return result;
    }

    /**
     * 记录推理延迟
     *
     * @param startTime 起始时间（纳秒）
     */
    private void recordLatency(long startTime) {
        if (inferenceLatencyTimer != null) {
            inferenceLatencyTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public List<NerEntityVO> extractEntitiesFromFile(Long fileId, String filePath) {
        if (fileId == null || StrUtil.isBlank(filePath)) {
            log.warn("NER 文件参数非法: fileId={}, filePath={}", fileId, filePath);
            return new ArrayList<>();
        }
        // 命中缓存直接返回
        List<NerEntityVO> cached = getFromCache(fileId);
        if (cached != null) {
            log.debug("命中 NER 缓存: fileId={}", fileId);
            return cached;
        }
        try {
            Path path = Paths.get(filePath);
            String content = Files.readString(path, StandardCharsets.UTF_8);
            List<NerEntityVO> entities = extractEntities(content);
            saveToCache(fileId, entities);
            return entities;
        } catch (IOException e) {
            log.error("读取文件失败: fileId={}, filePath={}", fileId, filePath, e);
            return new ArrayList<>();
        }
    }

    @Override
    public Map<String, Object> getModelStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        String statusStr;
        if (modelReady) {
            statusStr = "READY";
        } else if (modelLoadFailed) {
            statusStr = "FAILED";
        } else {
            statusStr = "FALLBACK";
        }
        status.put("status", statusStr);
        status.put("modelPath", modelPath);
        status.put("lastError", lastError);
        return status;
    }

    // ==================== 模型推理 ====================

    /**
     * 使用模型推理提取实体
     *
     * <p>调用 DJL {@link Predictor#predict(Object)} 获取 security-BERT 推理结果。
     * 翻译器已完成 BIO 标签解析，此方法负责过滤置信度低于阈值的结果。</p>
     *
     * @param text 文本内容
     * @return 实体列表
     * @throws TranslateException 推理异常
     */
    private List<NerEntityVO> extractByModel(String text) throws TranslateException {
        List<NerEntityVO> rawEntities = predictor.predict(text);
        List<NerEntityVO> filtered = new ArrayList<>();
        for (NerEntityVO entity : rawEntities) {
            if (entity.getConfidence() == null || entity.getConfidence() >= confidenceThreshold) {
                filtered.add(entity);
            }
        }
        log.debug("模型推理完成: 原始实体数={}, 过滤后={}", rawEntities.size(), filtered.size());
        return filtered;
    }

    // ==================== 正则兜底 ====================

    /**
     * 基于正则表达式提取实体
     *
     * <p>按优先级匹配：URL > EMAIL > SHA256 > MD5 > CVE > IP > DOMAIN。
     * 使用已匹配区间跳过重复识别。</p>
     *
     * @param text 文本内容
     * @return 实体列表
     */
    private List<NerEntityVO> extractByRegex(String text) {
        List<NerEntityVO> result = new ArrayList<>();
        Map<Integer, Integer> occupied = new LinkedHashMap<>();

        // URL（优先级最高，避免被 DOMAIN 截取）
        collectByPattern(text, URL_PATTERN, TYPE_URL, "URL", result, occupied);
        // EMAIL（优先于 DOMAIN，避免域名被识别为邮箱后缀）
        collectByPattern(text, EMAIL_PATTERN, TYPE_EMAIL, "邮箱", result, occupied);
        // SHA256（优先于 MD5，64 位 hex）
        collectByPattern(text, SHA256_PATTERN, TYPE_HASH_SHA256, "SHA256", result, occupied);
        // MD5（32 位 hex）
        collectByPattern(text, MD5_PATTERN, TYPE_HASH_MD5, "MD5", result, occupied);
        // CVE
        collectByPattern(text, CVE_PATTERN, TYPE_CVE, "CVE", result, occupied);
        // IP
        collectByPattern(text, IP_PATTERN, TYPE_IP, "IP地址", result, occupied);
        // DOMAIN（最后匹配，避免误识别 URL/EMAIL 中的域名）
        collectByPattern(text, DOMAIN_PATTERN, TYPE_DOMAIN, "域名", result, occupied);

        return result;
    }

    /**
     * 按正则收集实体，跳过已占用区间
     *
     * @param text      原文本
     * @param pattern   正则
     * @param type      实体类型
     * @param label     实体标签
     * @param result    结果列表
     * @param occupied  已占用区间（start -> end）
     */
    private void collectByPattern(String text, Pattern pattern, String type, String label,
                                   List<NerEntityVO> result, Map<Integer, Integer> occupied) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            int start = m.start();
            int end = m.end();
            if (isOverlapped(start, end, occupied)) {
                continue;
            }
            NerEntityVO vo = new NerEntityVO();
            vo.setEntityText(m.group());
            vo.setEntityType(type);
            vo.setEntityLabel(label);
            vo.setStartPos(start);
            vo.setEndPos(end);
            vo.setConfidence(0.95f);
            result.add(vo);
            occupied.put(start, end);
        }
    }

    /**
     * 判断区间是否与已占用区间重叠
     *
     * @param start     起始位置
     * @param end       结束位置
     * @param occupied  已占用区间
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

    // ==================== 缓存读写 ====================

    /**
     * 从 Redis 读取缓存
     *
     * @param fileId 文件ID
     * @return 实体列表，未命中返回 null
     */
    private List<NerEntityVO> getFromCache(Long fileId) {
        try {
            String key = CACHE_KEY_PREFIX + fileId;
            String json = redisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            return JSONUtil.toList(json, NerEntityVO.class);
        } catch (Exception e) {
            log.warn("读取 NER 缓存失败: fileId={}", fileId, e);
            return null;
        }
    }

    /**
     * 写入 Redis 缓存
     *
     * @param fileId   文件ID
     * @param entities 实体列表
     */
    private void saveToCache(Long fileId, List<NerEntityVO> entities) {
        try {
            String key = CACHE_KEY_PREFIX + fileId;
            String json = JSONUtil.toJsonStr(entities);
            redisTemplate.opsForValue().set(key, json, cacheTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入 NER 缓存失败: fileId={}", fileId, e);
        }
    }

    // ==================== 工具方法 ====================

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

    // ==================== 测试可见方法 ====================

    /**
     * 测试用：获取模型就绪状态
     *
     * @return 是否就绪
     */
    boolean isModelReady() {
        return modelReady;
    }

    /**
     * 测试用：获取模型加载失败标记
     *
     * @return 是否失败
     */
    boolean isModelLoadFailed() {
        return modelLoadFailed;
    }

    /**
     * 测试用：设置 Predictor（跳过真实模型加载）
     *
     * @param predictor DJL Predictor 实例
     */
    void setPredictor(Predictor<String, List<NerEntityVO>> predictor) {
        this.predictor = predictor;
    }

    /**
     * 测试用：获取当前 Predictor
     *
     * @return Predictor 实例（可能为 null）
     */
    Predictor<String, List<NerEntityVO>> getPredictorForTest() {
        return predictor;
    }

    // ==================== DJL 翻译器 ====================

    /**
     * security-BERT NER 翻译器
     *
     * <p>负责将输入文本通过 HuggingFace tokenizer 编码为模型输入张量，
     * 并将模型输出的 logits 解析为 BIO 标签，最终合并为 {@link NerEntityVO} 列表。</p>
     *
     * <p>BIO 标签映射（security-BERT 红方实体模型）：</p>
     * <ul>
     *   <li>B-IP / I-IP → IP 地址</li>
     *   <li>B-DOMAIN / I-DOMAIN → 域名</li>
     *   <li>B-URL / I-URL → URL</li>
     *   <li>B-EMAIL / I-EMAIL → 邮箱</li>
     *   <li>B-HASH / I-HASH → 哈希</li>
     *   <li>B-CVE / I-CVE → CVE 编号</li>
     * </ul>
     */
    public static class SecurityBertNerTranslator implements Translator<String, List<NerEntityVO>> {

        /**
         * BIO 标签 ID 到标签字符串的映射
         */
        private static final Map<Integer, String> ID2LABEL = Map.ofEntries(
                Map.entry(0, "O"),
                Map.entry(1, "B-IP"), Map.entry(2, "I-IP"),
                Map.entry(3, "B-DOMAIN"), Map.entry(4, "I-DOMAIN"),
                Map.entry(5, "B-URL"), Map.entry(6, "I-URL"),
                Map.entry(7, "B-EMAIL"), Map.entry(8, "I-EMAIL"),
                Map.entry(9, "B-HASH"), Map.entry(10, "I-HASH"),
                Map.entry(11, "B-CVE"), Map.entry(12, "I-CVE")
        );

        /**
         * BIO 标签到实体类型的映射
         */
        private static final Map<String, String> LABEL_TYPE_MAP = Map.ofEntries(
                Map.entry("B-IP", TYPE_IP), Map.entry("I-IP", TYPE_IP),
                Map.entry("B-DOMAIN", TYPE_DOMAIN), Map.entry("I-DOMAIN", TYPE_DOMAIN),
                Map.entry("B-URL", TYPE_URL), Map.entry("I-URL", TYPE_URL),
                Map.entry("B-EMAIL", TYPE_EMAIL), Map.entry("I-EMAIL", TYPE_EMAIL),
                Map.entry("B-HASH", "HASH"), Map.entry("I-HASH", "HASH"),
                Map.entry("B-CVE", TYPE_CVE), Map.entry("I-CVE", TYPE_CVE)
        );

        /**
         * Tokenizer 文件路径
         */
        private final Path tokenizerPath;

        private HuggingFaceTokenizer tokenizer;

        /**
         * 构造翻译器
         *
         * @param tokenizerPath tokenizer.json 文件路径
         */
        public SecurityBertNerTranslator(Path tokenizerPath) {
            this.tokenizerPath = tokenizerPath;
        }

        /**
         * 不返回 Batchifier（单条推理）
         *
         * @return null
         */
        @Override
        public Batchifier getBatchifier() {
            return null;
        }

        /**
         * 准备阶段：加载 tokenizer
         *
         * @param ctx 翻译上下文
         * @throws Exception tokenizer 加载异常
         */
        @Override
        public void prepare(TranslatorContext ctx) throws Exception {
            if (tokenizerPath != null && Files.exists(tokenizerPath)) {
                tokenizer = HuggingFaceTokenizer.newInstance(tokenizerPath);
            }
        }

        /**
         * 处理输入：将文本编码为模型输入张量
         *
         * @param ctx    翻译上下文
         * @param input  输入文本
         * @return 模型输入 NDList（input_ids + attention_mask）
         * @throws Exception 编码异常
         */
        @Override
        public NDList processInput(TranslatorContext ctx, String input) throws Exception {
            if (tokenizer == null) {
                throw new IOException("Tokenizer 未初始化，无法编码输入文本");
            }
            Encoding encoding = tokenizer.encode(input);
            NDManager manager = ctx.getNDManager();
            long[] ids = encoding.getIds();
            long[] mask = encoding.getAttentionMask();
            NDArray idArray = manager.create(ids);
            NDArray maskArray = manager.create(mask);
            return new NDList(idArray, maskArray);
        }

        /**
         * 处理输出：将模型 logits 解析为 BIO 标签并合并为实体
         *
         * @param ctx  翻译上下文
         * @param list 模型输出 NDList
         * @return 实体列表
         * @throws Exception 解析异常
         */
        @Override
        public List<NerEntityVO> processOutput(TranslatorContext ctx, NDList list) throws Exception {
            NDArray logits = list.singletonOrThrow();
            int dim = logits.getShape().dimension();
            // argmax 沿最后一维（num_labels）
            NDArray predictions = logits.argMax(dim - 1);
            // softmax 沿最后一维获取置信度
            NDArray probs = logits.softmax(dim - 1);

            long[] labelIds = predictions.toLongArray();
            float[] maxProbs = extractMaxProbs(probs, labelIds);

            // 解析 BIO 标签并合并实体
            return parseBioTags(labelIds, maxProbs);
        }

        /**
         * 从 softmax 输出中提取每个 token 预测标签的置信度
         *
         * @param probs    softmax 概率数组
         * @param labelIds 预测标签 ID 数组
         * @return 置信度数组
         */
        private float[] extractMaxProbs(NDArray probs, long[] labelIds) {
            float[] allProbs = probs.toFloatArray();
            int numLabels = (int) probs.getShape().get(probs.getShape().dimension() - 1);
            float[] maxProbs = new float[labelIds.length];
            for (int i = 0; i < labelIds.length; i++) {
                int offset = i * numLabels + (int) labelIds[i];
                if (offset < allProbs.length) {
                    maxProbs[i] = allProbs[offset];
                } else {
                    maxProbs[i] = 0f;
                }
            }
            return maxProbs;
        }

        /**
         * 解析 BIO 标签序列，合并 B-/I- 标签为完整实体
         *
         * @param labelIds 标签 ID 序列
         * @param probs    每个 token 的置信度
         * @return 合并后的实体列表
         */
        private List<NerEntityVO> parseBioTags(long[] labelIds, float[] probs) {
            List<NerEntityVO> entities = new ArrayList<>();
            StringBuilder currentText = new StringBuilder();
            String currentType = null;
            float currentConfidence = 0f;
            int entityStart = -1;

            for (int i = 0; i < labelIds.length; i++) {
                String label = ID2LABEL.getOrDefault((int) labelIds[i], "O");
                float confidence = i < probs.length ? probs[i] : 0f;

                if (label.startsWith("B-")) {
                    // 保存前一个实体
                    if (currentType != null) {
                        addEntity(entities, currentText.toString(), currentType, entityStart, i, currentConfidence);
                    }
                    currentType = LABEL_TYPE_MAP.get(label);
                    currentText = new StringBuilder();
                    currentConfidence = confidence;
                    entityStart = i;
                    currentText.append(label.substring(2));
                } else if (label.startsWith("I-") && currentType != null
                        && LABEL_TYPE_MAP.get(label) != null
                        && LABEL_TYPE_MAP.get(label).equals(currentType)) {
                    currentText.append(label.substring(2));
                    currentConfidence = Math.min(currentConfidence, confidence);
                } else {
                    // O 标签或类型不匹配，结束当前实体
                    if (currentType != null) {
                        addEntity(entities, currentText.toString(), currentType, entityStart, i, currentConfidence);
                    }
                    currentType = null;
                    currentText = new StringBuilder();
                }
            }
            // 处理序列末尾的实体
            if (currentType != null) {
                addEntity(entities, currentText.toString(), currentType, entityStart, labelIds.length, currentConfidence);
            }
            return entities;
        }

        /**
         * 添加实体到结果列表
         *
         * @param entities   结果列表
         * @param text       实体文本
         * @param type       实体类型
         * @param start      起始 token 位置
         * @param end        结束 token 位置
         * @param confidence 置信度
         */
        private void addEntity(List<NerEntityVO> entities, String text, String type,
                                int start, int end, float confidence) {
            if (StrUtil.isBlank(text)) {
                return;
            }
            NerEntityVO vo = new NerEntityVO();
            vo.setEntityText(text);
            vo.setEntityType(type);
            vo.setEntityLabel(type);
            vo.setStartPos(start);
            vo.setEndPos(end);
            vo.setConfidence(confidence);
            entities.add(vo);
        }
    }
}
