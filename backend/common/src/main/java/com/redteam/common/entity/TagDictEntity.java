package com.redteam.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 标签字典实体类（六层架构）
 *
 * <p>对应数据库表 tag_dict_v2，记录 L1-L6 六层标签体系字典数据。
 * 编码规范：层级.分类.名称.值，如 L1.FILE.TYPE.PDF、L3.ENTITY.IP.PUBLIC</p>
 *
 * @author 红方团队
 */
@Data
@TableName("tag_dict_v2")
public class TagDictEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 标签编码，格式：层级.分类.名称.值
     */
    private String tagCode;

    /**
     * 标签中文名
     */
    private String tagName;

    /**
     * 层级：L1-L6
     */
    private String layer;

    /**
     * 分类：FILE/BUSINESS/ENTITY/SCENE/INTEL/COMPLIANCE
     */
    private String category;

    /**
     * 值类型：ENUM/TEXT/NUMBER/BOOL/DATE
     */
    private String valueType;

    /**
     * 适用对象：FILE/ENTITY/TARGET/TASK/ALL
     */
    private String applicableObject;

    /**
     * 识别规则描述（正则/字典/模型/关联）
     */
    private String identifyRule;

    /**
     * 是否多选：0单选 1多选
     */
    private Integer isMulti;

    /**
     * 父标签编码
     */
    private String parentCode;

    /**
     * 启用：0禁用 1启用
     */
    private Integer enabled;

    /**
     * 口径定义
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
