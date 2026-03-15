# Skill: API Mock

## 描述

API Mock服务工具，帮助前后端工程师进行接口Mock和数据模拟。

## 功能

### 1. Mock服务启动

启动本地Mock服务器。

**支持协议**:
- RESTful API
- GraphQL
- WebSocket

### 2. 数据模板

定义Mock数据模板。

**模板语法**:
- 随机数据生成
- 条件逻辑
- 循环生成
- 引用关联

### 3. 接口代理

代理真实API并记录响应。

**代理功能**:
- 请求转发
- 响应记录
- 数据缓存
- 延迟模拟

### 4. 场景模拟

模拟各种业务场景。

**场景类型**:
- 正常响应
- 错误响应
- 超时响应
- 边界数据

## 使用示例

### 定义Mock接口

```json
{
  "action": "define",
  "endpoints": [
    {
      "method": "GET",
      "path": "/api/users",
      "response": {
        "code": 0,
        "data": {
          "list|10": [{
            "id": "@guid",
            "name": "@cname",
            "email": "@email"
          }],
          "total": 100
        }
      }
    },
    {
      "method": "POST",
      "path": "/api/users",
      "response": {
        "code": 0,
        "data": {
          "id": "@guid"
        }
      }
    }
  ]
}
```

### 启动Mock服务

```json
{
  "action": "start",
  "port": 3001,
  "proxy": {
    "/api/real": "http://real-api.example.com"
  }
}
```

### 模拟场景

```json
{
  "action": "scenario",
  "name": "error-case",
  "endpoints": [
    {
      "method": "GET",
      "path": "/api/users",
      "response": {
        "code": 500,
        "message": "服务器错误"
      },
      "delay": 1000
    }
  ]
}
```

## 数据模板语法

### 基础类型

```
@boolean        - 布尔值
@integer(1,100) - 整数范围
@float(1,100,2) - 浮点数
@string(10)     - 随机字符串
@email          - 邮箱
@phone          - 手机号
@cname          - 中文名
@date           - 日期
@datetime       - 日期时间
@url            - URL
@ip             - IP地址
@guid           - GUID
@id             - 自增ID
```

### 数组生成

```json
{
  "list|10": [{
    "id": "@id",
    "name": "@cname"
  }]
}
```

### 条件逻辑

```json
{
  "status|1": ["pending", "active", "completed"],
  "result": function() {
    return this.status === 'completed' ? 'success' : 'pending'
  }
}
```

## 输出格式

```json
{
  "mockServer": {
    "status": "running",
    "port": 3001,
    "endpoints": [
      {
        "method": "GET",
        "path": "/api/users",
        "calls": 10,
        "lastCall": "2024-03-15T10:30:00Z"
      }
    ]
  },
  "generatedData": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "张三",
    "email": "zhangsan@example.com"
  }
}
```

## 配置

```json
{
  "port": 3001,
  "baseUrl": "/api",
  "cors": true,
  "delay": {
    "min": 100,
    "max": 500
  },
  "proxy": {
    "enabled": true,
    "target": "http://real-api.example.com",
    "paths": ["/api/real"]
  },
  "scenarios": {
    "default": "normal",
    "available": ["normal", "error", "timeout", "empty"]
  }
}
```
