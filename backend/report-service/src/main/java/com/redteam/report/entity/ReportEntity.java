package com.redteam.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 报告实体类
 *
 * <p>对应数据库表 {@code redteam_reports}，记录每份报告的生成请求、文件位置及状态。
 * 主键使用 UUID 字符串（{@link IdType#ASSIGN_UUID}）。</p>
 *
 * <p>报告类型取值：</p>
 * <ul>
 *   <li>{@code PENETRATION_TEST} - 渗透测试报告</li>
 *   <li>{@code VULNERABILITY_SCAN} - 漏洞扫描报告</li>
 *   <li>{@code ATTACK_CHAIN} - 攻击链分析报告</li>
 *   <li>{@code TARGET_PROFILE} - 目标画像报告</li>
 *   <li>{@code TASK_SUMMARY} - 任务总结报告</li>
 * </ul>
 *
 * <p>状态取值：{@code PENDING} / {@code GENERATING} / {@code COMPLETED} / {@code FAILED}</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("redteam_reports")
public class ReportEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 报告ID（UUID，主键）
     */
    @TableId(value = "report_id", type = IdType.ASSIGN_UUID)
    private String reportId;

    /**
     * 报告名称
     */
    private String reportName;

    /**
     * 报告类型（PENETRATION_TEST/VULNERABILITY_SCAN/ATTACK_CHAIN/TARGET_PROFILE/TASK_SUMMARY）
     */
    private String reportType;

    /**
     * 关联任务ID
     */
    private String taskId;

    /**
     * 关联目标ID
     */
    private String targetId;

    /**
     * 使用的模板ID
     */
    private String templateId;

    /**
     * 报告格式（PDF/WORD/HTML）
     */
    private String format;

    /**
     * 报告文件在服务器上的存储路径
     */
    private String filePath;

    /**
     * 报告文件大小（字节）
     */
    private Long fileSize;

    /**
     * 报告状态（PENDING/GENERATING/COMPLETED/FAILED）
     */
    private String status;

    /**
     * 生成人ID
     */
    private Long generatedBy;

    /**
     * 生成完成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 报告摘要（生成完成后自动提取）
     */
    private String summary;

    /**
     * 元数据 JSON（扩展字段，存储报告渲染上下文等）
     */
    private String metadata;

    /**
     * 版本号（每次重新生成递增）
     */
    private Integer version;

    /**
     * 是否共享（0-否，1-是）
     */
    private Integer isShared;

    /**
     * 共享给的用户ID列表（逗号分隔）
     */
    private String sharedWith;

    /**
     * 失败原因（生成失败时记录）
     */
    private String failureReason;

    /**
     * 覆盖父类 id 字段：本表以 {@link #reportId} 作为主键，父类 id 字段不参与入库映射。
     *
     * @return 始终返回 null
     */
    @Override
    public Long getId() {
        return null;
    }

    /**
     * 屏蔽父类 id 的赋值（主键已由 {@link #reportId} 承担）。
     *
     * @param id 忽略入参
     */
    @Override
    public void setId(Long id) {
        // no-op：报告主键为 reportId
    }
}
