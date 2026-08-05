package com.redteam.profile.neo4j.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Neo4j 漏洞节点
 *
 * <p>存储漏洞信息（CVE 编号、严重等级等），在 Neo4j 中以 {@code Vuln} 标签存储。
 * 漏洞节点是关系图谱的叶子节点，不持有出向关系。</p>
 *
 * @author 红方团队
 */
@Data
@Node("Vuln")
public class VulnNode {

    /**
     * 漏洞ID
     */
    @Id
    private Long id;

    /**
     * CVE 编号
     */
    private String cveId;

    /**
     * 严重等级（LOW/MEDIUM/HIGH/CRITICAL）
     */
    private String severity;
}
