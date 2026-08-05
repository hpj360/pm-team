package com.redteam.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redteam.common.entity.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志 Mapper
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供 {@link AuditLogEntity} 的 CRUD 能力，
 * 并扩展按多条件查询与按操作类型统计的自定义方法。</p>
 *
 * @author 红方团队
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {

    /**
     * 按多条件查询审计日志（按创建时间倒序）
     *
     * @param userId       用户ID（可空）
     * @param action       操作类型（可空）
     * @param resourceType 资源类型（可空）
     * @param startTime    开始时间（可空）
     * @param endTime      结束时间（可空）
     * @return 审计日志列表
     */
    List<AuditLogEntity> selectByConditions(@Param("userId") Long userId,
                                            @Param("action") String action,
                                            @Param("resourceType") String resourceType,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);

    /**
     * 按多条件分页查询审计日志（MyBatis-Plus 分页插件自动改写 SQL）
     *
     * @param page         分页参数
     * @param userId       用户ID（可空）
     * @param action       操作类型（可空）
     * @param resourceType 资源类型（可空）
     * @param startTime    开始时间（可空）
     * @param endTime      结束时间（可空）
     * @return 分页审计日志
     */
    IPage<AuditLogEntity> selectByConditionsPage(IPage<AuditLogEntity> page,
                                                 @Param("userId") Long userId,
                                                 @Param("action") String action,
                                                 @Param("resourceType") String resourceType,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);

    /**
     * 按操作类型统计数量
     *
     * @param action    操作类型
     * @param startTime 开始时间（可空）
     * @return 数量
     */
    Long countByAction(@Param("action") String action, @Param("startTime") LocalDateTime startTime);

    /**
     * 按操作类型分组统计（用于审计统计）
     *
     * @param startTime 开始时间（可空）
     * @param endTime   结束时间（可空）
     * @return 操作类型 -> 数量 列表，每项为 {action, cnt}
     */
    List<Map<String, Object>> countGroupByAction(@Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);
}
