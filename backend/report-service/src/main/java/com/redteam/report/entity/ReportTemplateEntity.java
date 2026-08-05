package com.redteam.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报告模板实体类
 *
 * <p>对应数据库表 {@code redteam_report_templates}，维护各类报告使用的 Thymeleaf 模板信息。
 * 模板路径指向 {@code classpath:/templates/reports/} 下的 HTML 文件。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("redteam_report_templates")
public class ReportTemplateEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 模板ID（UUID，主键）
     */
    @TableId(value = "template_id", type = IdType.ASSIGN_UUID)
    private String templateId;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板类型（与 ReportEntity.reportType 对应：PENETRATION_TEST/VULNERABILITY_SCAN 等）
     */
    private String templateType;

    /**
     * Thymeleaf 模板路径（相对 classpath:/templates/reports/ 的文件名，不含后缀）
     */
    private String templatePath;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 覆盖父类 id 字段：本表以 {@link #templateId} 作为主键。
     *
     * @return 始终返回 null
     */
    @Override
    public Long getId() {
        return null;
    }

    /**
     * 屏蔽父类 id 的赋值。
     *
     * @param id 忽略入参
     */
    @Override
    public void setId(Long id) {
        // no-op：模板主键为 templateId
    }
}
