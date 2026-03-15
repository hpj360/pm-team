package com.redteam.parse.controller;

import com.redteam.common.result.Result;
import com.redteam.parse.dto.ParseResultDTO;
import com.redteam.parse.service.FileParseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 文件解析控制器
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/parse")
@RequiredArgsConstructor
@Tag(name = "文件解析接口", description = "文件内容解析、文本提取等接口")
public class FileParseController {

    private final FileParseService fileParseService;

    /**
     * 解析文件
     *
     * @param storagePath 存储路径
     * @param filename    文件名
     * @param fileType    文件类型
     * @return 解析结果
     */
    @PostMapping("/file")
    @Operation(summary = "解析文件", description = "解析指定文件并提取文本内容")
    public Result<ParseResultDTO> parseFile(
            @Parameter(description = "存储路径") @RequestParam("storagePath") String storagePath,
            @Parameter(description = "文件名") @RequestParam("filename") String filename,
            @Parameter(description = "文件类型") @RequestParam("fileType") String fileType) {

        log.info("解析文件: {}", filename);
        ParseResultDTO result = fileParseService.parseFile(storagePath, filename, fileType);
        return Result.success(result);
    }

    /**
     * 异步解析文件
     *
     * @param fileId 文件ID
     * @return 是否成功
     */
    @PostMapping("/async/{fileId}")
    @Operation(summary = "异步解析文件", description = "异步解析指定文件")
    public Result<Void> parseFileAsync(
            @Parameter(description = "文件ID") @PathVariable("fileId") Long fileId) {

        log.info("异步解析文件: {}", fileId);
        fileParseService.parseFileAsync(fileId);
        return Result.success();
    }
}
