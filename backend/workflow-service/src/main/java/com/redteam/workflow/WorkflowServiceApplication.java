package com.redteam.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 审批工作流服务启动类
 *
 * <p>负责审批工作流定义/实例/审批意见的全生命周期管理，支持三种审批模式：</p>
 * <ul>
 *   <li>SEQUENTIAL（线性）：节点内审批人按顺序逐人通过</li>
 *   <li>PARALLEL_ALL（会签）：节点内所有审批人都通过后才进入下一节点</li>
 *   <li>PARALLEL_ANY（或签）：节点内任一审批人通过即进入下一节点</li>
 * </ul>
 *
 * <ul>
 *   <li>端口：8094</li>
 *   <li>数据库：PostgreSQL (redteam_workflow)</li>
 *   <li>ORM：MyBatis-Plus</li>
 *   <li>API 文档：Knife4j</li>
 * </ul>
 *
 * @author 红方团队
 */
@SpringBootApplication
@MapperScan("com.redteam.common.mapper")
@ComponentScan(basePackages = {"com.redteam.common", "com.redteam.workflow"})
public class WorkflowServiceApplication {

    /**
     * 服务启动入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WorkflowServiceApplication.class, args);
        System.out.println("==========================================");
        System.out.println("    审批工作流服务启动成功！");
        System.out.println("    API文档地址: http://localhost:8094/api/doc.html");
        System.out.println("==========================================");
    }
}
