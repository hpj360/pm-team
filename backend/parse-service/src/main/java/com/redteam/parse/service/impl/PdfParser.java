package com.redteam.parse.service.impl;

import com.redteam.parse.dto.ParseResultDTO;
import com.redteam.parse.service.FileParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * PDF文件解析器
 *
 * @author 红方团队
 */
@Slf4j
@Component
public class PdfParser implements FileParser {

    @Override
    public String[] getSupportedTypes() {
        return new String[]{"pdf"};
    }

    @Override
    public ParseResultDTO parse(InputStream inputStream, String filename) {
        long startTime = System.currentTimeMillis();
        ParseResultDTO result = new ParseResultDTO();

        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            // 提取文本内容
            PDFTextStripper stripper = new PDFTextStripper();
            String textContent = stripper.getText(document);

            result.setSuccess(true);
            result.setTextContent(textContent);
            result.setTextLength(textContent.length());
            result.setPageCount(document.getNumberOfPages());

            // 提取元数据
            PDDocumentInformation info = document.getDocumentInformation();
            if (info != null) {
                result.setTitle(info.getTitle());
                result.setAuthor(info.getAuthor());
                result.setSummary(info.getSubject());

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("creator", info.getCreator());
                metadata.put("producer", info.getProducer());
                metadata.put("creationDate", info.getCreationDate());
                metadata.put("modificationDate", info.getModificationDate());
                result.setMetadata(metadata);
            }

            long endTime = System.currentTimeMillis();
            result.setDuration(endTime - startTime);

            log.info("PDF解析成功: {}, 页数: {}, 文本长度: {}, 耗时: {}ms",
                    filename, result.getPageCount(), result.getTextLength(), result.getDuration());

        } catch (Exception e) {
            log.error("PDF解析失败: {}", filename, e);
            result.setSuccess(false);
            result.setErrorMessage("PDF解析失败: " + e.getMessage());
        }

        return result;
    }
}
