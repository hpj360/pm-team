# Skill: Design Delivery

## 描述

设计交付工具，帮助UI设计师生成设计规范文档、切图资源和标注文档，实现设计到开发的无缝交付。

## 功能

### 1. 设计规范文档生成

自动生成完整的设计规范文档。

**文档内容**:
- 品牌色彩规范
- 字体排版规范
- 间距布局规范
- 组件设计规范
- 图标规范
- 动效规范

### 2. 切图资源导出

自动导出多分辨率切图资源。

**导出格式**:
- PNG @1x, @2x, @3x
- SVG 矢量格式
- WebP 现代格式
- PDF 文档格式

### 3. 设计标注生成

生成详细的设计标注文档。

**标注内容**:
- 尺寸标注
- 颜色标注
- 字体标注
- 间距标注
- 阴影标注

### 4. 设计Token生成

生成多平台设计Token。

**支持平台**:
- CSS Variables
- SCSS Variables
- JavaScript/JSON
- iOS (Swift)
- Android (XML/Kotlin)

## 使用示例

### 生成设计规范文档

```json
{
  "action": "generateSpec",
  "config": {
    "name": "产品设计规范",
    "brand": {
      "name": "MyApp",
      "logo": "./assets/logo.svg",
      "primaryColor": "#1890FF"
    },
    "output": {
      "format": "html",
      "path": "./design-spec"
    }
  }
}
```

### 导出切图资源

```json
{
  "action": "exportAssets",
  "config": {
    "source": "./design-files",
    "formats": ["png@1x", "png@2x", "png@3x", "svg"],
    "output": {
      "path": "./assets",
      "naming": "kebab-case",
      "structure": "by-type"
    },
    "optimize": {
      "compress": true,
      "removeMetadata": true
    }
  }
}
```

### 生成设计Token

```json
{
  "action": "generateTokens",
  "config": {
    "platforms": ["css", "scss", "js", "ios", "android"],
    "output": {
      "path": "./design-tokens",
      "filename": "tokens"
    }
  }
}
```

### 生成标注文档

```json
{
  "action": "generateAnnotations",
  "config": {
    "pages": ["home", "login", "profile"],
    "includeScreenshots": true,
    "output": {
      "format": "html",
      "path": "./annotations"
    }
  }
}
```

## 输出格式

### 设计规范文档结构

```
design-spec/
├── index.html              # 规范首页
├── overview/
│   ├── brand.html          # 品牌介绍
│   └── principles.html     # 设计原则
├── foundations/
│   ├── colors.html         # 色彩规范
│   ├── typography.html     # 字体规范
│   ├── spacing.html        # 间距规范
│   ├── icons.html          # 图标规范
│   └── shadows.html        # 阴影规范
├── components/
│   ├── buttons.html        # 按钮组件
│   ├── forms.html          # 表单组件
│   ├── navigation.html     # 导航组件
│   └── feedback.html       # 反馈组件
├── patterns/
│   ├── layouts.html        # 布局模式
│   └── templates.html      # 页面模板
├── resources/
│   ├── downloads.html      # 资源下载
│   └── changelog.html      # 更新日志
└── assets/
    ├── css/
    ├── js/
    └── images/
```

### 设计Token输出

#### CSS Variables

```css
:root {
  /* Colors */
  --color-primary: #1890FF;
  --color-primary-light: #40A9FF;
  --color-primary-dark: #096DD9;
  --color-success: #52C41A;
  --color-warning: #FAAD14;
  --color-error: #F5222D;
  
  /* Typography */
  --font-family: 'PingFang SC', sans-serif;
  --font-size-xs: 12px;
  --font-size-sm: 14px;
  --font-size-base: 16px;
  --font-size-lg: 20px;
  --font-size-xl: 24px;
  
  /* Spacing */
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;
  
  /* Border Radius */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  
  /* Shadows */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.1);
  --shadow-lg: 0 10px 15px rgba(0, 0, 0, 0.1);
}
```

#### JavaScript/JSON

```json
{
  "colors": {
    "primary": {
      "value": "#1890FF",
      "type": "color"
    },
    "primaryLight": {
      "value": "#40A9FF",
      "type": "color"
    }
  },
  "typography": {
    "fontFamily": {
      "value": "'PingFang SC', sans-serif",
      "type": "fontFamily"
    },
    "fontSizeBase": {
      "value": "16px",
      "type": "fontSize"
    }
  },
  "spacing": {
    "sm": {
      "value": "8px",
      "type": "spacing"
    },
    "md": {
      "value": "16px",
      "type": "spacing"
    }
  }
}
```

#### iOS (Swift)

