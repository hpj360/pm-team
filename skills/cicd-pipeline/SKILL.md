# Skill: CI/CD Pipeline

## 描述

CI/CD 流水线编排与文档层工具，支持从代码提交到部署的端到端自动化流程，覆盖构建、测试、打包、发布与回滚等全生命周期管理。

## 与现有 .gitlab-ci.yml 的关系（必读）

本项目已配置完整的 GitLab CI/CD 流水线（见 `.gitlab-ci.yml`），包含 8 个阶段：`checkout → lint → build → test → security → package → deploy → verify`。**本 Skill 是 CI/CD 的编排文档层与策略定义层，而非执行层。**

| Skill 角色 | 实际执行方 | 职责划分 |
|-----------|----------|---------|
| 编排策略定义（本 Skill） | `.gitlab-ci.yml` | 本 Skill 定义"应该做什么"，.gitlab-ci.yml 定义"实际怎么做" |
| 阶段顺序与依赖 | `.gitlab-ci.yml` 的 `stages` 与 `needs` | Skill 的 8 阶段模型与 .gitlab-ci.yml 完全对齐 |
| 工具选择（Maven/npm/Docker） | `.gitlab-ci.yml` 的 `image` 与 `script` | Skill 声明支持的工具，.gitlab-ci.yml 已选用 Maven + npm + Docker |
| 触发规则 | `.gitlab-ci.yml` 的 `workflow.rules` | Skill 定义触发策略，.gitlab-ci.yml 已配置 main/develop/feature/release 分支触发 |
| 覆盖率门禁 | JaCoCo（`JACOCO_MIN_COVERAGE: "0.80"`） | Skill 定义阈值，.gitlab-ci.yml 已实现 80% 门禁检查 |
| 质量门禁 | SonarQube Quality Gate | Skill 定义质量要求，.gitlab-ci.yml 已配置 `sonar.qualitygate.wait=true` |

**调用约定**：
- 当 Director 或 Operations Agent 需要了解"CI/CD 应该做什么"时，查询本 Skill 的文档与配置
- 当需要实际触发流水线时，由 Git push/MR 触发 `.gitlab-ci.yml`，而非调用本 Skill 的 `action: "trigger"` API
- 本 Skill 的 `action: "trigger"` / `action: "status"` / `action: "rollback"` API 仅作为编排接口保留，实际执行由 .gitlab-ci.yml 完成

## 阶段映射表

本 Skill 的 8 阶段与 `.gitlab-ci.yml` 的 job 一一对应：

| Skill 阶段 | .gitlab-ci.yml Job | 执行工具 | 门禁 |
|-----------|-------------------|---------|------|
| 1. Checkout | `checkout:verify` | alpine:3.18 | - |
| 2. Lint | `lint:backend` / `lint:frontend` | Maven checkstyle+spotbugs / ESLint+Prettier | fail-fast |
| 3. Build | `build:backend` / `build:frontend` | Maven compile / Vite build | - |
| 4. Test | `test:backend` / `test:frontend` / `sonarqube:check` | Maven test+JaCoCo / Vitest / SonarQube | 覆盖率≥80% |
| 5. Security | `security:dependency-scan` / `security:sast` / `security:secret-detect` | Trivy / Semgrep / Gitleaks | HIGH+CRITICAL fail |
| 6. Package | `package:backend:*` / `package:frontend` / `security:image-scan` | Docker build+push / Trivy image scan | 镜像扫描通过 |
| 7. Deploy | `deploy:staging` / `deploy:production` | kubectl rollout (K8s) | 手动审批(生产) |
| 8. Verify | `verify:health-check` / `verify:rollback` | curl 健康检查 / kubectl undo | 5分钟健康检查 |

## 功能

### 1. 代码检查 (Lint/代码风格)

对提交的代码进行规范性与风格检查，拦截低级错误与不一致风格。

**支持工具**:
- ESLint
- Prettier
- Checkstyle
- SpotBugs
- SonarQube
- Stylelint

### 2. 自动构建 (Maven/npm/Docker)

根据项目类型自动选择构建工具，生成可执行产物或镜像。

**支持构建工具**:
- Maven
- Gradle
- npm
- yarn
- Docker
- Vite

### 3. 自动测试触发

在流水线中自动触发单元测试、集成测试与端到端测试，并收集覆盖率。

**支持测试框架**:
- JUnit
- Jest
- Vitest
- Playwright
- Cypress

### 4. 制品管理 (Artifact 存储和版本)

对构建产物进行统一存储、版本化管理与生命周期管理。

**功能点**:
- 制品上传与归档
- 版本号管理 (Semantic Versioning)
- 制品元数据标记
- 制品仓库集成 (Nexus / Artifactory / Harbor)
- 老旧制品清理策略

### 5. 环境部署 (dev/staging/production)

将制品部署到不同环境，支持环境隔离与配置注入。

**支持环境**:
- dev (开发环境)
- staging (预发布环境)
- production (生产环境)

### 6. 回滚机制

支持快速回滚到历史稳定版本，保障线上稳定性。

**回滚方式**:
- 自动回滚（健康检查失败/指标异常时触发）
- 手动回滚
- 按版本号回滚
- 按提交回滚

### 7. 流水线状态追踪

实时追踪流水线执行状态，并提供可视化的阶段执行记录。

**追踪维度**:
- 流水线整体状态
- 各阶段执行状态与耗时
- 日志流式输出
- 历史执行记录
- 失败原因定位

