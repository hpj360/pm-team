# 红方文件汇聚平台标签体系设计 Spec

## Why

红方文件汇聚平台覆盖文件上传、文件解析、文件智能分析、目标画像刻画、网络地形还原、访问凭证获取、漏洞战机识别、横向移动等 8 大红方业务场景,文件是核心数据载体。现有数据模型中:
- [file_tags](file:///d:/AI项目/pm-team/docs/database-design.md#L557) 表仅有 `tag_name/tag_type(user|system)/tag_color` 三字段,无体系化分层
- [tag_dict](file:///d:/AI项目/pm-team/docs/database-design.md#L1465) 字典表仅 `tag_name/tag_category/description`,缺少编码、值类型、识别规则、适用对象等关键属性
- 标签类型仅 user/system 两类,无法覆盖红方业务场景、实体识别、情报关联、安全合规等维度
- 无自动打标规则定义,全靠人工打标,效率低且不一致

需设计一套完整、分层、可扩展的标签体系,打通"文件→实体→画像→场景→情报→合规"链路,支撑自动打标、多维检索、画像刻画与情报关联。

## What Changes

- **新增**《标签体系设计文档》`docs/tag-system-design.md`,包含:
  - 六层标签体系架构(L1 文件属性 / L2 业务流程 / L3 实体识别 / L4 业务场景 / L5 情报关联 / L6 安全合规)
  - 完整标签字典(每个标签含:编码、中文名、层级、分类、值类型、适用对象、识别规则、是否多选、示例)
  - 自动识别规则集(正则/字典/模型/关联推导四类规则)
  - 标签与 8 大业务场景的映射矩阵
  - 标签体系数据模型(扩展现有 tag_dict 表)
- **新增**《标签体系评审报告》`docs/tag-system-review.md`,记录评审问题与优化项,保证质量分 ≥ 90
- 不修改现有代码与数据库 DDL(本阶段为设计产出,DDL 落地在后续迭代)

## Impact

- Affected specs: v2-iteration-plan(标签体系为 v2.0 文件分析、目标画像、威胁情报模块的基础能力)
- Affected code: 设计阶段不改动代码;后续落地涉及
  - [docs/database-design.md](file:///d:/AI项目/pm-team/docs/database-design.md) `file_tags`/`tag_dict` 表结构增强
  - [docs/api-contracts/parse-service.yaml](file:///d:/AI项目/pm-team/docs/api-contracts/parse-service.yaml) 解析阶段自动打标
  - [docs/api-contracts/analyze-service.yaml](file:///d:/AI项目/pm-team/docs/api-contracts/analyze-service.yaml) 分析阶段标签推导
  - 前端文件检索/画像页面的标签筛选器

## ADDED Requirements

### Requirement: 六层标签体系架构

系统 SHALL 提供覆盖红方全业务流程的六层标签体系,每层职责清晰、互不重叠:

| 层级 | 名称 | 职责 | 标签对象 | 示例 |
|------|------|------|----------|------|
| L1 | 文件属性 | 描述文件自身客观属性 | 文件 | 文件类型、来源类型、语言、大小分级、格式族 |
| L2 | 业务流程 | 标记文件在业务链路中的阶段与处理结果 | 文件 | 上传来源、解析状态、分析结论、画像覆盖 |
| L3 | 实体识别 | 文件中提取的结构化实体类型 | 文件/实体 | IP、域名、主机、用户、凭证、漏洞、IOC、端口、服务 |
| L4 | 业务场景 | 关联红方 8 大业务场景 | 文件/目标/任务 | 目标画像、网络地形、凭证获取、漏洞战机、横向移动 |
| L5 | 情报关联 | 关联威胁情报与攻击知识库 | 文件/实体/目标 | APT 组织、攻击技术(TTP)、威胁等级、情报来源 |
| L6 | 安全合规 | 数据管控与合规属性 | 文件/目标 | 密级、保留期、合规要求、数据分类分级(L1-L5) |

#### Scenario: 标签层级互斥
- **WHEN** 为文件打 L1 文件类型标签
- **THEN** 该标签归属 L1 层,不与 L3 实体标签混淆
- **AND** 标签编码前缀反映层级(如 `L1.FILE.TYPE.PDF`)

### Requirement: 标签字典完整定义

系统 SHALL 提供完整标签字典,每个标签 SHALL 包含以下字段(中文名):

| 字段 | 中文名 | 说明 |
|------|--------|------|
| tag_code | 标签编码 | 全局唯一,格式 `层级.分类.名称.值`,如 `L1.FILE.TYPE.PDF` |
| tag_name | 标签中文名 | 如"PDF文档" |
| layer | 层级 | L1-L6 |
| category | 分类 | 层内分组,如 FILE/BUSINESS/ENTITY/SCENE/INTEL/COMPLIANCE |
| value_type | 值类型 | ENUM(枚举)/TEXT(文本)/NUMBER(数值)/BOOL(布尔)/DATE(日期) |
| applicable_object | 适用对象 | FILE(文件)/ENTITY(实体)/TARGET(目标)/TASK(任务)/ALL |
| identify_rule | 识别规则 | 自动打标规则描述(正则/字典/模型/关联) |
| is_multi | 是否多选 | 布尔,如文件类型单选、IOC类型多选 |
| parent_code | 父标签编码 | 支持层级树,如 `L3.ENTITY.IP` 下有 `L3.ENTITY.IP.PUBLIC` |
| enabled | 启用 | 布尔 |
| description | 口径定义 | 标签含义与边界说明 |

#### Scenario: 标签编码规范
- **WHEN** 新增一个 L3 实体识别层的 IP 公网标签
- **THEN** 编码为 `L3.ENTITY.IP.PUBLIC`
- **AND** 父标签编码为 `L3.ENTITY.IP`
- **AND** 适用对象为 FILE 和 ENTITY

### Requirement: 覆盖 8 大业务场景

标签体系 SHALL 覆盖以下 8 大红方业务场景,每个场景有对应的 L4 场景标签:

1. **文件上传** — 标记上传来源、上传方式、去重状态
2. **文件解析** — 标记解析能力、解析结果、提取实体类型
3. **文件智能分析** — 标记分析类型(恶意代码/行为分析/漏洞提取)、分析结论、威胁等级
4. **目标画像刻画** — 标记目标类型、资产分类、画像完整度
5. **网络地形还原** — 标记拓扑节点类型、网络区域、连接关系
6. **访问凭证获取** — 标记凭证类型、凭证状态、可用性
7. **漏洞战机识别** — 标记漏洞类型、可利用性、利用难度、影响等级
8. **横向移动** — 标记移动路径、跳板节点、权限变化

#### Scenario: 业务场景标签可检索
- **WHEN** 红方人员检索"漏洞战机"场景相关文件
- **THEN** 可通过 L4 场景标签 `L4.SCENE.VULN.OPPORTUNITY` 过滤
- **AND** 结果包含所有被标记为漏洞战机的文件

### Requirement: 自动识别规则

系统 SHALL 提供四类自动打标规则:

| 规则类型 | 适用层 | 触发时机 | 示例 |
|----------|--------|----------|------|
| 正则规则(REGEX) | L1/L3 | 文件上传/解析后 | 文件扩展名→L1文件类型; CVE编号正则→L3漏洞实体 |
| 字典匹配(DICT) | L1/L3/L5 | 文件解析后 | 文件类型字典→L1类型; APT组织字典→L5情报 |
| 模型识别(ML) | L3/L5 | 智能分析阶段 | MalwareML分类→L5威胁等级; NER模型→L3实体 |
| 关联推导(ASSOC) | L4/L6 | 实体入库/关联建立后 | 文件含目标IP→L4目标画像; 含敏感数据→L6密级 |

#### Scenario: 正则自动打标
- **WHEN** 上传文件 `report.pdf`
- **THEN** 规则 `L1.FILE.TYPE` 正则 `\.pdf$` 命中
- **AND** 自动打标 `L1.FILE.TYPE.PDF`

#### Scenario: 关联推导打标
- **WHEN** 文件解析提取到 IP `10.0.1.5`,且该 IP 已关联目标 T001
- **THEN** 关联推导规则触发
- **AND** 文件自动打标 `L4.SCENE.PROFILE.ASSET`(目标画像-资产)

### Requirement: 标签与业务场景映射矩阵

文档 SHALL 提供标签×业务场景映射矩阵,明确每个业务场景使用哪些层标签:

#### Scenario: 映射矩阵完整
- **WHEN** 查阅映射矩阵
- **THEN** 8 个业务场景 × 6 个标签层均有对应单元格
- **AND** 每个单元格标注"必选/可选/不适用"

## MODIFIED Requirements

### Requirement: 标签字典表结构增强(设计稿)

现有 [tag_dict](file:///d:/AI项目/pm-team/docs/database-design.md#L1465) 表字段不足,设计稿 SHALL 提供增强后的表结构(本阶段不落地 DDL):

```sql
-- 增强后的标签字典表(设计稿,后续迭代落地)
CREATE TABLE tag_dict_v2 (
  tag_code        VARCHAR(128) PRIMARY KEY,  -- 标签编码
  tag_name        VARCHAR(128) NOT NULL,      -- 标签中文名
  layer           CHAR(2) NOT NULL,           -- 层级 L1-L6
  category        VARCHAR(32) NOT NULL,       -- 分类
  value_type      VARCHAR(16) DEFAULT 'ENUM', -- 值类型
  applicable_object VARCHAR(16) DEFAULT 'FILE', -- 适用对象
  identify_rule   TEXT,                       -- 识别规则描述
  rule_type       VARCHAR(16),                -- 规则类型 REGEX/DICT/ML/ASSOC
  rule_expr       TEXT,                       -- 规则表达式
  is_multi        SMALLINT DEFAULT 0,         -- 是否多选
  parent_code     VARCHAR(128),               -- 父标签
  severity        SMALLINT,                   -- 严重级别(情报/合规层)
  enabled         SMALLINT DEFAULT 1,
  description     TEXT
);
```

### Requirement: 评审质量保障

文档产出后 SHALL 进行评审,评审维度:
1. 完整性 — 8 业务场景、6 层标签是否全覆盖
2. 一致性 — 编码规范、命名规范、值类型是否统一
3. 正交性 — 标签间是否重叠、互斥关系是否清晰
4. 可识别性 — 自动识别规则是否覆盖核心标签
5. 可扩展性 — 新增标签/场景是否可平滑扩展
6. 质量分 ≥ 90

#### Scenario: 评审通过
- **WHEN** 完成评审报告
- **THEN** 质量分 ≥ 90
- **AND** 所有 P0/P1 问题已修复
