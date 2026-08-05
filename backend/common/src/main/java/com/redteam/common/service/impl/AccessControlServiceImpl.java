package com.redteam.common.service.impl;

import cn.hutool.core.util.StrUtil;
import com.redteam.common.enums.Classification;
import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.AccessControlMapper;
import com.redteam.common.result.ResultCode;
import com.redteam.common.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分级访问控制服务实现
 *
 * <p>基于 {@link AccessControlMapper} 的原生 SQL 查询，提供文件密级 / 用户许可等级的读写
 * 与访问权限校验。校验规则：</p>
 * <ul>
 *   <li>用户许可等级 ≥ 文件密级等级 → 允许访问</li>
 *   <li>用户许可等级 = 99（管理员）→ 绕过所有密级校验</li>
 *   <li>用户许可等级 &lt; 文件密级等级 → 拒绝访问（403）</li>
 * </ul>
 *
 * <p>默认值保证兼容性：未设置密级的文件按 PUBLIC 处理，未设置许可等级的用户按 1（PUBLIC）处理。</p>
 *
 * @author 红方团队
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessControlServiceImpl implements AccessControlService {

    /**
     * 管理员许可等级（绕过所有密级校验）
     */
    private static final int ADMIN_CLEARANCE = 99;

    /**
     * 默认许可等级（用户字段为空时回退）
     */
    private static final int DEFAULT_CLEARANCE = 1;

    private final AccessControlMapper accessControlMapper;

    @Override
    public Map<String, Object> checkAccess(Long fileId, Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileId", fileId);
        result.put("userId", userId);

        // 查询文件密级
        String classificationCode = accessControlMapper.selectFileClassification(fileId);
        Classification classification = Classification.fromCode(classificationCode);
        result.put("fileClassification", classification.getCode());

        // 查询用户许可等级
        Integer clearance = accessControlMapper.selectUserClearanceLevel(userId);
        int clearanceLevel = clearance == null ? DEFAULT_CLEARANCE : clearance;
        result.put("userClearance", clearanceLevel);

        // 管理员绕过
        if (clearanceLevel >= ADMIN_CLEARANCE) {
            result.put("allowed", true);
            result.put("reason", "管理员绕过密级校验");
            return result;
        }

        // 密级校验
        boolean allowed = classification.isAccessibleBy(clearanceLevel);
        result.put("allowed", allowed);
        if (allowed) {
            result.put("reason", "许可等级满足文件密级要求");
        } else {
            result.put("reason", "许可等级不足：用户等级=" + clearanceLevel
                    + "，文件密级=" + classification.getCode() + "(" + classification.getLevel() + ")");
        }
        return result;
    }

    @Override
    public void requireAccess(Long fileId, Long userId) {
        Map<String, Object> result = checkAccess(fileId, userId);
        Boolean allowed = (Boolean) result.get("allowed");
        if (allowed == null || !allowed) {
            log.warn("文件访问被拒绝: fileId={}, userId={}, reason={}",
                    fileId, userId, result.get("reason"));
            throw new BusinessException(ResultCode.FORBIDDEN,
                    "无权访问此密级的文件");
        }
    }

    @Override
    public void setFileClassification(Long fileId, String classification) {
        if (fileId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件ID不能为空");
        }
        if (StrUtil.isBlank(classification)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "密级不能为空");
        }
        // 校验密级编码合法性
        Classification target = Classification.fromCode(classification);
        if (!target.getCode().equals(classification)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "非法密级编码： " + classification + "，合法值：PUBLIC/INTERNAL/CONFIDENTIAL/SECRET");
        }
        int rows = accessControlMapper.updateFileClassification(fileId, target.getCode());
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文件不存在: " + fileId);
        }
        log.info("文件密级已更新: fileId={}, classification={}", fileId, target.getCode());
    }

    @Override
    public void setUserClearance(Long userId, Integer clearanceLevel) {
        if (userId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "用户ID不能为空");
        }
        if (clearanceLevel == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "许可等级不能为空");
        }
        // 合法值：1-4 或 99
        if (clearanceLevel != ADMIN_CLEARANCE
                && (clearanceLevel < 1 || clearanceLevel > Classification.SECRET.getLevel())) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "非法许可等级： " + clearanceLevel + "，合法值：1-4 或 99(管理员)");
        }
        int rows = accessControlMapper.updateUserClearanceLevel(userId, clearanceLevel);
        if (rows == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在: " + userId);
        }
        log.info("用户许可等级已更新: userId={}, clearanceLevel={}", userId, clearanceLevel);
    }

    @Override
    public String getFileClassification(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return accessControlMapper.selectFileClassification(fileId);
    }

    @Override
    public Integer getUserClearance(Long userId) {
        if (userId == null) {
            return null;
        }
        return accessControlMapper.selectUserClearanceLevel(userId);
    }
}
