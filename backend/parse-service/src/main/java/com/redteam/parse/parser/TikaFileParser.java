package com.redteam.parse.parser;

import com.redteam.parse.dto.ParseResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Tika 文件解析器
 *
 * <p>基于 Apache Tika 2.9.1 实现统一文件内容提取，支持
 * PDF/Word/Excel/PPT/HTML/TXT/RTF 等主流格式。</p>
 *
 * <p>特性：</p>
 * <ul>
 *   <li>自动检测文件类型，无需手动指定解析器。</li>
 *   <li>限制最大提取字符数（默认 1MB），避免 OOM。</li>
 *   <li>提取元数据（标题、作者、创建时间、MIME 类型等）。</li>
 *   <li>检测文本语言（基于 Tika LanguageDetector）。</li>
 * </ul>
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class TikaFileParser {

    /**
     * 默认最大提取字符数：1MB
     */
    private static final int DEFAULT_MAX_TEXT_LENGTH = 1_048_576;

    /**
     * Tika 元数据：标题
     */
    private static final String META_TITLE = "dc:title";

    /**
     * Tika 元数据：作者
     */
    private static final String META_AUTHOR = "dc:creator";

    /**
     * Tika 元数据：摘要
     */
    private static final String META_SUBJECT = "dc:subject";

    /**
     * Tika 元数据：页数
     */
    private static final String META_PAGE_COUNT = "xmpTPg:NPages";

    /**
     * Tika 元数据：编码
     */
    private static final String META_ENCODING = "Content-Encoding";

    /**
     * Tika 元数据：MIME 类型
     */
    private static final String META_CONTENT_TYPE = "Content-Type";

    /**
     * 最大提取字符数
     */
    @Value("${redteam.parse.max-text-length:1048576}")
    private int maxTextLength;

    /**
     * Tika 超时（秒）—— 当前实现仅作为日志记录依据，Tika 同步解析不主动超时
     */
    @Value("${redteam.parse.tika-timeout-seconds:60}")
    private int tikaTimeoutSeconds;

    /**
     * 语言检测器（懒加载，复用）
     */
    private volatile LanguageDetector languageDetector;

    /**
     * 解析文件输入流
     *
     * @param inputStream 文件输入流
     * @param filename    文件名
     * @return 解析结果
     */
    public ParseResultDTO parse(InputStream inputStream, String filename) {
        if (inputStream == null) {
            return ParseResultDTO.fail("输入流为空");
        }
        long start = System.currentTimeMillis();
        int limit = maxTextLength > 0 ? maxTextLength : DEFAULT_MAX_TEXT_LENGTH;
        BodyContentHandler handler = new BodyContentHandler(limit);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        Parser parser = new AutoDetectParser();

        ParseResultDTO result = new ParseResultDTO();
        try {
            parser.parse(inputStream, handler, metadata, context);
            String text = handler.toString();

            result.setSuccess(true);
            result.setParseStatus("SUCCESS");
            result.setTextContent(text);
            result.setTextLength(text == null ? 0 : text.length());
            result.setFileName(filename);

            // 元数据
            Map<String, Object> meta = new HashMap<>();
            for (String name : metadata.names()) {
                meta.put(name, metadata.get(name));
            }
            result.setMetadata(meta);

            // 提取关键字段
            result.setTitle(metadata.get(META_TITLE));
            result.setAuthor(metadata.get(META_AUTHOR));
            result.setSummary(metadata.get(META_SUBJECT));
            result.setEncoding(metadata.get(META_ENCODING));
            result.setLanguage(detectLanguage(text));

            String pageStr = metadata.get(META_PAGE_COUNT);
            if (pageStr != null) {
                try {
                    result.setPageCount(Integer.parseInt(pageStr.trim()));
                } catch (NumberFormatException ignore) {
                    result.setPageCount(1);
                }
            } else {
                result.setPageCount(1);
            }

            result.setDuration(System.currentTimeMillis() - start);
            result.setParseDurationMs(result.getDuration());
            log.info("Tika 解析成功: {}, 文本长度={}, 页数={}, 耗时={}ms",
                    filename, result.getTextLength(), result.getPageCount(), result.getDuration());
        } catch (SAXException e) {
            // BodyContentHandler 超过 writeLimit 会抛出 SAXException
            log.warn("Tika 解析触发字符上限（可能截断）: filename={}, msg={}", filename, e.getMessage());
            result.setSuccess(true);
            result.setParseStatus("SUCCESS");
            result.setTextLength(limit);
            result.setFileName(filename);
            result.setDuration(System.currentTimeMillis() - start);
            result.setParseDurationMs(result.getDuration());
        } catch (TikaException | IOException e) {
            log.error("Tika 解析失败: {}", filename, e);
            result = ParseResultDTO.fail("Tika 解析失败: " + e.getMessage());
            result.setFileName(filename);
            result.setDuration(System.currentTimeMillis() - start);
            result.setParseDurationMs(result.getDuration());
        }
        return result;
    }

    /**
     * 解析文件路径（便捷方法）
     *
     * @param filePath 文件路径
     * @return 解析结果
     */
    public ParseResultDTO parse(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return ParseResultDTO.fail("文件路径为空");
        }
        try (InputStream is = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(filePath))) {
            return parse(is, java.nio.file.Paths.get(filePath).getFileName().toString());
        } catch (IOException e) {
            log.error("打开文件失败: {}", filePath, e);
            return ParseResultDTO.fail("打开文件失败: " + e.getMessage());
        }
    }

    /**
     * 检测语言
     *
     * <p>使用 Tika LanguageDetector，加载失败返回 null，不影响主流程。</p>
     *
     * @param text 文本内容
     * @return 语言代码，例如 en/zh
     */
    private String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            if (languageDetector == null) {
                synchronized (this) {
                    if (languageDetector == null) {
                        languageDetector = LanguageDetector.getDefaultLanguageDetector();
                        languageDetector.loadModels();
                    }
                }
            }
            LanguageResult result = languageDetector.detect(text);
            return result == null ? null : result.getLanguage();
        } catch (Exception e) {
            log.warn("语言检测失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 设置最大文本长度（测试用）
     *
     * @param maxTextLength 最大文本长度
     */
    void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }
}
