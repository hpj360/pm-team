package com.redteam.upload.controller;

import com.redteam.common.api.dto.FileInfoDTO;
import com.redteam.common.result.Result;
import com.redteam.upload.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器
 *
 * @author 红方团队
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
@Tag(name = "文件上传接口", description = "文件上传、下载、预览等接口")
public class FileUploadController {

    private final FileService fileService;

    /**
     * 上传文件
     *
     * @param file           文件
     * @param targetId       目标ID
     * @param tags           标签
     * @param description    描述
     * @param sensitiveLevel 敏感等级
     * @param isPublic       是否公开
     * @return 文件信息
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传单个文件到平台")
    public Result<FileInfoDTO> upload(
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "目标ID") @RequestParam(value = "targetId", required = false) Long targetId,
            @Parameter(description = "标签") @RequestParam(value = "tags", required = false) String tags,
            @Parameter(description = "描述") @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "敏感等级(1-低,2-中,3-高)") @RequestParam(value = "sensitiveLevel", required = false, defaultValue = "1") Integer sensitiveLevel,
            @Parameter(description = "是否公开(0-否,1-是)") @RequestParam(value = "isPublic", required = false, defaultValue = "0") Integer isPublic) {

        log.info("上传文件: {}", file.getOriginalFilename());
        FileInfoDTO fileInfo = fileService.upload(file, targetId, tags, description, sensitiveLevel, isPublic);
        return Result.success(fileInfo);
    }

    /**
     * 初始化分片上传
     *
     * @param filename 文件名
     * @param fileSize 文件大小
     * @param fileMd5  文件MD5
     * @param targetId 目标ID
     * @return 上传ID
     */
    @PostMapping("/multipart/init")
    @Operation(summary = "初始化分片上传", description = "大文件分片上传前初始化")
    public Result<String> initMultipartUpload(
            @Parameter(description = "文件名") @RequestParam("filename") String filename,
            @Parameter(description = "文件大小") @RequestParam("fileSize") Long fileSize,
            @Parameter(description = "文件MD5") @RequestParam("fileMd5") String fileMd5,
            @Parameter(description = "目标ID") @RequestParam(value = "targetId", required = false) Long targetId) {

        log.info("初始化分片上传: {}", filename);
        String uploadId = fileService.initMultipartUpload(filename, fileSize, fileMd5, targetId);
        return Result.success(uploadId);
    }

    /**
     * 上传分片
     *
     * @param uploadId   上传ID
     * @param partNumber 分片序号
     * @param partFile   分片文件
     * @return 分片ETag
     */
    @PostMapping("/multipart/part")
    @Operation(summary = "上传分片", description = "上传文件分片")
    public Result<String> uploadPart(
            @Parameter(description = "上传ID") @RequestParam("uploadId") String uploadId,
            @Parameter(description = "分片序号") @RequestParam("partNumber") Integer partNumber,
            @Parameter(description = "分片文件") @RequestParam("partFile") MultipartFile partFile) {

        log.info("上传分片: uploadId={}, partNumber={}", uploadId, partNumber);
        String eTag = fileService.uploadPart(uploadId, partNumber, partFile);
        return Result.success(eTag);
    }

    /**
     * 完成分片上传
     *
     * @param uploadId 上传ID
     * @param parts    分片信息
     * @return 文件信息
     */
    @PostMapping("/multipart/complete")
    @Operation(summary = "完成分片上传", description = "合并所有分片完成上传")
    public Result<FileInfoDTO> completeMultipartUpload(
            @Parameter(description = "上传ID") @RequestParam("uploadId") String uploadId,
            @Parameter(description = "分片信息") @RequestParam("parts") String parts) {

        log.info("完成分片上传: uploadId={}", uploadId);
        FileInfoDTO fileInfo = fileService.completeMultipartUpload(uploadId, parts);
        return Result.success(fileInfo);
    }

    /**
     * 下载文件
     *
     * @param id       文件ID
     * @param response HTTP响应
     */
    @GetMapping("/download/{id}")
    @Operation(summary = "下载文件", description = "下载指定文件")
    public void download(
            @Parameter(description = "文件ID") @PathVariable("id") Long id,
            HttpServletResponse response) {

        log.info("下载文件: {}", id);
        fileService.download(id, response);
    }

    /**
     * 获取文件预览URL
     *
     * @param id 文件ID
     * @return 预览URL
     */
    @GetMapping("/preview/{id}")
    @Operation(summary = "获取预览URL", description = "获取文件预览地址")
    public Result<String> getPreviewUrl(
            @Parameter(description = "文件ID") @PathVariable("id") Long id) {

        log.info("获取预览URL: {}", id);
        String previewUrl = fileService.getPreviewUrl(id);
        return Result.success(previewUrl);
    }

    /**
     * 获取文件详情
     *
     * @param id 文件ID
     * @return 文件信息
     */
    @GetMapping("/info/{id}")
    @Operation(summary = "获取文件详情", description = "获取文件详细信息")
    public Result<FileInfoDTO> getFileInfo(
            @Parameter(description = "文件ID") @PathVariable("id") Long id) {

        log.info("获取文件详情: {}", id);
        FileInfoDTO fileInfo = fileService.getFileInfo(id);
        return Result.success(fileInfo);
    }

    /**
     * 删除文件
     *
     * @param id 文件ID
     * @return 是否成功
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除文件", description = "删除指定文件")
    public Result<Void> deleteFile(
            @Parameter(description = "文件ID") @PathVariable("id") Long id) {

        log.info("删除文件: {}", id);
        fileService.deleteFile(id);
        return Result.success();
    }

    /**
     * 更新文件信息
     *
     * @param id             文件ID
     * @param tags           标签
     * @param description    描述
     * @param sensitiveLevel 敏感等级
     * @param isPublic       是否公开
     * @return 文件信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新文件信息", description = "更新文件的标签、描述等信息")
    public Result<FileInfoDTO> updateFileInfo(
            @Parameter(description = "文件ID") @PathVariable("id") Long id,
            @Parameter(description = "标签") @RequestParam(value = "tags", required = false) String tags,
            @Parameter(description = "描述") @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "敏感等级(1-低,2-中,3-高)") @RequestParam(value = "sensitiveLevel", required = false) Integer sensitiveLevel,
            @Parameter(description = "是否公开(0-否,1-是)") @RequestParam(value = "isPublic", required = false) Integer isPublic) {

        log.info("更新文件信息: {}", id);
        FileInfoDTO fileInfo = fileService.updateFileInfo(id, tags, description, sensitiveLevel, isPublic);
        return Result.success(fileInfo);
    }

    /**
     * 检查文件是否存在（秒传）
     *
     * @param fileMd5 文件MD5
     * @return 文件信息
     */
    @GetMapping("/check")
    @Operation(summary = "检查文件是否存在", description = "根据MD5检查文件是否已存在，实现秒传")
    public Result<FileInfoDTO> checkFile(
            @Parameter(description = "文件MD5") @RequestParam("fileMd5") String fileMd5) {

        log.info("检查文件是否存在: {}", fileMd5);
        FileInfoDTO fileInfo = fileService.checkFileByMd5(fileMd5);
        return Result.success(fileInfo);
    }
}
