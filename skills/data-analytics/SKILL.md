# Skill: Data Analytics

## 描述

数据统计分析工具，收集、分析和可视化项目工作数据。适用于 OpenClaw 多 Agent 协作框架中任务执行数据、Agent 工作数据与质量数据的采集、多维分析、报表生成与趋势预测，为项目管理者提供量化决策依据。

## 功能

### 1. 数据收集

按周期与来源采集项目执行过程中的原始数据，结构化入库。

**数据来源**:
- 任务执行数据：任务创建、状态变更、完成时间、阻塞记录
- Agent 工作数据：Agent 任务接单量、执行耗时、产出物大小、负载分布
- 质量数据：缺陷数、测试用例数、测试覆盖率、代码审查记录
- 流程数据：各阶段耗时、审批记录、依赖关系变更

**采集模式**:
- `realtime` - 事件驱动实时采集（任务状态变更、Agent 心跳）
- `batch` - 定时批量采集（按小时/天聚合）
- `manual` - 手动触发采集（指定时间段补采）

### 2. 数据分析

对采集到的数据进行多维度统计分析。

**分析类型**:
- 趋势分析 - 识别指标随时间的变化趋势与周期性规律
- 效率分析 - 评估任务完成速度、Agent 产出效率与瓶颈
- 质量分析 - 评估缺陷密度、测试覆盖充分度与审查通过情况
- 流程分析 - 分析各阶段耗时占比与流转效率
- 资源分析 - 评估 Agent 工作量分布与并行度

### 3. 统计报表

按周期生成结构化统计报表，支持多格式输出。

**报表类型**:
- `daily` - 日报：当日任务执行情况、Agent 产出、异常事件
- `weekly` - 周报：本周进度汇总、趋势对比、下周预测
- `monthly` - 月报：月度综合统计、效率/质量回顾、瓶颈分析
- `quarterly` - 季报：季度战略指标回顾、同比环比、目标达成度

### 4. 数据可视化

基于统计数据生成可视化图表，支持多种图表类型。

**图表类型**:
- `line` - 折线图：趋势分析（任务完成率随时间变化）
- `bar` - 柱状图：对比分析（各 Agent 任务量对比）
- `pie` - 饼图：占比分析（任务状态分布、工作量分布）
- `heatmap` - 热力图：分布分析（Agent × 时间段活跃度）

### 5. 预测分析

基于历史数据预测未来指标走势，辅助资源规划与风险预警。

**预测能力**:
- 任务完成时间预测（基于历史 P50/P90 耗时）
- Agent 工作量预测（基于趋势与周期性）
- 缺陷数预测（基于代码变更量与历史缺陷率）
- 资源瓶颈预警（基于负载趋势外推）

### 6. 异常检测

识别指标中的异常模式，主动暴露潜在风险。

**检测能力**:
- 阈值异常 - 指标超出正常范围（如完成率骤降、缺陷率激增）
- 趋势异常 - 指标偏离历史趋势（如耗时持续上升）
- 离群点检测 - 个别数据点显著偏离群体（如某 Agent 耗时远超均值）
- 周期异常 - 周期性规律被打破（如周末任务量异常上升）

## 数据指标

### 效率指标

| 指标 | 计算方式 | 单位 |
| --- | --- | --- |
| 任务完成率 | 已完成任务数 / 总任务数 × 100% | % |
| 平均完成时间 | Σ 任务实际耗时 / 已完成任务数 | 小时 |
| Agent 利用率 | Agent 实际工作时长 / 可用工作时长 × 100% | % |
| 任务吞吐量 | 单位时间内完成任务数 | 个/天 |
| 准时交付率 | 按时完成任务数 / 已完成任务数 × 100% | % |

### 质量指标

| 指标 | 计算方式 | 单位 |
| --- | --- | --- |
| 缺陷率 | 缺陷数 / 千行代码 | 个/KLOC |
| 测试覆盖率 | 已覆盖代码行 / 总代码行 × 100% | % |
| 代码审查通过率 | 一次通过审查数 / 总审查数 × 100% | % |
| 缺陷修复时长 | Σ 缺陷修复耗时 / 缺陷数 | 小时 |
| 回归率 | 修复后再次出现缺陷数 / 总缺陷数 × 100% | % |

