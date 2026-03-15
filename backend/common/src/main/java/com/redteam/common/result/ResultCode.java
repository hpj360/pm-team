package com.redteam.common.result;

import lombok.Getter;

/**
 * 响应状态码枚举
 *
 * @author 红方团队
 */
@Getter
public enum ResultCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 失败
     */
    FAIL(500, "操作失败"),

    /**
     * 参数错误
     */
    PARAM_ERROR(400, "参数错误"),

    /**
     * 未授权
     */
    UNAUTHORIZED(401, "未授权，请先登录"),

    /**
     * 禁止访问
     */
    FORBIDDEN(403, "禁止访问，权限不足"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在"),

    /**
     * 请求方法不支持
     */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /**
     * 请求超时
     */
    REQUEST_TIMEOUT(408, "请求超时"),

    /**
     * 资源冲突
     */
    CONFLICT(409, "资源冲突"),

    /**
     * 资源已存在
     */
    RESOURCE_EXISTS(410, "资源已存在"),

    /**
     * 请求过于频繁
     */
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    /**
     * 服务器内部错误
     */
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),

    /**
     * 服务不可用
     */
    SERVICE_UNAVAILABLE(503, "服务不可用"),

    /**
     * 网关超时
     */
    GATEWAY_TIMEOUT(504, "网关超时"),

    // ========== 业务错误码 1000-1999 ==========

    /**
     * 用户名或密码错误
     */
    LOGIN_ERROR(1001, "用户名或密码错误"),

    /**
     * 用户已存在
     */
    USER_EXISTS(1002, "用户已存在"),

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(1003, "用户不存在"),

    /**
     * 密码错误
     */
    PASSWORD_ERROR(1004, "密码错误"),

    /**
     * 账号已被禁用
     */
    ACCOUNT_DISABLED(1005, "账号已被禁用"),

    /**
     * Token无效
     */
    TOKEN_INVALID(1006, "Token无效或已过期"),

    /**
     * 验证码错误
     */
    CAPTCHA_ERROR(1007, "验证码错误"),

    // ========== 文件相关错误码 2000-2999 ==========

    /**
     * 文件不存在
     */
    FILE_NOT_FOUND(2001, "文件不存在"),

    /**
     * 文件上传失败
     */
    FILE_UPLOAD_ERROR(2002, "文件上传失败"),

    /**
     * 文件大小超限
     */
    FILE_SIZE_EXCEEDED(2003, "文件大小超过限制"),

    /**
     * 文件类型不支持
     */
    FILE_TYPE_NOT_SUPPORTED(2004, "文件类型不支持"),

    /**
     * 文件解析失败
     */
    FILE_PARSE_ERROR(2005, "文件解析失败"),

    /**
     * 文件已存在
     */
    FILE_EXISTS(2006, "文件已存在"),

    /**
     * 文件下载失败
     */
    FILE_DOWNLOAD_ERROR(2007, "文件下载失败"),

    // ========== 检索相关错误码 3000-3999 ==========

    /**
     * 检索失败
     */
    SEARCH_ERROR(3001, "检索失败"),

    /**
     * 索引创建失败
     */
    INDEX_CREATE_ERROR(3002, "索引创建失败"),

    /**
     * 索引删除失败
     */
    INDEX_DELETE_ERROR(3003, "索引删除失败"),

    // ========== 分析相关错误码 4000-4999 ==========

    /**
     * 分析任务失败
     */
    ANALYZE_ERROR(4001, "分析任务失败"),

    /**
     * 分析任务不存在
     */
    ANALYZE_TASK_NOT_FOUND(4002, "分析任务不存在"),

    /**
     * 向量索引失败
     */
    VECTOR_INDEX_ERROR(4003, "向量索引失败"),

    // ========== 目标画像相关错误码 5000-5999 ==========

    /**
     * 目标不存在
     */
    TARGET_NOT_FOUND(5001, "目标不存在"),

    /**
     * 画像生成失败
     */
    PROFILE_GENERATE_ERROR(5002, "画像生成失败");

    /**
     * 响应码
     */
    private final Integer code;

    /**
     * 响应消息
     */
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
