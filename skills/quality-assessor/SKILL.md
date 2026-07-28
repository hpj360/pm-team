# Skill: Quality Assessor

## 描述

质量评估系统，自动评估各 Agent 输出的质量并给出改进建议。适用于 OpenClaw 多 Agent 协作框架中各类交付物（文档、代码、设计、测试）的多维度质量评估、量化评分、基线管理、趋势追踪与改进建议生成，为 Director Agent 进行质量门禁判定和持续质量优化提供数据支撑。

## 功能

### 1. 多维度质量评估

针对不同类型的交付物，从多个维度进行结构化评估。

**评估维度**:
- 完整性 - 内容是否覆盖全部需求点与边界场景
- 准确性 - 信息是否正确、可验证、与事实一致
- 规范性 - 是否符合团队规范、编码标准与模板要求
- 可读性 - 结构是否清晰、表达是否易于理解

**支持的评估类型**:
- 文档质量评估
- 代码质量评估
- 设计质量评估
- 测试质量评估

### 2. 自动评分机制

基于评估维度权重，输出 0-100 分的量化评分。

**评分特性**:
- 按维度独立打分后加权汇总
- 支持自定义维度权重配置
- 支持严重问题一票否决（critical issue 触发评分上限封顶）
- 评分结果映射到 A/B/C/D/F 五级质量等级
- 评分明细可追溯，记录每个扣分项的依据

### 3. 质量基线管理

设定和维护各类型交付物的质量标准，作为质量门禁的判定依据。

**基线能力**:
- 按 `type`（如 prd / design / code / test）设置最低通过分数
- 按 Agent 或项目阶段设置差异化基线
- 基线版本化管理，支持回溯历史基线
- 基线达成率统计
- 基线变更审批记录

### 4. 质量趋势分析

跟踪质量指标随时间的变化趋势，识别质量风险与改进方向。

**分析维度**:
- 按 Agent 维度统计平均分变化趋势
- 按交付物类型统计质量波动
- 按时间窗口（日 / 周 / 月）聚合质量指标
- 识别质量下降的 Agent 或类型并预警
- 计算质量稳定性指标（标准差、波动率）

### 5. 改进建议生成

基于评估结果自动生成可执行的改进建议。

**建议生成策略**:
- 针对低分维度给出针对性建议
- 按问题严重程度排序（critical → major → minor）
- 关联历史同类问题的修复经验
- 给出建议优先级与预估修复成本
- 提供可复用的修复模板或参考链接

### 6. 质量报告生成

生成结构化的质量评估报告，支持多种输出格式。

**报告类型**:
- `single` - 单次评估报告
- `agent` - 按 Agent 维度汇总报告
- `weekly` / `monthly` - 周期性质量报告
- `baseline` - 基线达成情况报告
- `trend` - 质量趋势分析报告

**输出格式**: `markdown` / `html` / `json`

## 评估维度

### 文档质量

适用于 PRD、设计文档、技术方案、用户手册等文档类交付物。

| 维度 | 权重（默认） | 评估要点 |
|------|--------------|----------|
| 完整性 | 30% | 需求点覆盖、边界场景说明、异常流程描述、依赖项识别 |
| 准确性 | 30% | 信息正确性、数据可验证性、与既有事实一致性、版本同步 |
| 规范性 | 20% | 模板遵循、术语统一、格式规范、版本号管理 |
| 可读性 | 20% | 结构清晰度、章节层级、图文配合、语言流畅度 |

### 代码质量

适用于前后端代码、脚本、配置文件等代码类交付物。

| 维度 | 权重（默认） | 评估要点 |
|------|--------------|----------|
| 可读性 | 25% | 命名规范、注释覆盖率、函数长度、代码复杂度 |
| 可维护性 | 25% | 模块解耦、单一职责、重复代码率、扩展性 |
| 性能 | 25% | 算法复杂度、资源占用、N+1 查询、内存泄漏风险 |
| 安全性 | 25% | 注入风险、敏感数据处理、权限校验、依赖漏洞 |

### 设计质量

适用于 UI 设计稿、交互方案、原型图、视觉规范等设计类交付物。

| 维度 | 权重（默认） | 评估要点 |
|------|--------------|----------|
| 用户体验 | 40% | 操作流畅度、信息架构、可用性、无障碍性 |
| 视觉一致性 | 30% | 设计规范遵循、色彩统一、字体层级、组件复用 |
| 可实现性 | 30% | 技术可行性、开发成本、跨端兼容、性能预期 |

### 测试质量

适用于测试用例、测试报告、自动化测试脚本等测试类交付物。

| 维度 | 权重（默认） | 评估要点 |
|------|--------------|----------|
| 覆盖率 | 35% | 需求覆盖、路径覆盖、分支覆盖、语句覆盖 |
| 有效性 | 35% | 断言合理性、用例独立性、缺陷发现率、误报率 |
| 边界情况 | 30% | 极值测试、异常输入、并发场景、兼容性场景 |

