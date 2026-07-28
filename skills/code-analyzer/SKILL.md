# Skill: Code Analyzer

## 描述

代码静态分析工具，帮助代码审查员进行代码质量、代码规范与性能分析。

> **职责边界说明**: 本 Skill 专注于**静态代码质量分析**（可读性、复杂度、重复代码、规范检查）。安全漏洞扫描（SQL 注入、XSS、CSRF 等）已委托给 `security-scanner` Skill，后者提供更专业的 SAST/DAST/SCA/容器扫描/渗透测试/合规检查能力。本 Skill 的"安全漏洞扫描"功能保留 API 兼容性，但实际安全扫描应调用 `security-scanner`。两者分工：`code-analyzer` = 代码质量与性能；`security-scanner` = 安全漏洞与合规。

## 技术栈映射（必读）

本项目的代码分析工具链已在 `.gitlab-ci.yml` 的 `lint` 和 `test` 阶段配置。本 Skill 与现有工具的映射关系：

| Skill 声明能力 | 实际执行工具 | 配置位置 | 状态 |
|---------------|-------------|---------|------|
| 代码规范检查（后端） | **Checkstyle** + **SpotBugs** | `.gitlab-ci.yml` `lint:backend`（mvn checkstyle:check spotbugs:check） | ✅ 已配置 |
| 代码规范检查（前端） | **ESLint** + **Prettier** | `.gitlab-ci.yml` `lint:frontend`（npm run lint + prettier --check） | ✅ 已配置 |
| 代码质量分析 | **SonarQube**（Quality Gate） | `.gitlab-ci.yml` `sonarqube:check`（qualitygate.wait=true） | ✅ 已配置 |
| 代码复杂度 | SonarQube 复杂度指标 | 同上 | ✅ 已配置 |
| 重复代码检测 | SonarQube Duplications | 同上 | ✅ 已配置 |
| 覆盖率检查 | JaCoCo（后端≥80%）+ @vitest/coverage-v8（前端） | `.gitlab-ci.yml` `test:*` | ✅ 已配置 |
| 性能分析（循环复杂度/N+1查询） | 未配置专项工具 | - | ⚠️ 规划中（可引入 p6spy / Arthas） |
| 安全漏洞扫描 | 委托至 `security-scanner`（Semgrep SAST） | `.gitlab-ci.yml` `security:sast` | ✅ 已委托 |

**调用约定**：
- 当需要了解"项目有哪些代码质量检查"时，查询本 Skill 的映射表
- 当需要实际执行代码分析时，由 `.gitlab-ci.yml` 的 `lint:*` 和 `sonarqube:check` jobs 自动完成
- 本 Skill 的 `action: "analyze"` API 仅作为编排接口保留，实际分析由 Checkstyle/SpotBugs/ESLint/SonarQube 完成
- 性能分析需引入专项工具后启用

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
