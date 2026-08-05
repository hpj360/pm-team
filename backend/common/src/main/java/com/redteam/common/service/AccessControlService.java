package com.redteam.common.service;

import java.util.Map;

/**
 * 分级访问控制服务接口
 *
 * <p>提供文件密级与用户许可等级的管理，以及访问权限校验能力。
 * 校验规则：用户许可等级（clearance_level）≥ 文件密级等级（classification.level）时允许访问；
 * 管理员（许可等级 99）绕过所有密级校验。</p>
 *
 * <p>采用 Service 层校验而非 AOP，原因：</p>
 * <ul>
 *   <li>FileEntity 位于 upload-service，UserEntity 位于 auth-service，common 模块无法直接依赖</li>
 *   <li>通过 {@code AccessControlMapper} 原生 SQL 查询避免跨模块依赖</li>
 *   <li>Service 层校验更易于测试，且不影响现有 Controller 方法签名</li>
 * </ul>
 *
 * @author 红方团队
 */
public interface AccessControlService {

    /**
     * 校验用户对文件的访问权限
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 校验结果，包含 allowed、fileClassification、userClearance、reason 字段
     */
    Map<String, Object> checkAccess(Long fileId, Long userId);

    /**
     * 校验用户对文件的访问权限，不通过时抛出 BusinessException
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @throws com.redteam.common.exception.BusinessException 当无权访问时抛出（403）
     */
    void requireAccess(Long fileId, Long userId);

    /**
     * 设置文件密级
     *
     * @param fileId         文件ID
     * @param classification 密级编码（PUBLIC/INTERNAL/CONFIDENTIAL/SECRET）
     */
    void setFileClassification(Long fileId, String classification);

    /**
     * 设置用户许可等级
     *
     * @param userId         用户ID
     * @param clearanceLevel 许可等级（1-4 或 99-管理员）
     */
    void setUserClearance(Long userId, Integer clearanceLevel);

    /**
     * 查询文件密级编码
     *
     * @param fileId 文件ID
     * @return 密级编码，文件不存在返回 null
     */
    String getFileClassification(Long fileId);

    /**
     * 查询用户许可等级
     *
     * @param userId 用户ID
     * @return 许可等级，用户不存在返回 null
     */
    Integer getUserClearance(Long userId);
}
