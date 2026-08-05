package com.redteam.profile.neo4j.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j 目标节点
 *
 * <p>对应 PostgreSQL 中 t_target 表的记录，在 Neo4j 中以 {@code Target} 标签存储。
 * 目标节点是关系图谱的根节点，通过 CONTAINS 关系关联文件节点，
 * 通过 RELATES_TO 关系关联其他目标节点。</p>
 *
 * @author 红方团队
 */
@Data
@Node("Target")
public class TargetNode {

    /**
     * 目标ID（与 PostgreSQL t_target.id 一致）
     */
    @Id
    private Long id;

    /**
     * 目标名称
     */
    private String name;

    /**
     * 目标类型（1-个人，2-组织，3-网站，4-IP，5-域名，6-其他）
     */
    private Integer type;

    /**
     * 目标描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 目标包含的文件列表（Target -[:CONTAINS]-> File）
     */
    @Relationship(type = "CONTAINS", direction = Relationship.Direction.OUTGOING)
    private List<FileNode> files = new ArrayList<>();

    /**
     * 目标关联的其他目标（Target -[:RELATES_TO]-> Target）
     */
    @Relationship(type = "RELATES_TO", direction = Relationship.Direction.OUTGOING)
    private List<TargetNode> relatedTargets = new ArrayList<>();
}