```swift
enum Colors {
    static let primary = UIColor(hex: 0x1890FF)
    static let primaryLight = UIColor(hex: 0x40A9FF)
    static let success = UIColor(hex: 0x52C41A)
    static let warning = UIColor(hex: 0xFAAD14)
    static let error = UIColor(hex: 0xF5222D)
}

enum Typography {
    static let fontFamily = "PingFang SC"
    static let fontSizeXS: CGFloat = 12
    static let fontSizeSM: CGFloat = 14
    static let fontSizeBase: CGFloat = 16
    static let fontSizeLG: CGFloat = 20
}

enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
}
```

#### Android (XML)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Colors -->
    <color name="color_primary">#1890FF</color>
    <color name="color_primary_light">#40A9FF</color>
    <color name="color_success">#52C41A</color>
    <color name="color_warning">#FAAD14</color>
    <color name="color_error">#F5222D</color>
    
    <!-- Typography -->
    <dimen name="font_size_xs">12sp</dimen>
    <dimen name="font_size_sm">14sp</dimen>
    <dimen name="font_size_base">16sp</dimen>
    <dimen name="font_size_lg">20sp</dimen>
    
    <!-- Spacing -->
    <dimen name="spacing_xs">4dp</dimen>
    <dimen name="spacing_sm">8dp</dimen>
    <dimen name="spacing_md">16dp</dimen>
    <dimen name="spacing_lg">24dp</dimen>
</resources>
```

### 切图命名规范

```
[模块]_[组件]_[状态]_[尺寸].[格式]

示例:
btn_primary_normal@2x.png
btn_primary_hover@2x.png
btn_primary_disabled@2x.png
icon_user_default@2x.png
icon_user_active@2x.png
card_bg_shadow@2x.png
```

### 标注文档格式

```markdown
# 页面标注: 登录页

## 页面信息
- 页面名称: 登录页
- 设计稿尺寸: 375x812 (iPhone X)
- 设计师: UI Designer
- 更新日期: 2024-03-15

## 布局标注

### 页面结构
┌─────────────────────────────────────┐
│           顶部间距: 120px            │
├─────────────────────────────────────┤
│            Logo区域                  │
│           尺寸: 120x40               │
│           居中对齐                   │
├─────────────────────────────────────┤
│           间距: 40px                 │
├─────────────────────────────────────┤
│         表单区域                      │
│    宽度: 335px (左右边距20px)        │
│    ┌─────────────────────────┐      │
│    │ 邮箱输入框               │      │
│    │ 高度: 48px              │      │
│    │ 圆角: 8px               │      │
│    │ 边框: 1px #E8E8E8       │      │
│    └─────────────────────────┘      │
│           间距: 16px                 │
│    ┌─────────────────────────┐      │
│    │ 密码输入框               │      │
│    └─────────────────────────┘      │
├─────────────────────────────────────┤
│           间距: 24px                 │
├─────────────────────────────────────┤
│    ┌─────────────────────────┐      │
│    │      登录按钮            │      │
│    │ 高度: 48px              │      │
│    │ 背景: #1890FF           │      │
│    │ 文字: #FFFFFF 16px      │      │
│    │ 圆角: 8px               │      │
│    └─────────────────────────┘      │
└─────────────────────────────────────┘

## 颜色标注

| 元素 | 颜色值 | 用途 |
|------|--------|------|
| 主色 | #1890FF | 按钮、链接 |
| 背景色 | #F5F5F5 | 页面背景 |
| 文字主色 | #333333 | 主要文字 |
| 文字次色 | #666666 | 次要文字 |
| 边框色 | #E8E8E8 | 输入框边框 |
| 错误色 | #F5222D | 错误提示 |

## 字体标注

| 元素 | 字体 | 字号 | 字重 | 行高 |
|------|------|------|------|------|
| 标题 | PingFang SC | 24px | 600 | 32px |
| 输入框标签 | PingFang SC | 14px | 400 | 22px |
| 按钮文字 | PingFang SC | 16px | 500 | 24px |
| 错误提示 | PingFang SC | 12px | 400 | 20px |

## 交互说明

| 元素 | 交互 | 效果 |
|------|------|------|
| 输入框 | 聚焦 | 边框变为#1890FF |
| 输入框 | 输入错误 | 边框变为#F5222D，显示错误提示 |
| 登录按钮 | 点击 | 验证表单，成功跳转首页 |
| 登录按钮 | 加载中 | 显示loading，禁用按钮 |
```

## 配置

```json
{
  "spec": {
    "name": "产品设计规范",
    "version": "1.0.0",
    "output": {
      "format": "html",
      "path": "./design-spec"
    }
  },
  "assets": {
    "formats": ["png@1x", "png@2x", "png@3x", "svg"],
    "optimize": true,
    "output": "./assets"
  },
  "tokens": {
    "platforms": ["css", "scss", "js", "ios", "android"],
    "output": "./design-tokens"
  },
  "annotations": {
    "includeScreenshots": true,
    "output": "./annotations"
  }
}
```
