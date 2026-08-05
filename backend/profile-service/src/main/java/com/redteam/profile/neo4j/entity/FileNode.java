package com.redteam.profile.neo4j.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j 文件节点
 *
 * <p>对应 upload-service 中 t_file 表的记录，在 Neo4j 中以 {@code File} 标签存储。
 * 文件节点通过 CONTAINS 关联其包含的 IOC 节点。</p>
 *
 * @author 红方团队
 */
@Data
@Node("File")
public class FileNode {

    /**
     * 文件ID（与 PostgreSQL t_file.id 一致）
     */
    @Id
    private Long id;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型（扩展名）
     */
    private String fileType;

    /**
     * 文件哈希（SM3）
     */
    private String hash;

    /**
     * 文件包含的 IOC 列表（File -[:CONTAINS]-> Ioc）
     */
    @Relationship(type = "CONTAINS", direction = Relationship.Direction.OUTGOING)
    private List<IocNode> iocs = new ArrayList<>();
}