## 使用示例

### 评估文档

```json
{
  "action": "assess",
  "type": "document",
  "content": "...",
  "criteria": {
    "completeness": 30,
    "accuracy": 30,
    "readability": 20,
    "standards": 20
  }
}
```

### 评估代码

```json
{
  "action": "assess",
  "type": "code",
  "content": "...",
  "language": "java"
}
```

### 设定基线

```json
{
  "action": "setBaseline",
  "type": "prd",
  "minScore": 85
}
```

### 生成报告

```json
{
  "action": "report",
  "period": "weekly",
  "format": "html"
}
```

### 评估设计稿

```json
{
  "action": "assess",
  "type": "design",
  "content": "...",
  "criteria": {
    "ux": 40,
    "consistency": 30,
    "feasibility": 30
  }
}
```

### 评估测试用例

```json
{
  "action": "assess",
  "type": "test",
  "content": "...",
  "criteria": {
    "coverage": 35,
    "effectiveness": 35,
    "boundary": 30
  }
}
```

### 查询质量趋势

```json
{
  "action": "trend",
  "agent": "backend-developer",
  "window": "monthly",
  "period": "2026-01-01/2026-07-26"
}
```

### 查询基线达成情况

```json
{
  "action": "baseline",
  "type": "code",
  "period": "2026-07-01/2026-07-26"
}
```

## 评分规则

### 评分计算公式

```
最终得分 = Σ (维度得分 × 维度权重)
```

- 每个维度得分取值 0-100
- 维度权重总和应为 100（百分比）
- 若权重未指定，使用各类型默认权重
- 若触发一票否决项，最终得分不超过 60

### 维度评分标准

| 维度得分区间 | 评级 | 说明 |
|--------------|------|------|
| 90-100 | 优秀 | 该维度表现卓越，无明显改进空间 |
| 80-89 | 良好 | 该维度表现达标，存在少量优化点 |
| 70-79 | 合格 | 该维度基本达标，存在明显改进空间 |
| 60-69 | 待改进 | 该维度不达标，存在较多问题 |
| 0-59 | 不合格 | 该维度严重不达标，需返工 |

### 扣分规则

按问题严重程度扣分，单维度扣分上限为 100 分：

| 问题严重程度 | 单项扣分 | 说明 |
|--------------|----------|------|
| critical | 20-30 | 阻断性问题，必须立即修复（如安全漏洞、需求遗漏） |
| major | 10-15 | 重要问题，影响交付质量（如逻辑错误、规范违反） |
| minor | 3-5 | 次要问题，建议修复（如命名不规范、注释缺失） |
| info | 0 | 提示性信息，不扣分（如风格建议） |

### 一票否决项

出现以下情况时，最终得分不超过 60 分（即质量等级不高于 D 级）：

- 文档类：核心需求遗漏、关键信息错误、与既有规范严重冲突
- 代码类：存在 critical 级安全漏洞、可能导致数据丢失的缺陷
- 设计类：核心流程不可用、严重违反品牌规范
- 测试类：核心路径未覆盖、存在系统性测试盲区

## 质量等级

最终得分映射到 A/B/C/D/F 五级质量等级：

| 等级 | 得分区间 | 含义 | 处理建议 |
|------|----------|------|----------|
| A | 90-100 | 优秀 | 直接通过，可作为标杆案例 |
| B | 80-89 | 良好 | 通过，建议优化 minor 问题 |
| C | 70-79 | 合格 | 有条件通过，需修复 major 问题后复审 |
| D | 60-69 | 待改进 | 不通过，需修复全部 major 问题后重新提交 |
| F | 0-59 | 不合格 | 不通过，需返工重做并重新评估 |

### 基线达成判定

- 得分 ≥ 基线 `minScore` → 判定为通过
- 得分 < 基线 `minScore` → 判定为不通过，触发改进流程
- 默认基线：文档类 80、代码类 85、设计类 75、测试类 80
- 未设置基线时，默认以 D 级（60 分）为通过线

## 评估报告格式

### 单次评估报告 (JSON)

```json
{
  "assessmentId": "assess-20260726-001",
  "type": "document",
  "target": "docs/prd.md",
  "agent": "requirement-analyst",
  "score": 87,
  "grade": "B",
  "passed": true,
  "baseline": {
    "type": "prd",
    "minScore": 85,
    "achieved": true
  },
  "dimensions": [
    {
      "name": "completeness",
      "label": "完整性",
      "weight": 30,
      "score": 90,
      "grade": "优秀",
      "issues": [
        {
          "id": "DOC-001",
          "severity": "minor",
          "location": "第 3.2 节",
          "message": "未说明异常场景下的降级策略",
          "deduction": 3
        }
      ]
    },
    {
      "name": "accuracy",
      "label": "准确性",
      "weight": 30,
      "score": 85,
      "grade": "良好",
      "issues": []
    },
    {
      "name": "standards",
      "label": "规范性",
      "weight": 20,
      "score": 88,
      "grade": "良好",
      "issues": []
    },
    {
      "name": "readability",
      "label": "可读性",
      "weight": 20,
      "score": 82,
      "grade": "良好",
      "issues": []
    }
  ],
  "suggestions": [
    {
      "id": "SUG-001",
      "priority": "medium",
      "dimension": "completeness",
      "relatedIssue": "DOC-001",
      "title": "补充异常场景降级策略",
      "detail": "在 3.2 节补充第三方服务不可用时的降级处理方案，建议参考 docs/template/fallback.md",
      "estimatedCost": "0.5h"
    }
  ],
  "assessedAt": "2026-07-26T15:30:00+08:00"
}
```

