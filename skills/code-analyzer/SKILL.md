# Skill: Code Analyzer

## 描述

代码静态分析工具，帮助代码审查员进行代码质量、安全和性能分析。

## 功能

### 1. 代码质量分析

分析代码的可读性、可维护性和可扩展性。

**检查项**:
- 命名规范
- 代码复杂度
- 重复代码检测
- 代码注释覆盖率
- 函数长度检测

### 2. 安全漏洞扫描

检测代码中的安全漏洞。

**检测类型**:
- SQL注入
- XSS跨站脚本
- CSRF跨站请求伪造
- 敏感数据暴露
- 不安全的依赖

### 3. 性能分析

分析代码的性能问题。

**检测项**:
- 循环复杂度
- 内存泄漏风险
- N+1查询问题
- 不合理的算法复杂度

### 4. 规范检查

检查代码是否符合团队规范。

**规范类型**:
- 编码风格
- 命名约定
- 注释规范
- 文件组织

## 使用示例

```json
{
  "action": "analyze",
  "target": "./src",
  "rules": ["quality", "security", "performance"]
}
```

## 输出格式

```json
{
  "summary": {
    "totalFiles": 50,
    "totalLines": 5000,
    "issues": {
      "critical": 2,
      "major": 10,
      "minor": 25,
      "info": 50
    }
  },
  "issues": [
    {
      "id": "SEC-001",
      "type": "security",
      "severity": "critical",
      "file": "src/auth/login.ts",
      "line": 42,
      "message": "检测到SQL注入风险",
      "suggestion": "使用参数化查询替代字符串拼接"
    }
  ]
}
```

## 配置

```json
{
  "languages": ["typescript", "javascript", "python"],
  "rules": {
    "maxLineLength": 120,
    "maxFunctionLength": 50,
    "maxComplexity": 10,
    "minCommentCoverage": 20
  },
  "ignore": ["node_modules", "dist", "build"]
}
```

## 支持的语言

- JavaScript / TypeScript
- Python
- Java
- Go
- Rust
