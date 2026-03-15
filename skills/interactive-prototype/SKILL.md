# Skill: Interactive Prototype Builder

## 描述

交互式原型构建工具，生成可交互的高保真原型，支持页面跳转、表单验证、状态管理等交互能力。

## 功能

### 1. 交互原型生成

生成可交互的HTML原型。

**交互能力**:
- 页面跳转和路由
- 表单验证交互
- 状态管理
- 动画效果
- 数据绑定

### 2. 组件渲染引擎

实时渲染UI组件。

**支持组件**:
- 表单组件: Input, Select, Checkbox, Radio, Switch
- 布局组件: Header, Footer, Sidebar, Card, Modal
- 导航组件: Menu, Tabs, Breadcrumb, Pagination
- 数据组件: Table, List, Tree, Chart
- 反馈组件: Message, Notification, Progress

### 3. 预览服务

启动本地预览服务器。

**服务能力**:
- 实时预览
- 热更新
- 多设备预览
- 分享链接

### 4. 响应式预览

多设备尺寸预览。

**预设尺寸**:
- iPhone SE: 375x667
- iPhone 14: 390x844
- iPad: 768x1024
- Desktop: 1440x900

## 使用示例

### 生成交互原型

```json
{
  "action": "build",
  "config": {
    "name": "用户管理系统",
    "pages": [
      {
        "id": "login",
        "path": "/login",
        "title": "登录",
        "layout": "center",
        "components": [
          {
            "id": "login-form",
            "type": "Form",
            "props": {
              "fields": [
                { "name": "email", "label": "邮箱", "type": "email", "required": true },
                { "name": "password", "label": "密码", "type": "password", "required": true }
              ],
              "submitText": "登录"
            },
            "events": {
              "onSubmit": {
                "action": "navigate",
                "target": "/home",
                "condition": "validateSuccess"
              }
            }
          }
        ]
      },
      {
        "id": "home",
        "path": "/home",
        "title": "首页",
        "layout": "default",
        "components": [
          {
            "id": "header",
            "type": "Header",
            "props": {
              "title": "用户管理系统",
              "menu": [
                { "text": "首页", "path": "/home" },
                { "text": "用户", "path": "/users" },
                { "text": "设置", "path": "/settings" }
              ]
            }
          }
        ]
      }
    ],
    "interactions": [
      {
        "trigger": "click",
        "source": "login-form.submit",
        "actions": [
          { "type": "validate" },
          { "type": "navigate", "target": "/home" }
        ]
      }
    ],
    "theme": {
      "primaryColor": "#1890FF",
      "fontFamily": "PingFang SC"
    }
  }
}
```

### 启动预览服务

```json
{
  "action": "preview",
  "config": {
    "port": 3000,
    "open": true,
    "hotReload": true,
    "devices": ["iphone", "ipad", "desktop"]
  }
}
```

### 导出原型

```json
{
  "action": "export",
  "config": {
    "format": "html",
    "outputPath": "./prototypes",
    "includeAssets": true,
    "minify": true
  }
}
```

## 输出格式

### 原型项目结构

```
prototype/
├── index.html           # 入口文件
├── assets/
│   ├── css/
│   │   └── styles.css
│   ├── js/
│   │   └── app.js
│   └── images/
├── pages/
│   ├── login.html
│   ├── home.html
│   └── users.html
└── config.json          # 原型配置
```

### 交互配置

```json
{
  "router": {
    "mode": "history",
    "routes": [
      { "path": "/login", "component": "LoginPage" },
      { "path": "/home", "component": "HomePage" }
    ]
  },
  "store": {
    "state": {
      "user": null,
      "isLoggedIn": false
    },
    "actions": {
      "login": "handleLogin",
      "logout": "handleLogout"
    }
  },
  "interactions": [
    {
      "id": "login-submit",
      "trigger": "click",
      "source": "#login-btn",
      "actions": [
        { "type": "validate", "target": "#login-form" },
        { "type": "setState", "key": "isLoggedIn", "value": true },
        { "type": "navigate", "path": "/home" }
      ]
    }
  ]
}
```

## 组件定义

### 表单组件

```typescript
interface FormProps {
  fields: FormField[];
  layout?: 'horizontal' | 'vertical' | 'inline';
  submitText?: string;
  validateOnBlur?: boolean;
}

interface FormField {
  name: string;
  label: string;
  type: 'text' | 'email' | 'password' | 'number' | 'select' | 'checkbox';
  required?: boolean;
  placeholder?: string;
  rules?: ValidationRule[];
}
```

### 表格组件

```typescript
interface TableProps {
  columns: TableColumn[];
  dataSource: any[];
  pagination?: {
    page: number;
    pageSize: number;
    total: number;
  };
  rowSelection?: {
    type: 'checkbox' | 'radio';
    selectedKeys: string[];
  };
}
```

## 预览服务API

### 获取原型列表

```
GET /api/prototypes
```

### 获取原型详情

```
GET /api/prototypes/:id
```

### 更新原型

```
PUT /api/prototypes/:id
```

### 预览原型

```
GET /preview/:id
```

## 配置

```json
{
  "preview": {
    "port": 3000,
    "host": "localhost",
    "open": true,
    "hotReload": true
  },
  "export": {
    "formats": ["html", "zip"],
    "outputPath": "./prototypes",
    "minify": true
  },
  "theme": {
    "primaryColor": "#1890FF",
    "successColor": "#52C41A",
    "warningColor": "#FAAD14",
    "errorColor": "#F5222D",
    "fontFamily": "PingFang SC, sans-serif",
    "borderRadius": 4
  },
  "devices": [
    { "name": "iPhone SE", "width": 375, "height": 667 },
    { "name": "iPhone 14", "width": 390, "height": 844 },
    { "name": "iPad", "width": 768, "height": 1024 },
    { "name": "Desktop", "width": 1440, "height": 900 }
  ]
}
```
