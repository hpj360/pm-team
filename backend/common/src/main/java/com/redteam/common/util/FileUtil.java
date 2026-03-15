package com.redteam.common.util;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.util.StrUtil;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件工具类
 *
 * @author 红方团队
 */
public class FileUtil {

    /**
     * 允许上传的文件类型
     */
    private static final Map<String, String> ALLOWED_FILE_TYPES = new HashMap<>();

    static {
        // 文档类型
        ALLOWED_FILE_TYPES.put("pdf", "application/pdf");
        ALLOWED_FILE_TYPES.put("doc", "application/msword");
        ALLOWED_FILE_TYPES.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        ALLOWED_FILE_TYPES.put("xls", "application/vnd.ms-excel");
        ALLOWED_FILE_TYPES.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        ALLOWED_FILE_TYPES.put("ppt", "application/vnd.ms-powerpoint");
        ALLOWED_FILE_TYPES.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        ALLOWED_FILE_TYPES.put("txt", "text/plain");
        ALLOWED_FILE_TYPES.put("rtf", "application/rtf");

        // 图片类型
        ALLOWED_FILE_TYPES.put("jpg", "image/jpeg");
        ALLOWED_FILE_TYPES.put("jpeg", "image/jpeg");
        ALLOWED_FILE_TYPES.put("png", "image/png");
        ALLOWED_FILE_TYPES.put("gif", "image/gif");
        ALLOWED_FILE_TYPES.put("bmp", "image/bmp");
        ALLOWED_FILE_TYPES.put("svg", "image/svg+xml");

        // 压缩文件
        ALLOWED_FILE_TYPES.put("zip", "application/zip");
        ALLOWED_FILE_TYPES.put("rar", "application/x-rar-compressed");
        ALLOWED_FILE_TYPES.put("7z", "application/x-7z-compressed");
        ALLOWED_FILE_TYPES.put("tar", "application/x-tar");
        ALLOWED_FILE_TYPES.put("gz", "application/gzip");

        // 代码文件
        ALLOWED_FILE_TYPES.put("java", "text/java");
        ALLOWED_FILE_TYPES.put("py", "text/python");
        ALLOWED_FILE_TYPES.put("js", "text/javascript");
        ALLOWED_FILE_TYPES.put("html", "text/html");
        ALLOWED_FILE_TYPES.put("css", "text/css");
        ALLOWED_FILE_TYPES.put("xml", "text/xml");
        ALLOWED_FILE_TYPES.put("json", "application/json");
        ALLOWED_FILE_TYPES.put("sql", "text/sql");
        ALLOWED_FILE_TYPES.put("sh", "text/shell");
        ALLOWED_FILE_TYPES.put("bat", "text/bat");

        // 其他
        ALLOWED_FILE_TYPES.put("exe", "application/octet-stream");
        ALLOWED_FILE_TYPES.put("dll", "application/octet-stream");
        ALLOWED_FILE_TYPES.put("so", "application/octet-stream");
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名（小写）
     */
    public static String getExtension(String filename) {
        if (StrUtil.isBlank(filename)) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 获取文件名（不含扩展名）
     *
     * @param filename 文件名
     * @return 文件名（不含扩展名）
     */
    public static String getBaseName(String filename) {
        if (StrUtil.isBlank(filename)) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1) {
            return filename;
        }
        return filename.substring(0, dotIndex);
    }

    /**
     * 获取文件MIME类型
     *
     * @param extension 文件扩展名
     * @return MIME类型
     */
    public static String getMimeType(String extension) {
        if (StrUtil.isBlank(extension)) {
            return "application/octet-stream";
        }
        return ALLOWED_FILE_TYPES.getOrDefault(extension.toLowerCase(), "application/octet-stream");
    }

    /**
     * 判断文件类型是否允许
     *
     * @param extension 文件扩展名
     * @return 是否允许
     */
    public static boolean isAllowedType(String extension) {
        if (StrUtil.isBlank(extension)) {
            return false;
        }
        return ALLOWED_FILE_TYPES.containsKey(extension.toLowerCase());
    }

    /**
     * 根据输入流获取文件类型
     *
     * @param inputStream 输入流
     * @return 文件类型
     */
    public static String getTypeByStream(InputStream inputStream) {
        try {
            return FileTypeUtil.getType(inputStream);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 格式化文件大小
     *
     * @param size 文件大小（字节）
     * @return 格式化后的大小
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 生成唯一文件名
     *
     * @param originalFilename 原始文件名
     * @return 唯一文件名
     */
    public static String generateUniqueFilename(String originalFilename) {
        String extension = getExtension(originalFilename);
        String uuid = cn.hutool.core.util.IdUtil.fastSimpleUUID();
        if (StrUtil.isNotBlank(extension)) {
            return uuid + "." + extension;
        }
        return uuid;
    }

    /**
     * 生成存储路径
     *
     * @param basePath 基础路径
     * @param filename 文件名
     * @return 存储路径
     */
    public static String generateStoragePath(String basePath, String filename) {
        String datePath = cn.hutool.core.date.DateUtil.format(cn.hutool.core.date.DateUtil.date(), "yyyy/MM/dd");
        return basePath + "/" + datePath + "/" + filename;
    }
}
