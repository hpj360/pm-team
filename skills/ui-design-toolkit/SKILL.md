# Skill: UI Design Toolkit

## 描述

UI 设计系统与组件模板工具，帮助 UI 设计师生成设计系统基础配置、维护组件模板库。作为设计阶段的"源头"工具，专注于设计资产的生产与管理。

> **职责边界说明**: 本 Skill 专注于"设计系统生成"与"组件模板库"两项核心能力。"切图导出"和"标注生成"已委托给 `design-delivery` Skill，以避免功能重叠。`design-delivery` 负责设计交付环节（规范文档、切图资源、标注文档、设计 Token），与本 Skill 形成"生产→交付"的清晰分工。

## 功能

### 1. 设计系统生成（核心能力）

自动生成设计系统基础配置。

**输出内容**:
- 色彩系统
- 字体系统
- 间距系统
- 圆角系统
- 阴影系统

### 2. 组件模板库（核心能力）

提供常用组件的设计模板。

**组件类型**:
- 基础组件: Button, Input, Select, Checkbox, Radio
- 布局组件: Header, Footer, Sidebar, Card
- 导航组件: Menu, Tabs, Breadcrumb, Pagination
- 反馈组件: Modal, Message, Notification, Progress
- 数据组件: Table, List, Tree, Form

### 3. 委托至 design-delivery 的能力（兼容引用）

以下能力保留 API 兼容性，但实际委托给 `design-delivery`：

- **切图导出**（`action: "exportAssets"`）→ 转发至 design-delivery 的"切图资源导出"
- **标注生成** → 转发至 design-delivery 的"设计标注生成"

> **分工说明**: 当需要"从设计稿导出切图"或"生成标注文档"时，应直接调用 `design-delivery` Skill。本 Skill 的 `exportAssets` 接口仅为兼容旧调用方保留。

## 使用示例

### 生成设计系统

```json
{
  "action": "generateDesignSystem",
  "config": {
    "primaryColor": "#1890FF",
    "fontFamily": "PingFang SC",
    "baseUnit": 8
  }
}
```

### 导出切图

```json
{
  "action": "exportAssets",
  "components": ["Button", "Input", "Card"],
  "formats": ["png@2x", "svg"],
  "outputPath": "./assets"
}
```

## 输出格式

```json
{
  "designSystem": {
    "colors": {
      "primary": "#1890FF",
      "success": "#52C41A",
      "warning": "#FAAD14",
      "error": "#F5222D"
    },
    "typography": {
      "h1": { "fontSize": 24, "fontWeight": 600 },
      "h2": { "fontSize": 20, "fontWeight": 600 },
      "body": { "fontSize": 14, "fontWeight": 400 }
    },
    "spacing": [4, 8, 16, 24, 32, 48],
    "borderRadius": [4, 8, 12, 16]
  },
  "assets": [
    {
      "name": "btn_primary",
      "path": "./assets/btn_primary@2x.png",
      "size": "120x40"
    }
  ]
}
```

## 配置

```json
{
  "designSystem": {
    "primaryColor": "#1890FF",
    "fontFamily": "PingFang SC, sans-serif",
    "baseUnit": 8,
    "borderRadius": 4
  },
  "export": {
    "formats": ["png@1x", "png@2x", "png@3x", "svg"],
    "namingConvention": "kebab-case",
    "outputDir": "./design-assets"
  }
}
```
