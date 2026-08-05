package com.redteam.analyze.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分析任务实体类
 *
 * <p>对应数据库表 t_analyze_task，记录异步分析任务的请求参数与执行状态。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_analyze_task")
public class AnalyzeTaskEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（自增），同时作为任务ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 分析类型（1-敏感信息，2-关键词，3-实体识别，4-情感分析，5-全文分析）
     */
    private Integer analyzeType;

    /**
     * 分析状态（0-待分析，1-分析中，2-已完成，3-失败）
     */
    private Integer status;

    /**
     * 分析进度（0-100）
     */
    private Integer progress;

    /**
     * 文本内容（异步任务可缓存文本，避免重复读取）
     */
    private String textContent;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 是否生成向量嵌入（0-否，1-是）
     */
    private Integer generateEmbedding;

    /**
     * 错误信息
     */
    private String errorMessage;
}
