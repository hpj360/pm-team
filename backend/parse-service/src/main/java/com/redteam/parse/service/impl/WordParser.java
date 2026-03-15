package com.redteam.parse.service.impl;

import com.redteam.parse.dto.ParseResultDTO;
import com.redteam.parse.service.FileParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Word文档解析器
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class WordParser implements FileParser {

    @Override
    public String[] getSupportedTypes() {
        return new String[]{"docx"};
    }

    @Override
    public ParseResultDTO parse(InputStream inputStream, String filename) {
        long startTime = System.currentTimeMillis();
        ParseResultDTO result = new ParseResultDTO();

        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            // 提取文本内容
            String textContent = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));

            result.setSuccess(true);
            result.setTextContent(textContent);
            result.setTextLength(textContent.length());
            result.setPageCount(1); // Word文档页数需要特殊处理

            // 提取元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("paragraphCount", document.getParagraphs().size());
            metadata.put("tableCount", document.getTables().size());
            result.setMetadata(metadata);

            long endTime = System.currentTimeMillis();
            result.setDuration(endTime - startTime);

            log.info("Word文档解析成功: {}, 段落数: {}, 文本长度: {}, 耗时: {}ms",
                    filename, document.getParagraphs().size(), result.getTextLength(), result.getDuration());

        } catch (Exception e) {
            log.error("Word文档解析失败: {}", filename, e);
            result.setSuccess(false);
            result.setErrorMessage("Word文档解析失败: " + e.getMessage());
        }

        return result;
    }
}
