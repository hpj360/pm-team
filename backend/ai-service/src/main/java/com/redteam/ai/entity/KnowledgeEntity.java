package com.redteam.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档实体类
 *
 * <p>对应数据库表 ai_knowledge，记录 RAG 知识库中的文档元信息。
 * 向量索引存储在 Milvus 中，本表存储文档原文与元数据。</p>
 *
 * @author 红方团队
 */
@Data
@TableName("ai_knowledge")
public class KnowledgeEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 知识ID（UUID 字符串，主键）
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String knowledgeId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 来源（ATT&CK / CVE / APT / REPORT 等）
     */
    private String source;

    /**
     * 元数据 JSON（如 ATT&CK ID、CVE ID、APT 组织名等）
     */
    private String metadataJson;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
