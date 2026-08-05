package com.redteam.parse.parser;

import com.redteam.parse.dto.ParseResultDTO;
import org.apache.tika.parser.AutoDetectParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tika 文件解析器单元测试
 *
 * <p>覆盖文本/HTML 文件解析、元数据提取、空输入处理、字符上限、文件路径解析等场景。</p>
 *
 * @author 红方团队
 */
class TikaFileParserTest {

    private TikaFileParser tikaFileParser;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tikaFileParser = new TikaFileParser();
        ReflectionTestUtils.setField(tikaFileParser, "maxTextLength", 1_048_576);
        ReflectionTestUtils.setField(tikaFileParser, "tikaTimeoutSeconds", 60);
    }

    // ==================== parse(InputStream, filename) ====================

    @Test
    @DisplayName("parse: null 输入流返回失败结果")
    void parse_nullInput_returnsFail() {
        ParseResultDTO result = tikaFileParser.parse(null, "empty.txt");
        assertFalse(result.getSuccess());
        assertEquals("FAILED", result.getParseStatus());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("parse: 纯文本文件解析成功")
    void parse_textFile_success() {
        String content = "Hello World from red team";
        InputStream is = new ByteArrayInputStream(content.getBytes());

        ParseResultDTO result = tikaFileParser.parse(is, "test.txt");

        assertTrue(result.getSuccess());
        assertEquals("SUCCESS", result.getParseStatus());
        assertNotNull(result.getTextContent());
        assertTrue(result.getTextContent().contains("Hello World"));
        assertEquals("test.txt", result.getFileName());
        assertNotNull(result.getDuration());
        assertNotNull(result.getMetadata());
    }

    @Test
    @DisplayName("parse: HTML 文件解析提取正文")
    void parse_htmlFile_success() {
        String html = "<html><head><title>Test</title></head><body><p>Hello HTML</p></body></html>";
        InputStream is = new ByteArrayInputStream(html.getBytes());

        ParseResultDTO result = tikaFileParser.parse(is, "test.html");

        assertTrue(result.getSuccess());
        assertNotNull(result.getTextContent());
        assertTrue(result.getTextContent().contains("Hello HTML"));
    }

    @Test
    @DisplayName("parse: XML 文件解析提取内容")
    void parse_xmlFile_success() {
        String xml = "<?xml version=\"1.0\"?><root><item>test content</item></root>";
        InputStream is = new ByteArrayInputStream(xml.getBytes());

        ParseResultDTO result = tikaFileParser.parse(is, "test.xml");

        assertTrue(result.getSuccess());
        assertNotNull(result.getTextContent());
    }

    @Test
    @DisplayName("parse: 空内容文件解析成功")
    void parse_emptyContent_success() {
        InputStream is = new ByteArrayInputStream(new byte[0]);

        ParseResultDTO result = tikaFileParser.parse(is, "empty.txt");

        assertTrue(result.getSuccess());
        assertEquals(0, result.getTextLength());
    }

    @Test
    @DisplayName("parse: 超大文本触发字符上限仍标记成功（截断）")
    void parse_largeText_triggersWriteLimit() {
        // 设置一个很小的上限触发 SAXException
        ReflectionTestUtils.setField(tikaFileParser, "maxTextLength", 10);
        String content = "01234567890123456789".repeat(100);
        InputStream is = new ByteArrayInputStream(content.getBytes());

        ParseResultDTO result = tikaFileParser.parse(is, "large.txt");

        // 触发 SAXException 时被捕获，标记成功（截断）
        assertTrue(result.getSuccess());
        assertEquals("SUCCESS", result.getParseStatus());
        assertEquals(10, result.getTextLength());
    }

    // ==================== parse(filePath) ====================

    @Test
    @DisplayName("parse(path): 空路径返回失败")
    void parsePath_nullPath_returnsFail() {
        ParseResultDTO result = tikaFileParser.parse((String) null);
        assertFalse(result.getSuccess());
    }

    @Test
    @DisplayName("parse(path): 空白路径返回失败")
    void parsePath_blankPath_returnsFail() {
        ParseResultDTO result = tikaFileParser.parse("");
        assertFalse(result.getSuccess());
    }

    @Test
    @DisplayName("parse(path): 文本文件路径解析成功")
    void parsePath_textFile_success() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello from path");

        ParseResultDTO result = tikaFileParser.parse(file.toString());

        assertTrue(result.getSuccess());
        assertTrue(result.getTextContent().contains("Hello from path"));
        assertEquals("test.txt", result.getFileName());
    }

    @Test
    @DisplayName("parse(path): 文件不存在返回失败")
    void parsePath_notExists_returnsFail() {
        ParseResultDTO result = tikaFileParser.parse("/tmp/nonexistent-file-12345.txt");
        assertFalse(result.getSuccess());
        assertNotNull(result.getErrorMessage());
    }

    // ==================== 元数据 ====================

    @Test
    @DisplayName("parse: 元数据非空，包含 Content-Type")
    void parse_metadataPopulated() {
        String content = "sample text";
        InputStream is = new ByteArrayInputStream(content.getBytes());

        ParseResultDTO result = tikaFileParser.parse(is, "sample.txt");

        assertNotNull(result.getMetadata());
        assertFalse(result.getMetadata().isEmpty());
        // 至少有 Content-Type 元数据
        assertTrue(result.getMetadata().keySet().stream()
                .anyMatch(k -> k.toLowerCase().contains("content")));
    }

    @Test
    @DisplayName("parse: 默认页数为 1（无页数元数据时）")
    void parse_defaultPageCount() {
        String content = "plain text";
        InputStream is = new ByteArrayInputStream(content.getBytes());

        ParseResultDTO result = tikaFileParser.parse(is, "plain.txt");

        assertEquals(1, result.getPageCount());
    }
}
