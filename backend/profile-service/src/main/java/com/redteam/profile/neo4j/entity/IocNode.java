package com.redteam.profile.neo4j.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j IOC（威胁指标）节点
 *
 * <p>存储文件解析过程中提取的威胁指标，在 Neo4j 中以 {@code Ioc} 标签存储。
 * IOC 节点通过 EXPLOITS 关联其利用的漏洞节点。</p>
 *
 * @author 红方团队
 */
@Data
@Node("Ioc")
public class IocNode {

    /**
     * IOC ID
     */
    @Id
    private Long id;

    /**
     * IOC 值（IP、域名、哈希等）
     */
    private String iocValue;

    /**
     * IOC 类型（IP/DOMAIN/URL/HASH/EMAIL 等）
     */
    private String iocType;

    /**
     * IOC 利用的漏洞列表（Ioc -[:EXPLOITS]-> Vuln）
     */
    @Relationship(type = "EXPLOITS", direction = Relationship.Direction.OUTGOING)
    private List<VulnNode> vulns = new ArrayList<>();
}
