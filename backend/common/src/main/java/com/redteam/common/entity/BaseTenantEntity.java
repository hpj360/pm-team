package com.redteam.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 带租户的基础实体类
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseTenantEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 租户ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;
}
