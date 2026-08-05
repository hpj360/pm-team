package com.redteam.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 攻击链推理结果实体类
 *
 * <p>对应数据库表 ai_attack_chain，记录基于 NER 实体、标签和关系图谱
 * 由 LLM 推理得到的攻击链路径、置信度及推理过程。</p>
 *
 * @author 红方团队
 */
@Data
@TableName("ai_attack_chain")
public class AttackChainEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID（自增）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件ID
     */
    private Long fileId;

    /**
     * 推理的攻击路径 JSON 数组
     */
    private String attackPaths;

    /**
     * 置信度 HIGH/MEDIUM/LOW
     */
    private String confidence;

    /**
     * 推理过程
     */
    private String reasoning;

    /**
     * 使用的模型名称
     */
    private String model;

    /**
     * 消耗的 token 数
     */
    private Integer tokensUsed;

    /**
     * 状态：0-生成中 1-成功 2-失败
     */
    private Integer status;

    /**
     * 错误信息
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
