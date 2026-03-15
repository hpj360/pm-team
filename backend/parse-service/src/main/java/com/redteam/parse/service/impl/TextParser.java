package com.redteam.parse.service.impl;

import com.redteam.parse.dto.ParseResultDTO;
import com.redteam.parse.service.FileParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 文本文件解析器
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class TextParser implements FileParser {

    @Override
    public String[] getSupportedTypes() {
        return new String[]{"txt", "md", "json", "xml", "html", "css", "js", "java", "py", "sql", "sh", "bat"};
    }

    @Override
    public ParseResultDTO parse(InputStream inputStream, String filename) {
        long startTime = System.currentTimeMillis();
        ParseResultDTO result = new ParseResultDTO();

        try {
            // 读取文本内容
            String textContent = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            result.setSuccess(true);
            result.setTextContent(textContent);
            result.setTextLength(textContent.length());
            result.setPageCount(1);

            long endTime = System.currentTimeMillis();
            result.setDuration(endTime - startTime);

            log.info("文本文件解析成功: {}, 文本长度: {}, 耗时: {}ms",
                    filename, result.getTextLength(), result.getDuration());

        } catch (Exception e) {
            log.error("文本文件解析失败: {}", filename, e);
            result.setSuccess(false);
            result.setErrorMessage("文本文件解析失败: " + e.getMessage());
        }

        return result;
    }
}