### 周期性质量报告 (Markdown)

```markdown
# 质量评估周报

**报告周期**: 2026-07-20 ~ 2026-07-26
**生成时间**: 2026-07-26 18:00:00
**评估总数**: 18
**平均得分**: 82.5
**整体等级**: B

## 等级分布

| 等级 | 数量 | 占比   |
|------|------|--------|
| A    | 3    | 16.7%  |
| B    | 9    | 50.0%  |
| C    | 4    | 22.2%  |
| D    | 2    | 11.1%  |
| F    | 0    | 0.0%   |

## 基线达成情况

| 类型     | 评估数 | 通过数 | 达成率 | 平均分 |
|----------|--------|--------|--------|--------|
| document | 6      | 5      | 83.3%  | 85.2   |
| code     | 8      | 7      | 87.5%  | 86.1   |
| design   | 2      | 2      | 100%   | 78.5   |
| test     | 2      | 1      | 50.0%  | 76.0   |

## Agent 维度统计

| Agent                | 评估数 | 平均分 | 最低分 | 最高分 |
| --------------------- | ------ | ------ | ------ | ------ |
| requirement-analyst   | 3      | 88.3   | 82     | 95     |
| architect             | 2      | 86.5   | 84     | 89     |
| frontend-developer    | 4      | 83.0   | 72     | 91     |
| backend-developer     | 5      | 85.2   | 68     | 94     |
| ui-designer           | 2      | 78.5   | 75     | 82     |
| tester                | 2      | 76.0   | 70     | 82     |

## 质量趋势

- 较上周：平均分 +2.3，C 级及以上占比 +5.6%
- backend-developer 平均分较上周下降 3.1，需关注
- test 类型达成率仅 50%，建议加强测试评审

## 主要问题 Top 5

| 排名 | 问题类型 | 出现次数 | 涉及 Agent          |
|------|----------|----------|---------------------|
| 1    | 边界场景遗漏 | 6    | frontend-developer  |
| 2    | 注释覆盖率不足 | 4  | backend-developer   |
| 3    | 规范违反 | 4      | ui-designer         |
| 4    | 命名不规范 | 3      | backend-developer   |
| 5    | 断言不充分 | 2      | tester              |

## 改进建议

1. **加强边界场景评审** - 在 PRD 评审阶段强制检查边界场景清单
2. **提升代码注释覆盖率** - 配置 CI 检查，注释覆盖率低于 20% 阻断合并
3. **统一设计规范培训** - 组织设计规范复盘会，重点对齐组件复用与色彩规范
```

## 配置

```json
{
  "storage": {
    "backend": "file",
    "path": "./workspaces/.quality-assessor",
    "autosave": true
  },
  "scoring": {
    "defaultWeights": {
      "document": { "completeness": 30, "accuracy": 30, "readability": 20, "standards": 20 },
      "code": { "readability": 25, "maintainability": 25, "performance": 25, "security": 25 },
      "design": { "ux": 40, "consistency": 30, "feasibility": 30 },
      "test": { "coverage": 35, "effectiveness": 35, "boundary": 30 }
    },
    "vetoThreshold": 60,
    "deductionRules": {
      "critical": { "min": 20, "max": 30 },
      "major": { "min": 10, "max": 15 },
      "minor": { "min": 3, "max": 5 },
      "info": 0
    }
  },
  "baseline": {
    "defaults": {
      "document": 80,
      "code": 85,
      "design": 75,
      "test": 80
    },
    "versioned": true,
    "approvalRequired": true
  },
  "trend": {
    "enabled": true,
    "windows": ["daily", "weekly", "monthly"],
    "retention": "180d",
    "alertOnDecline": true,
    "declineThreshold": 5
  },
  "report": {
    "formats": ["markdown", "html", "json"],
    "defaultFormat": "markdown",
    "timezone": "Asia/Shanghai",
    "includeSuggestions": true,
    "includeHistory": true
  },
  "suggestion": {
    "enabled": true,
    "linkHistoricalFixes": true,
    "maxSuggestions": 10,
    "priorityOrder": ["critical", "major", "minor"]
  }
}
```
