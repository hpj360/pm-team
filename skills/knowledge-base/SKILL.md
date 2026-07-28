---
name: "knowledge-base"
description: "团队知识库系统，收集、组织和共享项目知识与经验。在需要存储、检索、推荐项目知识，或积累成功经验与失败教训、构建知识图谱时调用。"
---

# 团队知识库系统 (Knowledge Base)

## Skill 描述

团队知识库系统是 OpenClaw 多 Agent 协作框架中的核心知识管理能力，用于收集、组织和共享项目知识和经验。通过结构化的知识存储、智能检索和关联推荐，帮助团队避免重复踩坑、复用最佳实践、加速决策过程，实现团队智慧的持续积累与传承。

---

## 功能

### 1. 知识收集 (Knowledge Collection)
- 自动收集项目各阶段（需求、设计、开发、测试、运维）的产出物和经验总结
- 支持 Agent 主动上报知识条目
- 支持从代码、文档、会议纪要中提取知识片段
- 记录知识来源、创建者、上下文环境等元数据

### 2. 知识分类 (Knowledge Classification)
- 按领域 (Domain) 分类：业务领域、技术领域
- 按项目 (Project) 分类：关联具体项目标识
- 按类型 (Type) 分类：需求、架构、设计、开发、测试、运维
- 支持多维度标签 (Tags) 体系，便于交叉检索
- 支持层级分类树，可自定义分类节点

### 3. 知识检索 (Knowledge Search)
- 全文搜索 (Full-text Search)：基于关键词的快速检索
- 语义搜索 (Semantic Search)：基于向量嵌入的相似度检索
- 组合过滤：按类型、标签、项目、时间范围、作者等多条件过滤
- 支持模糊匹配和拼写纠错
- 返回结果按相关度评分排序

### 4. 知识推荐 (Knowledge Recommendation)
- 基于当前任务上下文推荐相关知识
- 基于 Agent 角色（如 backend-developer、frontend-developer）推荐领域知识
- 基于知识图谱的关联推荐
- 基于使用频率和时效性的热度推荐
- 支持个性化推荐（根据历史使用记录）

### 5. 经验积累 (Experience Accumulation)
- 记录成功经验 (Best Practices)：可复用的解决方案和模式
- 记录失败教训 (Lessons Learned)：踩坑记录和避坑指南
- 记录决策依据 (Decision Records)：架构决策记录 (ADR)
- 支持经验复盘和周期性回顾
- 经验条目可关联具体场景和适用条件

### 6. 知识图谱 (Knowledge Graph)
- 构建知识节点之间的关联关系（引用、依赖、衍生、冲突等）
- 自动识别知识间的语义关联
- 支持图谱可视化查询和遍历
- 识别知识孤岛和知识冲突
- 支持基于图谱的推理和发现

---

## 知识类型

| 类型 | 标识 | 说明 | 示例 |
|------|------|------|------|
| 需求知识 | `requirement` | 需求模式、用户故事模板、需求分析方法 | 用户故事模板、PRD规范 |
| 架构知识 | `architecture` | 架构模式、技术选型经验、系统设计原则 | 微服务架构模式、技术选型对比 |
| 设计知识 | `design` | 设计规范、UI组件库、交互模式 | 设计系统、组件API规范 |
| 开发知识 | `development` | 代码模板、最佳实践、编码规范 | 代码生成模板、命名规范 |
| 测试知识 | `testing` | 测试策略、测试用例库、测试工具用法 | 测试金字塔、E2E用例库 |
| 运维知识 | `operations` | 部署方案、运维手册、监控告警配置 | CI/CD流水线、故障排查手册 |

---

## 使用示例

### 存储知识

```json
{
  "action": "store",
  "knowledge": {
    "type": "architecture",
    "title": "微服务架构模式",
    "content": "微服务架构将应用拆分为一组小型、自治的服务，每个服务独立部署、独立扩展...",
    "tags": ["microservice", "architecture"],
    "project": "pm-team",
    "author": "system-architect",
    "source": "architecture-review-2026-07"
  }
}
```

### 检索知识

```json
{
  "action": "search",
  "query": "用户登录API设计",
  "type": "development",
  "limit": 10
}
```

### 推荐知识

```json
{
  "action": "recommend",
  "context": {
    "task": "api-development",
    "agent": "backend-developer"
  }
}
```

### 更新知识

```json
{
  "action": "update",
  "id": "kb-001",
  "content": "...",
  "version": "2.0"
}
```

---

## 知识存储格式

知识条目采用 JSON 结构化存储，包含元数据、内容主体和关联信息：

```json
{
  "knowledge": {
    "id": "kb-001",
    "type": "architecture",
    "title": "微服务架构模式",
    "content": "知识正文内容...",
    "summary": "知识摘要，用于快速预览和检索展示",
    "tags": ["microservice", "architecture", "service-split"],
    "project": "pm-team",
    "domain": "backend",
    "author": {
      "agent": "system-architect",
      "role": "architect"
    },
    "source": {
      "type": "architecture-review",
      "reference": "architecture-review-2026-07"
    },
    "context": {
      "applicable_scenarios": ["大型分布式系统", "独立扩展需求"],
      "prerequisites": ["容器化基础", "服务治理能力"],
      "constraints": ["团队规模需足够", "运维复杂度增加"]
    },
    "metadata": {
      "created_at": "2026-07-26T10:00:00+08:00",
      "updated_at": "2026-07-26T10:00:00+08:00",
      "version": "1.0",
      "status": "active",
      "confidence": 0.95,
      "usage_count": 0,
      "rating": 0
    },
    "relations": [
      {
        "type": "depends-on",
        "target_id": "kb-002",
        "target_title": "服务注册与发现"
      },
      {
        "type": "related-to",
        "target_id": "kb-005",
        "target_title": "API网关设计"
      }
    ]
  }
}
```

