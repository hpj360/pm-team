# Report Plan

## Meta
- **Type**: Research Report（研究评述）
- **Topic**: 2026年11月全球前沿大模型横向对比评估（终版）
- **Audience**: 技术决策者 / Agent 工程团队 / 模型选型相关人员
- **Language**: 中文

## Design System
- **Palette**: bg #FBFAF6 · bg2 #F2EFE7 · ink #21201B · muted #6E6B60 · rule #E3DFD3 · accent #B4531F（terracotta，开放阵营）· accent2 #1F5C4D（petrol，闭源阵营）
- **Typography**: 标题 IBMPlexSerif+（CJK 宋体栈）；正文 WorkSans+（CJK 黑体栈）；数据 JetBrainsMono。标题 flush-left 衬线，正文 15px/1.75
- **Layout**: 880px 单栏编辑流，章节大间距，无卡片堆砌，表格 + 图表为主
- **Personality**: 冷静的技术研究期刊，等宽数据点缀，暖纸底色

## Structure（10章）
1. 执行摘要 — 6 条核心发现 + 指标卡
2. 评估范围与方法论 — 阵营、v4 评分口径、信源分级、三条方法论教训
3. 阵容与规格 — 18 模型规格横表
4. 定价横评 — 价格表 + 混合价图（ECharts log bar）
5. 基准横评 — AA 指数图 + 基准成绩单 + TB4.0 双源图（双尺悖论）
6. 能力评分终版 — 15 模型五维评分表 + 能力×价格散点（帕累托前沿）
7. 灵敏度与方法论反思 — 成本权重敏感性 + 专才/通才
8. 分层格局 — T0/T0.5/T1/T2 分层卡
9. 选型矩阵 — 11 场景推荐
10. 结论 — 三条终局思考
+ Sources（26 项，可点击引用）

## Visuals（ECharts ×4）
| # | 图 | 类型 | 说明 |
|---|---|---|---|
| 1 | AA 独立智能指数 | horizontal bar + markLine(中位41) | 唯一跨阵营独立标尺 |
| 2 | 混合价对比 | horizontal bar（log 轴） | 17 模型 3:1 混合价 |
| 3 | 能力×价格 | scatter（log x） | 帕累托前沿：Hy4 flash→DS-1017→Hy4 |
| 4 | TB4.0 双源 | bar（按测量者着色） | 分数按"谁在测"聚类 |

## Thesis
1. 三层市场结构固化；开源前沿以 1/5~1/20 价格追平 agentic 口径前沿
2. 双尺悖论（广谱差15分 vs agentic 差1-3分）皆真实：中文阵营以广度换专精
3. 2026 H2 竞赛主线=单位任务成本（token 压缩+缓存定价+峰谷折扣）
