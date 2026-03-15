# Skill: Database Designer

## 描述

数据库设计工具，帮助数据库设计师进行数据模型设计和数据库优化。

## 功能

### 1. ER图生成

根据需求自动生成ER图。

**生成内容**:
- 实体定义
- 关系定义
- 属性定义
- 约束定义

### 2. 表结构设计

生成标准化的表结构DDL。

**设计内容**:
- 表结构
- 字段定义
- 索引设计
- 约束设计
- 注释说明

### 3. 索引优化

分析并优化索引设计。

**优化内容**:
- 索引建议
- 冗余索引检测
- 缺失索引检测
- 索引使用率分析

### 4. 数据迁移

生成数据迁移脚本。

**迁移类型**:
- DDL迁移
- 数据迁移
- 增量同步
- 回滚脚本

## 使用示例

### 生成ER图

```json
{
  "action": "generateER",
  "entities": [
    {
      "name": "User",
      "attributes": [
        { "name": "id", "type": "uuid", "pk": true },
        { "name": "username", "type": "string", "length": 50 },
        { "name": "email", "type": "string", "length": 100, "unique": true }
      ]
    },
    {
      "name": "Order",
      "attributes": [
        { "name": "id", "type": "uuid", "pk": true },
        { "name": "user_id", "type": "uuid", "fk": "User.id" },
        { "name": "amount", "type": "decimal", "precision": 10, "scale": 2 }
      ]
    }
  ],
  "relations": [
    { "from": "User", "to": "Order", "type": "one-to-many" }
  ]
}
```

### 生成DDL

```json
{
  "action": "generateDDL",
  "tables": ["users", "orders"],
  "dialect": "postgresql",
  "options": {
    "ifNotExists": true,
    "comments": true,
    "indexes": true
  }
}
```

### 索引分析

```json
{
  "action": "analyzeIndexes",
  "database": "mydb",
  "options": {
    "checkUnused": true,
    "checkMissing": true,
    "checkRedundant": true
  }
}
```

### 生成迁移脚本

```json
{
  "action": "generateMigration",
  "changes": [
    {
      "type": "addColumn",
      "table": "users",
      "column": {
        "name": "avatar",
        "type": "string",
        "length": 255,
        "nullable": true
      }
    }
  ],
  "dialect": "postgresql"
}
```

## 输出格式

### ER图输出

```json
{
  "erDiagram": {
    "entities": [
      {
        "name": "User",
        "attributes": [
          { "name": "id", "type": "UUID", "constraint": "PK" },
          { "name": "username", "type": "VARCHAR(50)", "constraint": "NOT NULL" },
          { "name": "email", "type": "VARCHAR(100)", "constraint": "UNIQUE" }
        ]
      }
    ],
    "relations": [
      {
        "from": "User",
        "to": "Order",
        "type": "one-to-many",
        "foreignKey": "user_id"
      }
    ]
  },
  "diagram": "graph TD\n    User --> Order"
}
```

### DDL输出

```sql
-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_created_at ON users(created_at);

-- 注释
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '用户ID';
COMMENT ON COLUMN users.username IS '用户名';
```

### 索引分析输出

```json
{
  "analysis": {
    "unused": [
      {
        "table": "users",
        "index": "idx_users_old",
        "size": "10MB",
        "lastUsed": "never",
        "recommendation": "建议删除"
      }
    ],
    "missing": [
      {
        "table": "orders",
        "columns": ["user_id", "created_at"],
        "reason": "高频查询条件",
        "recommendation": "建议创建复合索引"
      }
    ],
    "redundant": [
      {
        "table": "users",
        "indexes": ["idx_email", "idx_email_status"],
        "reason": "idx_email 被 idx_email_status 覆盖",
        "recommendation": "可删除 idx_email"
      }
    ]
  }
}
```

## 数据类型映射

| 通用类型 | PostgreSQL | MySQL | SQLite |
|----------|------------|-------|--------|
| uuid | UUID | CHAR(36) | TEXT |
| string | VARCHAR | VARCHAR | TEXT |
| text | TEXT | TEXT | TEXT |
| integer | INTEGER | INT | INTEGER |
| bigint | BIGINT | BIGINT | INTEGER |
| decimal | DECIMAL | DECIMAL | REAL |
| boolean | BOOLEAN | TINYINT | INTEGER |
| datetime | TIMESTAMP | DATETIME | TEXT |
| json | JSONB | JSON | TEXT |

## 配置

```json
{
  "dialect": "postgresql",
  "naming": {
    "tableCase": "snake_case",
    "columnCase": "snake_case",
    "indexPrefix": "idx",
    "fkPrefix": "fk"
  },
  "defaults": {
    "primaryKey": {
      "type": "uuid",
      "autoGenerate": true
    },
    "timestamps": true,
    "softDelete": true
  },
  "migration": {
    "path": "./migrations",
    "format": "timestamp",
    "createBackup": true
  }
}
```
