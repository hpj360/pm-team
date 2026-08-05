package com.redteam.profile.neo4j.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j 攻击链节点
 *
 * <p>存储攻击链信息，在 Neo4j 中以 {@code AttackChain} 标签存储。
 * 攻击链节点通过 TARGETS 关联目标节点，通过 EXPLOITS 关联漏洞节点。</p>
 *
 * @author 红方团队
 */
@Data
@Node("AttackChain")
public class AttackChainNode {

    /**
     * 攻击链ID
     */
    @Id
    private Long id;

    /**
     * 攻击链名称
     */
    private String name;

    /**
     * 攻击阶段（侦察/武器化/投递/利用/安装/C2/行动）
     */
    private String stage;

    /**
     * 攻击链针对的目标列表（AttackChain -[:TARGETS]-> Target）
     */
    @Relationship(type = "TARGETS", direction = Relationship.Direction.OUTGOING)
    private List<TargetNode> targets = new ArrayList<>();

    /**
     * 攻击链利用的漏洞列表（AttackChain -[:EXPLOITS]-> Vuln）
     */
    @Relationship(type = "EXPLOITS", direction = Relationship.Direction.OUTGOING)
    private List<VulnNode> vulns = new ArrayList<>();
}
