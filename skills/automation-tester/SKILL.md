# Skill: Automation Tester

## 描述

自动化测试框架，支持多种测试类型的自动执行和报告生成。面向测试工程师与 CI/CD 流水线，提供从单元测试到端到端测试、从接口契约验证到性能压测的一站式自动化能力，并输出结构化测试报告与覆盖率分析。

> **合并说明**: 本 Skill 已合并原 `test-runner` Skill 的全部功能（单元测试执行、集成测试执行、性能测试执行、测试报告生成）。`test-runner` 已废弃，所有调用方应迁移至本 Skill。本 Skill 兼容 test-runner 的全部 API（action: "run", type: "unit|integration|performance"）和配置字段。

## 功能

### 1. 单元测试自动执行

执行函数级别的单元测试并收集结果。

**支持框架**:
- JUnit (Java)
- Jest (JavaScript/TypeScript)
- PyTest (Python)
- Vitest (Vite 项目)
- Go Test (Go)

### 2. 集成测试自动执行

执行模块间集成测试，验证组件协作行为。

**支持框架**:
- Spring Boot Test
- Jest + Supertest
- PyTest + pytest-integration
- TestContainers

### 3. API 接口测试

对 REST API 进行接口契约验证与回归测试。

**支持工具**:
- Postman / Newman
- REST Assured
- Supertest
- HTTPie

### 4. UI 自动化测试

执行浏览器端到端 UI 自动化测试。

**支持框架**:
- Selenium
- Playwright
- Cypress
- Puppeteer

### 5. 性能测试

执行压力测试与负载测试，评估系统性能瓶颈。

**支持工具**:
- JMeter
- Gatling
- k6
- Locust
- Artillery

### 6. 测试覆盖率分析

采集并汇总代码覆盖率数据，支持多维度统计。

**覆盖维度**:
- 行覆盖率 (Line)
- 分支覆盖率 (Branch)
- 函数覆盖率 (Function)
- 语句覆盖率 (Statement)

### 7. 测试报告生成

生成标准化、可视化的测试报告，支持多种输出格式。

**报告内容**:
- 测试概要统计
- 详细用例结果
- 覆盖率报告
- 失败用例分析
- 性能指标趋势

## 测试类型

| 类型 | 范围 | 目标 | 典型工具 |
|------|------|------|----------|
| 单元测试 | 函数级 | 验证单一函数逻辑正确性 | JUnit / Jest / PyTest |
| 集成测试 | 模块间 | 验证模块集成后的协作行为 | Spring Boot Test / TestContainers |
| API 测试 | 接口契约 | 验证 REST API 请求/响应契约 | Newman / REST Assured |
| E2E 测试 | 端到端 | 验证完整用户业务流程 | Playwright / Selenium / Cypress |
| 性能测试 | 系统级 | 评估压力/负载下的系统表现 | JMeter / Gatling / k6 |

## 使用示例

### 运行单元测试

```json
{
  "action": "run",
  "type": "unit",
  "config": {
    "framework": "junit",
    "path": "./backend",
    "coverage": true
  }
}
```

### 运行 API 测试

```json
{
  "action": "run",
  "type": "api",
  "config": {
    "baseUrl": "http://localhost:8080",
    "testCases": [
      {
        "name": "用户登录",
        "method": "POST",
        "path": "/api/auth/login",
        "body": { "username": "test", "password": "test123" },
        "expect": { "status": 200, "body": { "code": 0 } }
      }
    ]
  }
}
```

### 运行 E2E 测试

```json
{
  "action": "run",
  "type": "e2e",
  "config": {
    "browser": "chrome",
    "scenarios": [
      {
        "name": "登录并上传文件",
        "steps": [
          { "action": "goto", "url": "/login" },
          { "action": "fill", "selector": "#username", "value": "test" },
          { "action": "click", "selector": "#submit" },
          { "action": "assert", "selector": ".dashboard", "visible": true }
        ]
      }
    ]
  }
}
```

### 生成测试报告

```json
{
  "action": "report",
  "format": "html",
  "includeCoverage": true
}
```

