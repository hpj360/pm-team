# Skill: UI Design Toolkit

## 描述

UI设计辅助工具，帮助UI设计师进行视觉设计、设计规范管理和切图输出。

## 功能

### 1. 设计系统生成

自动生成设计系统基础配置。

**输出内容**:
- 色彩系统
- 字体系统
- 间距系统
- 圆角系统
- 阴影系统

### 2. 组件模板库

提供常用组件的设计模板。

**组件类型**:
- 基础组件: Button, Input, Select, Checkbox, Radio
- 布局组件: Header, Footer, Sidebar, Card
- 导航组件: Menu, Tabs, Breadcrumb, Pagination
- 反馈组件: Modal, Message, Notification, Progress
- 数据组件: Table, List, Tree, Form

### 3. 切图导出

生成多分辨率切图资源。

**导出格式**:
```
@1x  - 标准分辨率
@2x  - 高清分辨率
@3x  - 超高清分辨率
SVG  - 矢量格式
```

### 4. 标注生成

自动生成设计标注文档。

**标注内容**:
- 尺寸标注
- 颜色标注
- 字体标注
- 间距标注

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
