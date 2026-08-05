package com.redteam.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 搜索模板实体类
 *
 * <p>对应数据库表 {@code search_template}，存储用户保存的搜索条件模板，
 * params_json 由前端负责序列化/反序列化，后端原样存储。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("search_template")
public class SearchTemplateEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 搜索条件JSON（关键词/模式/布尔条件/标签等）
     */
    private String paramsJson;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
