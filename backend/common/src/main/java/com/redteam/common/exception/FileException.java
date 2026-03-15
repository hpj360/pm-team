package com.redteam.common.exception;

import com.redteam.common.result.ResultCode;

/**
 * 文件异常
 *
 * @author 红方团队
 */
public class FileException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public FileException(String message) {
        super(ResultCode.FILE_UPLOAD_ERROR, message);
    }

    public FileException(ResultCode resultCode) {
        super(resultCode);
    }

    public FileException(ResultCode resultCode, String message) {
        super(resultCode, message);
    }

    /**
     * 文件不存在异常
     *
     * @return 文件异常
     */
    public static FileException notFound() {
        return new FileException(ResultCode.FILE_NOT_FOUND);
    }

    /**
     * 文件上传失败异常
     *
     * @param message 错误消息
     * @return 文件异常
     */
    public static FileException uploadError(String message) {
        return new FileException(ResultCode.FILE_UPLOAD_ERROR, message);
    }

    /**
     * 文件大小超限异常
     *
     * @return 文件异常
     */
    public static FileException sizeExceeded() {
        return new FileException(ResultCode.FILE_SIZE_EXCEEDED);
    }

    /**
     * 文件类型不支持异常
     *
     * @return 文件异常
     */
    public static FileException typeNotSupported() {
        return new FileException(ResultCode.FILE_TYPE_NOT_SUPPORTED);
    }

    /**
     * 文件解析失败异常
     *
     * @param message 错误消息
     * @return 文件异常
     */
    public static FileException parseError(String message) {
        return new FileException(ResultCode.FILE_PARSE_ERROR, message);
    }
}
