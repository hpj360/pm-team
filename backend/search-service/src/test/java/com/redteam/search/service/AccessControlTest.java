package com.redteam.search.service;

import com.redteam.common.exception.BusinessException;
import com.redteam.common.mapper.AccessControlMapper;
import com.redteam.common.result.ResultCode;
import com.redteam.common.service.AccessControlService;
import com.redteam.common.service.impl.AccessControlServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分级访问控制单元测试
 *
 * <p>使用 Mockito mock {@link AccessControlMapper}，验证 {@link AccessControlServiceImpl} 的
 * 密级校验、管理员绕过、密级设置与许可等级设置能力。</p>
 *
 * <p>测试用例：</p>
 * <ul>
 *   <li>testCheckAccess_Allowed — 用户等级 ≥ 文件密级，放行</li>
 *   <li>testCheckAccess_Denied — 用户等级 &lt; 文件密级，拒绝</li>
 *   <li>testCheckAccess_AdminBypass — 管理员（99）绕过所有密级校验</li>
 *   <li>testCheckAccess_DefaultPublic — 文件无密级时按 PUBLIC 处理（兼容性）</li>
 *   <li>testSetClassification — 设置文件密级</li>
 *   <li>testSetClassification_Illegal — 非法密级编码抛异常</li>
 *   <li>testSetClearance — 设置用户许可等级</li>
 *   <li>testSetClearance_Illegal — 非法许可等级抛异常</li>
 *   <li>testRequireAccess_Throw — 无权访问时抛 BusinessException(403)</li>
 * </ul>
 *
 * @author 红方团队
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessControlTest {

    @Mock
    private AccessControlMapper accessControlMapper;

    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        accessControlService = new AccessControlServiceImpl(accessControlMapper);
    }

    // ==================== checkAccess ====================

    /**
     * 用例1：用户许可等级 ≥ 文件密级，放行
     */
    @Test
    @DisplayName("放行：用户等级≥文件密级（CONFIDENTIAL 文件，等级3用户）")
    void testCheckAccess_Allowed() {
        Long fileId = 1001L;
        Long userId = 2001L;
        when(accessControlMapper.selectFileClassification(eq(fileId))).thenReturn("CONFIDENTIAL");
        when(accessControlMapper.selectUserClearanceLevel(eq(userId))).thenReturn(3);

        Map<String, Object> result = accessControlService.checkAccess(fileId, userId);

        assertEquals(Boolean.TRUE, result.get("allowed"), "许可等级3应可访问 CONFIDENTIAL 文件");
        assertEquals("CONFIDENTIAL", result.get("fileClassification"));
        assertEquals(3, result.get("userClearance"));
    }

    /**
     * 用例2：用户许可等级 &lt; 文件密级，拒绝
     */
    @Test
    @DisplayName("拒绝：用户等级<文件密级（SECRET 文件，等级2用户）")
    void testCheckAccess_Denied() {
        Long fileId = 1002L;
        Long userId = 2002L;
        when(accessControlMapper.selectFileClassification(eq(fileId))).thenReturn("SECRET");
        when(accessControlMapper.selectUserClearanceLevel(eq(userId))).thenReturn(2);

        Map<String, Object> result = accessControlService.checkAccess(fileId, userId);

        assertEquals(Boolean.FALSE, result.get("allowed"), "许可等级2不可访问 SECRET 文件");
        assertEquals("SECRET", result.get("fileClassification"));
        assertEquals(2, result.get("userClearance"));
    }

    /**
     * 用例3：管理员绕过所有密级校验
     */
    @Test
    @DisplayName("管理员绕过：许可等级99可访问任意密级文件")
    void testCheckAccess_AdminBypass() {
        Long fileId = 1003L;
        Long userId = 2003L;
        when(accessControlMapper.selectFileClassification(eq(fileId))).thenReturn("SECRET");
        when(accessControlMapper.selectUserClearanceLevel(eq(userId))).thenReturn(99);

        Map<String, Object> result = accessControlService.checkAccess(fileId, userId);

        assertEquals(Boolean.TRUE, result.get("allowed"), "管理员应绕过密级校验");
        assertEquals("SECRET", result.get("fileClassification"));
        assertEquals(99, result.get("userClearance"));
        assertEquals("管理员绕过密级校验", result.get("reason"));
    }

    /**
     * 用例4：文件无密级记录时按 PUBLIC 处理（兼容性，所有用户可访问）
     */
    @Test
    @DisplayName("兼容性：文件密级为null时按PUBLIC处理，等级1用户可访问")
    void testCheckAccess_DefaultPublic() {
        Long fileId = 1004L;
        Long userId = 2004L;
        when(accessControlMapper.selectFileClassification(eq(fileId))).thenReturn(null);
        when(accessControlMapper.selectUserClearanceLevel(eq(userId))).thenReturn(1);

        Map<String, Object> result = accessControlService.checkAccess(fileId, userId);

        assertEquals(Boolean.TRUE, result.get("allowed"), "PUBLIC 文件应允许所有用户访问");
        assertEquals("PUBLIC", result.get("fileClassification"), "null 密级应回退为 PUBLIC");
        assertEquals(1, result.get("userClearance"));
    }

    // ==================== setFileClassification ====================

    /**
     * 用例5：设置文件密级
     */
    @Test
    @DisplayName("设置文件密级：合法编码SECRET，更新成功")
    void testSetClassification() {
        Long fileId = 1005L;
        when(accessControlMapper.updateFileClassification(eq(fileId), eq("SECRET"))).thenReturn(1);

        accessControlService.setFileClassification(fileId, "SECRET");

        verify(accessControlMapper).updateFileClassification(eq(fileId), eq("SECRET"));
    }

    /**
     * 用例6：非法密级编码抛 PARAM_ERROR
     */
    @Test
    @DisplayName("设置文件密级：非法编码抛 BusinessException")
    void testSetClassification_Illegal() {
        Long fileId = 1006L;
        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControlService.setFileClassification(fileId, "TOP_SECRET"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    /**
     * 用例7：文件不存在抛 NOT_FOUND
     */
    @Test
    @DisplayName("设置文件密级：文件不存在抛 BusinessException")
    void testSetClassification_NotFound() {
        Long fileId = 1007L;
        when(accessControlMapper.updateFileClassification(eq(fileId), eq("INTERNAL"))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControlService.setFileClassification(fileId, "INTERNAL"));
        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== setUserClearance ====================

    /**
     * 用例8：设置用户许可等级
     */
    @Test
    @DisplayName("设置用户许可等级：合法值4，更新成功")
    void testSetClearance() {
        Long userId = 2008L;
        when(accessControlMapper.updateUserClearanceLevel(eq(userId), eq(4))).thenReturn(1);

        accessControlService.setUserClearance(userId, 4);

        verify(accessControlMapper).updateUserClearanceLevel(eq(userId), eq(4));
    }

    /**
     * 用例9：非法许可等级抛 PARAM_ERROR
     */
    @Test
    @DisplayName("设置用户许可等级：非法值5抛 BusinessException")
    void testSetClearance_Illegal() {
        Long userId = 2009L;
        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControlService.setUserClearance(userId, 5));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    /**
     * 用例10：管理员许可等级99合法
     */
    @Test
    @DisplayName("设置用户许可等级：99(管理员)合法，更新成功")
    void testSetClearance_Admin() {
        Long userId = 2010L;
        when(accessControlMapper.updateUserClearanceLevel(eq(userId), eq(99))).thenReturn(1);

        accessControlService.setUserClearance(userId, 99);

        verify(accessControlMapper).updateUserClearanceLevel(eq(userId), eq(99));
    }

    // ==================== requireAccess ====================

    /**
     * 用例11：无权访问时抛 BusinessException(403)
     */
    @Test
    @DisplayName("requireAccess：无权访问时抛 FORBIDDEN 异常")
    void testRequireAccess_Throw() {
        Long fileId = 1011L;
        Long userId = 2011L;
        when(accessControlMapper.selectFileClassification(eq(fileId))).thenReturn("SECRET");
        when(accessControlMapper.selectUserClearanceLevel(eq(userId))).thenReturn(1);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> accessControlService.requireAccess(fileId, userId));
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("无权访问"), "异常消息应提示无权访问");
    }

    /**
     * 用例12：requireAccess 通过时不抛异常
     */
    @Test
    @DisplayName("requireAccess：有权限时不抛异常")
    void testRequireAccess_Pass() {
        Long fileId = 1012L;
        Long userId = 2012L;
        when(accessControlMapper.selectFileClassification(eq(fileId))).thenReturn("PUBLIC");
        when(accessControlMapper.selectUserClearanceLevel(eq(userId))).thenReturn(2);

        // 不应抛出异常
        accessControlService.requireAccess(fileId, userId);
    }

    // ==================== getter 方法 ====================

    /**
     * 用例13：getFileClassification 文件不存在返回 null
     */
    @Test
    @DisplayName("getFileClassification：文件不存在返回 null")
    void testGetFileClassification_NotFound() {
        Long fileId = 1013L;
        when(accessControlMapper.selectFileClassification(eq(fileId))).thenReturn(null);
        assertNull(accessControlService.getFileClassification(fileId));
    }

    /**
     * 用例14：getUserClearance userId 为 null 返回 null
     */
    @Test
    @DisplayName("getUserClearance：userId为null返回 null")
    void testGetUserClearance_NullUserId() {
        assertNull(accessControlService.getUserClearance(null));
    }
}
