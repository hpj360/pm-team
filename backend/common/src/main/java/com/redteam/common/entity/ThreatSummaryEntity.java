package com.redteam.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 威胁摘要实体类
 *
 * <p>对应数据库表 {@code ai_threat_summary}，存储基于 LLM 生成的文件威胁分析摘要，
 * 包含摘要文本、关键发现、建议行动、所用模型、Token 消耗及生成状态等。</p>
 *
 * <p>状态码：0-生成中 1-成功 2-失败。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("ai_threat_summary")
public class ThreatSummaryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（数据库自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * LLM 生成的威胁摘要
     */
    private String summary;

    /**
     * 关键发现 JSON 数组
     */
    private String keyFindings;

    /**
     * 建议行动 JSON 数组
     */
    private String suggestedActions;

    /**
     * 使用的 LLM 模型
     */
    private String model;

    /**
     * 消耗 token 数
     */
    private Integer tokensUsed;

    /**
     * 状态：0-生成中 1-成功 2-失败
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
