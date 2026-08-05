package com.redteam.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 分级访问控制 Mapper
 *
 * <p>直接通过 SQL 查询 t_file 与 t_user 表的密级 / 许可等级字段，
 * 避免跨模块依赖（FileEntity 在 upload-service，UserEntity 在 auth-service）。</p>
 *
 * <p>注意：由于使用原生 SQL，MyBatis-Plus 的逻辑删除不会自动生效，
 * 需手动在 SQL 中添加 {@code deleted = 0} 过滤条件。</p>
 *
 * @author 红方团队
 */
@Mapper
public interface AccessControlMapper {

    /**
     * 查询文件密级编码
     *
     * @param fileId 文件ID
     * @return 密级编码（PUBLIC/INTERNAL/CONFIDENTIAL/SECRET），文件不存在返回 null
     */
    @Select("SELECT classification FROM t_file WHERE id = #{fileId} AND deleted = 0")
    String selectFileClassification(@Param("fileId") Long fileId);

    /**
     * 更新文件密级
     *
     * @param fileId         文件ID
     * @param classification 密级编码
     * @return 影响行数
     */
    @Update("UPDATE t_file SET classification = #{classification} WHERE id = #{fileId} AND deleted = 0")
    int updateFileClassification(@Param("fileId") Long fileId,
                                 @Param("classification") String classification);

    /**
     * 查询用户许可等级
     *
     * @param userId 用户ID
     * @return 许可等级（1-4 或 99-管理员），用户不存在返回 null
     */
    @Select("SELECT clearance_level FROM t_user WHERE id = #{userId} AND deleted = 0")
    Integer selectUserClearanceLevel(@Param("userId") Long userId);

    /**
     * 更新用户许可等级
     *
     * @param userId          用户ID
     * @param clearanceLevel  许可等级
     * @return 影响行数
     */
    @Update("UPDATE t_user SET clearance_level = #{clearanceLevel} WHERE id = #{userId} AND deleted = 0")
    int updateUserClearanceLevel(@Param("userId") Long userId,
                                 @Param("clearanceLevel") Integer clearanceLevel);
}