## 流水线阶段

| 阶段 | 名称 | 说明 |
|------|------|------|
| 1 | Checkout | 代码检出，从仓库拉取指定分支代码 |
| 2 | Lint | 代码规范检查，校验风格与潜在问题 |
| 3 | Build | 编译构建，生成可执行产物或镜像 |
| 4 | Test | 自动化测试，执行单元/集成/E2E 测试 |
| 5 | Security | 安全扫描，检测依赖漏洞与代码安全问题 |
| 6 | Package | 打包制品，归档并上传至制品仓库 |
| 7 | Deploy | 环境部署，将制品发布到目标环境 |
| 8 | Verify | 部署验证，执行健康检查与冒烟测试 |

## 使用示例

### 触发流水线

```json
{
  "action": "trigger",
  "trigger": "push",
  "branch": "main",
  "config": {
    "stages": ["checkout", "lint", "build", "test", "package", "deploy"],
    "environment": "staging",
    "notifications": {
      "onFailure": ["slack", "email"],
      "onSuccess": ["slack"]
    }
  }
}
```

### 查询状态

```json
{
  "action": "status",
  "pipelineId": "pipe-001"
}
```

### 回滚

```json
{
  "action": "rollback",
  "environment": "production",
  "version": "v1.2.0"
}
```

### 配置流水线

```json
{
  "action": "configure",
  "pipeline": {
    "name": "backend-service-pipeline",
    "stages": [
      { "name": "checkout", "enabled": true },
      { "name": "lint", "enabled": true },
      { "name": "build", "enabled": true, "tool": "maven" },
      { "name": "test", "enabled": true, "framework": "junit" },
      { "name": "security", "enabled": true },
      { "name": "package", "enabled": true },
      { "name": "deploy", "enabled": true, "strategy": "blue-green" },
      { "name": "verify", "enabled": true }
    ],
    "triggers": [
      { "type": "push", "branch": "main" },
      { "type": "pull_request", "branch": "main" },
      { "type": "schedule", "cron": "0 2 * * *" }
    ]
  }
}
```

## 流水线状态

| 状态 | 说明 |
|------|------|
| `pending` | 流水线已创建，等待执行 |
| `running` | 流水线正在执行中 |
| `success` | 流水线全部阶段执行成功 |
| `failed` | 流水线执行失败（某阶段出错） |
| `canceled` | 流水线被手动取消 |

## 支持的构建工具

- **Maven**: Java 项目构建与依赖管理
- **Gradle**: Java/Groovy/Kotlin 项目构建
- **npm**: Node.js 包管理与构建
- **yarn**: Node.js 包管理（高效缓存）
- **Docker**: 容器镜像构建

## 部署策略

### 蓝绿部署 (Blue-Green)

同时维护两套完全相同的环境（蓝/绿），通过切换流量实现零停机发布。

### 滚动更新 (Rolling Update)

逐步替换旧版本实例，直到全部实例更新为新版本，适用于无状态服务。

### 金丝雀发布 (Canary Release)

将新版本先发布给小比例用户，根据指标分析逐步扩大流量比例，降低发布风险。

## 配置文件示例

```yaml
# pipeline.yaml - CI/CD 流水线配置示例
pipeline:
  name: backend-service-pipeline
  trigger:
    type: push
    branch: main
  environment: staging

  stages:
    - name: checkout
      enabled: true
      config:
        depth: 1

    - name: lint
      enabled: true
      config:
        tool: eslint
        failOnWarning: false

    - name: build
      enabled: true
      config:
        tool: maven
        goals: ["clean", "package", "-DskipTests"]
        cache: true

    - name: test
      enabled: true
      config:
        framework: junit
        coverage: true
        threshold: 80

    - name: security
      enabled: true
      config:
        scanners: ["dependency-check", "sonarqube"]
        failOnVulnerability: critical

    - name: package
      enabled: true
      config:
        registry: harbor.example.com
        image: myapp/backend-service
        tag: ${VERSION}

    - name: deploy
      enabled: true
      config:
        environment: staging
        strategy: rolling-update
        replicas: 3

    - name: verify
      enabled: true
      config:
        healthCheck: /actuator/health
        timeout: 60s
        retries: 5

  notifications:
    onSuccess:
      - slack: "#releases"
    onFailure:
      - slack: "#alerts"
      - email: ["devops@example.com"]

  rollback:
    auto: true
    onHealthCheckFail: true
    maxRetries: 2
```

## 输出格式

```json
{
  "pipelineId": "pipe-001",
  "status": "success",
  "branch": "main",
  "commit": "a1b2c3d",
  "version": "v1.2.0",
  "timestamp": "2026-07-26T10:30:00Z",
  "duration": "8m32s",
  "stages": [
    { "name": "checkout", "status": "success", "duration": "5s" },
    { "name": "lint", "status": "success", "duration": "20s" },
    { "name": "build", "status": "success", "duration": "3m12s" },
    { "name": "test", "status": "success", "duration": "2m45s" },
    { "name": "security", "status": "success", "duration": "45s" },
    { "name": "package", "status": "success", "duration": "30s" },
    { "name": "deploy", "status": "success", "duration": "1m05s" },
    { "name": "verify", "status": "success", "duration": "35s" }
  ],
  "artifact": {
    "name": "backend-service",
    "version": "v1.2.0",
    "registry": "harbor.example.com/myapp/backend-service:v1.2.0"
  }
}
```