### 流程指标

| 指标 | 计算方式 | 单位 |
| --- | --- | --- |
| 各阶段耗时 | 阶段实际耗时 / 项目总耗时 × 100% | % |
| 瓶颈识别 | 各阶段耗时 Top N（耗时最长的阶段） | 阶段 |
| 审批通过率 | 一次审批通过数 / 总审批数 × 100% | % |
| 平均审批时长 | Σ 审批耗时 / 审批数 | 小时 |
| 阶段流转效率 | 阶段实际耗时 / 阶段标准耗时 × 100% | % |

### 资源指标

| 指标 | 计算方式 | 单位 |
| --- | --- | --- |
| Agent 工作量分布 | 各 Agent 任务数 / 总任务数 × 100% | % |
| 并行任务数 | 同一时刻运行中任务数 | 个 |
| Agent 负载均衡度 | 1 - (任务数标准差 / 均值) | 0-1 |
| Agent 平均负载 | Σ 并行任务数 / Agent 数 | 个/Agent |
| 资源冲突次数 | 资源依赖阻塞发生次数 | 次 |

## 使用示例

### 收集数据

```json
{
  "action": "collect",
  "type": "task",
  "period": "daily",
  "date": "2024-03-15"
}
```

### 分析数据

```json
{
  "action": "analyze",
  "type": "efficiency",
  "period": "weekly"
}
```

### 生成报表

```json
{
  "action": "report",
  "type": "weekly",
  "format": "html"
}
```

### 预测分析

```json
{
  "action": "predict",
  "metric": "completion-time",
  "horizon": 7
}
```

### 异常检测

```json
{
  "action": "detect",
  "metric": "defect-rate",
  "period": "weekly",
  "sensitivity": "high"
}
```

### 可视化图表

```json
{
  "action": "visualize",
  "chart": "line",
  "metric": "task-completion-rate",
  "period": "monthly",
  "format": "png"
}
```

## 报表类型

| 类型 | 周期 | 适用场景 | 主要指标 |
| --- | --- | --- | --- |
| `daily` | 日报 | 日常运营监控 | 当日任务量、Agent 活跃度、异常事件 |
| `weekly` | 周报 | 周度进度复盘 | 周进度、趋势对比、下周预测 |
| `monthly` | 月报 | 月度综合评估 | 效率/质量回顾、瓶颈分析、资源利用率 |
| `quarterly` | 季报 | 季度战略复盘 | 战略指标、目标达成度、同比环比 |

## 可视化图表类型

| 图表 | 适用场景 | 示例 |
| --- | --- | --- |
| 折线图 `line` | 时间序列趋势 | 任务完成率随时间变化 |
| 柱状图 `bar` | 跨维度对比 | 各 Agent 任务量对比 |
| 饼图 `pie` | 占比与分布 | 任务状态分布 |
| 热力图 `heatmap` | 二维分布密度 | Agent × 时间段活跃度 |

## 数据存储格式

采用 JSON 结构化存储，按指标维度与时序组织：

```json
{
  "schemaVersion": "1.0",
  "metric": "task-completion-rate",
  "type": "efficiency",
  "period": "daily",
  "dataPoints": [
    {
      "timestamp": "2024-03-15T00:00:00+08:00",
      "value": 85.5,
      "dimensions": {
        "agent": "backend-developer",
        "project": "red-team-file-platform"
      },
      "tags": ["weekly", "normal"]
    }
  ],
  "metadata": {
    "unit": "%",
    "source": "task-tracker",
    "collectedAt": "2024-03-15T23:59:59+08:00",
    "confidence": 0.98
  }
}
```

## 分析模型说明

### 趋势分析模型

采用移动平均与线性回归结合的方法识别趋势：