## 测试结果状态

| 状态 | 说明 |
|------|------|
| `passed` | 测试通过，断言全部成功 |
| `failed` | 测试失败，断言不匹配或业务逻辑错误 |
| `skipped` | 测试被跳过，未实际执行 |
| `error` | 测试执行出错，如环境异常、超时、崩溃 |

## 输出格式

```json
{
  "summary": {
    "total": 200,
    "passed": 185,
    "failed": 8,
    "skipped": 5,
    "error": 2,
    "duration": "125.6s",
    "passRate": "92.5%",
    "coverage": {
      "line": "85.2%",
      "branch": "78.4%",
      "function": "90.1%",
      "statement": "85.5%"
    }
  },
  "details": [
    {
      "suite": "AuthService 登录测试",
      "type": "unit",
      "tests": [
        {
          "name": "should login successfully",
          "status": "passed",
          "duration": "120ms"
        },
        {
          "name": "should reject invalid credentials",
          "status": "passed",
          "duration": "95ms"
        }
      ]
    }
  ],
  "failures": [
    {
      "suite": "FileUpload 上传测试",
      "test": "should upload large file",
      "status": "failed",
      "error": "Expected status 200 but got 413",
      "stack": "..."
    },
    {
      "suite": "SearchService 搜索测试",
      "test": "should handle timeout",
      "status": "error",
      "error": "Timeout exceeded 30000ms",
      "stack": "..."
    }
  ]
}
```

## 测试报告格式

测试报告按以下结构组织输出：

1. **测试概要**：总数、通过/失败/跳过/错误数、耗时、通过率
2. **详细结果**：按测试套件分组的用例明细与执行状态
3. **覆盖率**：行/分支/函数/语句覆盖率，未覆盖文件清单
4. **失败分析**：失败用例的错误信息、堆栈、复现步骤与修复建议

**支持输出格式**:
- HTML（可视化报告，含图表）
- JSON（机器可读，便于集成）
- XML（兼容 JUnit 报告格式）
- Markdown（轻量文本报告）

## CI/CD 集成配置

```json
{
  "ci": {
    "platform": "github-actions",
    "triggers": {
      "onPush": true,
      "onPullRequest": true,
      "schedule": "0 2 * * *"
    },
    "stages": [
      {
        "name": "unit-test",
        "type": "unit",
        "failFast": true,
        "threshold": {
          "passRate": 95,
          "coverage": 80
        }
      },
      {
        "name": "integration-test",
        "type": "integration",
        "dependsOn": "unit-test"
      },
      {
        "name": "api-test",
        "type": "api",
        "dependsOn": "integration-test",
        "config": {
          "baseUrl": "http://localhost:8080",
          "env": "staging"
        }
      },
      {
        "name": "e2e-test",
        "type": "e2e",
        "dependsOn": "api-test",
        "config": {
          "browser": "chrome",
          "headless": true
        }
      },
      {
        "name": "performance-test",
        "type": "performance",
        "dependsOn": "e2e-test",
        "config": {
          "tool": "k6",
          "vus": 100,
          "duration": "5m"
        }
      }
    ],
    "reporting": {
      "formats": ["html", "json", "junit"],
      "outputDir": "./test-results",
      "publishTo": "artifacts"
    },
    "notification": {
      "onFailure": true,
      "channels": ["feishu", "email"]
    }
  }
}
```

## 配置

```json
{
  "parallel": true,
  "maxWorkers": 4,
  "retry": 2,
  "timeout": 30000,
  "reporters": ["console", "html", "junit"],
  "outputDir": "./test-results",
  "coverage": {
    "enabled": true,
    "threshold": {
      "line": 80,
      "branch": 70,
      "function": 80
    },
    "exclude": ["**/test/**", "**/mock/**", "**/*.config.*"]
  },
  "environments": {
    "dev": { "baseUrl": "http://localhost:8080" },
    "staging": { "baseUrl": "https://staging.example.com" },
    "production": { "baseUrl": "https://api.example.com" }
  }
}
```