### 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 知识唯一标识，格式 `kb-XXXXX` |
| `type` | string | 是 | 知识类型（requirement/architecture/design/development/testing/operations） |
| `title` | string | 是 | 知识标题 |
| `content` | string | 是 | 知识正文 |
| `summary` | string | 否 | 知识摘要 |
| `tags` | string[] | 否 | 标签数组 |
| `project` | string | 否 | 关联项目标识 |
| `domain` | string | 否 | 业务/技术领域 |
| `author` | object | 是 | 创建者信息 |
| `source` | object | 否 | 知识来源 |
| `context` | object | 否 | 适用上下文与约束 |
| `metadata` | object | 是 | 元数据 |
| `relations` | object[] | 否 | 关联关系 |

---

## 知识图谱结构

知识图谱由节点 (Nodes) 和边 (Edges) 组成，描述知识条目之间的关联关系。

### 节点 (Node)

```json
{
  "node": {
    "id": "kb-001",
    "type": "knowledge",
    "label": "微服务架构模式",
    "properties": {
      "knowledge_type": "architecture",
      "tags": ["microservice", "architecture"],
      "confidence": 0.95,
      "status": "active"
    }
  }
}
```

### 边 (Edge)

```json
{
  "edge": {
    "id": "edge-001",
    "source": "kb-001",
    "target": "kb-002",
    "type": "depends-on",
    "weight": 0.8,
    "properties": {
      "description": "微服务架构依赖服务注册与发现机制",
      "created_at": "2026-07-26T10:00:00+08:00"
    }
  }
}
```

### 关系类型 (Edge Types)

| 关系类型 | 标识 | 说明 | 示例 |
|----------|------|------|------|
| 依赖 | `depends-on` | A 的实现依赖 B | 微服务架构 → 服务注册发现 |
| 引用 | `references` | A 引用了 B 的内容 | 架构文档 → 设计规范 |
| 衍生 | `derived-from` | A 由 B 衍生而来 | 测试用例 → 需求文档 |
| 相关 | `related-to` | A 与 B 主题相关 | API网关 → 负载均衡 |
| 冲突 | `conflicts-with` | A 与 B 存在矛盾 | 方案A → 方案B |
| 替代 | `replaces` | A 替代了 B | 新方案 → 旧方案 |
| 组成 | `part-of` | A 是 B 的组成部分 | 组件 → 设计系统 |
| 解决 | `solves` | A 解决了 B 描述的问题 | 解决方案 → 问题记录 |

### 图谱查询示例

```json
{
  "action": "graph_query",
  "query": {
    "start_node": "kb-001",
    "traversal": "bfs",
    "max_depth": 3,
    "edge_types": ["depends-on", "related-to"],
    "filters": {
      "knowledge_type": ["architecture", "development"]
    }
  }
}
```

---

## 版本管理机制

知识库采用语义化版本 (Semantic Versioning) 进行版本管理，确保知识演进可追溯、可回滚。

### 版本号规则

版本号格式：`MAJOR.MINOR.PATCH`

| 版本段 | 触发条件 | 示例 |
|--------|----------|------|
| `MAJOR` | 知识核心结论或方案发生根本性变更，与旧版本不兼容 | `1.0.0` → `2.0.0` |
| `MINOR` | 新增补充内容、扩展适用场景，向后兼容 | `1.0.0` → `1.1.0` |
| `PATCH` | 修正错误、优化表述、补充细节，向后兼容 | `1.0.0` → `1.0.1` |

### 版本记录结构

```json
{
  "version_record": {
    "knowledge_id": "kb-001",
    "current_version": "2.0.0",
    "history": [
      {
        "version": "1.0.0",
        "timestamp": "2026-07-26T10:00:00+08:00",
        "author": "system-architect",
        "change_type": "initial",
        "change_summary": "初始创建微服务架构模式知识条目"
      },
      {
        "version": "1.1.0",
        "timestamp": "2026-08-15T14:30:00+08:00",
        "author": "system-architect",
        "change_type": "minor",
        "change_summary": "补充服务网格 (Service Mesh) 适用场景"
      },
      {
        "version": "2.0.0",
        "timestamp": "2026-10-01T09:00:00+08:00",
        "author": "system-architect",
        "change_type": "major",
        "change_summary": "重构架构建议，从单体优先调整为微服务优先策略",
        "breaking_change": true
      }
    ],
    "rollback_supported": true
  }
}
```

### 版本操作

- **查询历史**：获取指定知识条目的完整版本历史
- **版本对比**：对比两个版本之间的差异
- **版本回滚**：将知识条目回滚到指定历史版本
- **版本锁定**：对关键知识条目锁定版本，防止误修改
- **变更审计**：记录所有版本变更操作，支持审计追溯

### 版本回滚示例

```json
{
  "action": "rollback",
  "id": "kb-001",
  "target_version": "1.1.0",
  "reason": "2.0.0 版本结论经验证不准确，回滚至上一稳定版本"
}
```