- **短期趋势**：7 日移动平均，捕捉近期变化
- **中期趋势**：30 日线性回归斜率，反映中期走向
- **周期性识别**：基于自相关函数检测周/月周期性

### 效率分析模型

基于任务执行数据计算效率指标：

- **任务完成率**：按时段聚合，区分 Agent 与项目维度
- **平均完成时间**：采用 P50/P90/P99 分位数，避免极值干扰
- **Agent 利用率**：基于 Agent 心跳与任务执行时长计算

### 质量分析模型

结合缺陷数据与代码规模评估质量：

- **缺陷率**：缺陷数归一化到千行代码，支持跨项目对比
- **测试覆盖率**：基于代码覆盖率工具上报数据聚合
- **缺陷预测**：基于代码变更量、复杂度与历史缺陷率建模

### 预测分析模型

采用时间序列预测方法：

- **短期预测（≤7 天）**：基于移动平均与指数平滑
- **中期预测（≤30 天）**：基于 ARIMA 模型
- **长期预测（>30 天）**：基于历史同期数据外推
- **置信区间**：提供 P50/P90 预测值与置信区间

### 异常检测模型

采用统计学与机器学习结合的方法：

- **阈值检测**：基于历史数据动态计算上下限（均值 ± 3σ）
- **趋势异常**：基于残差分析检测趋势偏离
- **离群点检测**：基于箱线图与孤立森林算法识别离群点
- **敏感性等级**：`low`（宽松阈值）/ `medium`（默认）/ `high`（严格阈值）

## 输出格式

### 分析结果输出

```json
{
  "summary": {
    "type": "efficiency",
    "period": "weekly",
    "generatedAt": "2024-03-22T10:00:00+08:00"
  },
  "metrics": {
    "taskCompletionRate": 87.5,
    "avgCompletionTime": 12.5,
    "agentUtilization": 76.8,
    "taskThroughput": 14
  },
  "trends": {
    "taskCompletionRate": "up",
    "avgCompletionTime": "down",
    "trendConfidence": 0.92
  },
  "anomalies": [],
  "insights": [
    "本周任务完成率较上周上升 5.2%",
    "backend-developer Agent 利用率偏低 (62%)"
  ]
}
```

### 报表输出

```json
{
  "reportType": "weekly",
  "format": "html",
  "period": {
    "start": "2024-03-15",
    "end": "2024-03-21"
  },
  "sections": [
    {
      "title": "进度概览",
      "content": "本周完成任务 28 个，新增任务 32 个，整体进度 68%"
    },
    {
      "title": "效率指标",
      "content": "平均完成时间 12.5h，准时交付率 92%"
    },
    {
      "title": "质量指标",
      "content": "缺陷率 1.2/KLOC，测试覆盖率 85%"
    },
    {
      "title": "异常与风险",
      "content": "task-006 阻塞超 48h，需人工介入"
    }
  ],
  "charts": [
    { "type": "line", "title": "任务完成率趋势", "path": "/charts/weekly-line-001.png" },
    { "type": "bar", "title": "Agent 工作量分布", "path": "/charts/weekly-bar-001.png" }
  ],
  "generatedAt": "2024-03-22T10:00:00+08:00"
}
```

## 配置

```json
{
  "storage": {
    "backend": "file",
    "path": "./workspaces/.data-analytics",
    "format": "json",
    "retention": "180d"
  },
  "collection": {
    "mode": "batch",
    "interval": "1h",
    "sources": ["task-tracker", "code-reviewer", "tester"]
  },
  "analysis": {
    "defaultPeriod": "weekly",
    "trendWindow": 7,
    "anomalySensitivity": "medium"
  },
  "report": {
    "defaultType": "weekly",
    "format": "html",
    "timezone": "Asia/Shanghai",
    "autoGenerate": true
  },
  "prediction": {
    "defaultHorizon": 7,
    "model": "arima",
    "confidenceLevel": 0.9
  },
  "visualization": {
    "defaultChart": "line",
    "format": "png",
    "theme": "light"
  }
}
```
