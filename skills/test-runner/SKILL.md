# Skill: Test Runner

## 描述

自动化测试执行工具，帮助测试工程师执行各类测试并生成报告。

## 功能

### 1. 单元测试执行

执行单元测试并收集覆盖率数据。

**支持框架**:
- Jest
- Vitest
- PyTest
- JUnit
- Go Test

### 2. 集成测试执行

执行集成测试和端到端测试。

**支持框架**:
- Cypress
- Playwright
- Selenium
- Postman/Newman

### 3. 性能测试执行

执行性能和负载测试。

**支持工具**:
- k6
- JMeter
- Locust
- Artillery

### 4. 测试报告生成

生成标准化的测试报告。

**报告内容**:
- 测试统计
- 通过/失败详情
- 覆盖率报告
- 性能指标
- 缺陷列表

## 使用示例

### 执行单元测试

```json
{
  "action": "run",
  "type": "unit",
  "config": {
    "framework": "jest",
    "coverage": true,
    "threshold": 80
  }
}
```

### 执行端到端测试

```json
{
  "action": "run",
  "type": "e2e",
  "config": {
    "framework": "playwright",
    "browsers": ["chromium", "firefox"],
    "headless": true
  }
}
```

### 执行性能测试

```json
{
  "action": "run",
  "type": "performance",
  "config": {
    "tool": "k6",
    "vus": 100,
    "duration": "5m",
    "thresholds": {
      "http_req_duration": ["p(95)<500"],
      "http_req_failed": ["rate<0.01"]
    }
  }
}
```

## 输出格式

```json
{
  "summary": {
    "total": 150,
    "passed": 145,
    "failed": 3,
    "skipped": 2,
    "duration": "45.2s",
    "coverage": "85.5%"
  },
  "results": [
    {
      "suite": "User Authentication",
      "tests": [
        {
          "name": "should login successfully",
          "status": "passed",
          "duration": "120ms"
        },
        {
          "name": "should fail with invalid credentials",
          "status": "passed",
          "duration": "95ms"
        }
      ]
    }
  ],
  "failures": [
    {
      "suite": "Payment Processing",
      "test": "should process refund",
      "error": "Timeout exceeded",
      "stack": "..."
    }
  ]
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
  "outputDir": "./test-results"
}
```
