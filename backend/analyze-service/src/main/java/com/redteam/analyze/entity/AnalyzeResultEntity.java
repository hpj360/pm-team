package com.redteam.analyze.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.redteam.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 分析结果实体类
 *
 * <p>对应数据库表 t_analyze_result，记录每次文件分析的任务状态与结果 JSON。</p>
 *
 * @author 红方团队
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_analyze_result")
public class AnalyzeResultEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分析任务ID（关联 t_analyze_task.id）
     */
    private Long taskId;

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
     * 分析结果 JSON
     */
    private String resultJson;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 分析耗时（毫秒）
     */
    private Long duration;

    /**
     * 完成时间
     */
    private LocalDateTime finishTime;
}
