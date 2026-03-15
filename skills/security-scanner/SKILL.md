# Skill: Security Scanner

## 描述

安全扫描工具，帮助安全工程师进行漏洞扫描和安全审计。

## 功能

### 1. 漏洞扫描

扫描系统中的安全漏洞。

**扫描类型**:
- Web应用扫描 (DAST)
- 代码安全扫描 (SAST)
- 依赖漏洞扫描 (SCA)
- 容器镜像扫描
- 配置安全扫描

### 2. 渗透测试

模拟攻击进行安全测试。

**测试类型**:
- SQL注入测试
- XSS测试
- CSRF测试
- 认证绕过测试
- 权限提升测试

### 3. 合规检查

检查是否符合安全合规要求。

**合规标准**:
- OWASP Top 10
- CWE Top 25
- PCI DSS
- GDPR
- 等保2.0

### 4. 安全报告

生成安全审计报告。

**报告内容**:
- 漏洞统计
- 风险评估
- 修复建议
- 合规状态

## 使用示例

### Web应用扫描

```json
{
  "action": "scan",
  "type": "web",
  "target": "https://example.com",
  "config": {
    "crawl": true,
    "auth": {
      "type": "form",
      "loginUrl": "/login",
      "username": "test",
      "password": "test"
    },
    "excludes": ["/logout"]
  }
}
```

### 代码安全扫描

```json
{
  "action": "scan",
  "type": "code",
  "target": "./src",
  "config": {
    "languages": ["javascript", "typescript", "python"],
    "rules": ["owasp-top-10", "cwe-top-25"],
    "severity": ["critical", "high", "medium"]
  }
}
```

### 依赖漏洞扫描

```json
{
  "action": "scan",
  "type": "dependency",
  "target": "./package.json",
  "config": {
    "checkTransitive": true,
    "ignoreDev": false,
    "severity": ["critical", "high"]
  }
}
```

### 容器镜像扫描

```json
{
  "action": "scan",
  "type": "container",
  "target": "myapp:latest",
  "config": {
    "checkBase": true,
    "checkPackages": true,
    "checkSecrets": true
  }
}
```

## 漏洞分类

### 按严重程度

| 级别 | CVSS分数 | 说明 | 处理时限 |
|------|----------|------|----------|
| Critical | 9.0-10.0 | 严重漏洞，可导致系统被完全控制 | 立即修复 |
| High | 7.0-8.9 | 高危漏洞，可导致敏感数据泄露 | 24小时内 |
| Medium | 4.0-6.9 | 中危漏洞，有一定安全风险 | 7天内 |
| Low | 0.1-3.9 | 低危漏洞，影响较小 | 30天内 |
| Info | 0 | 信息性发现 | 可选修复 |

### 按漏洞类型

| 类型 | 说明 | 检测方法 |
|------|------|----------|
| 注入漏洞 | SQL/命令/代码注入 | SAST + DAST |
| 认证问题 | 弱密码/会话管理 | DAST + 渗透测试 |
| XSS | 跨站脚本攻击 | DAST + SAST |
| CSRF | 跨站请求伪造 | DAST |
| 敏感数据泄露 | 数据暴露 | SAST + 配置检查 |
| 访问控制失效 | 越权访问 | 渗透测试 |
| 安全配置错误 | 配置不当 | 配置扫描 |

## 输出格式

```json
{
  "scanResult": {
    "scanId": "scan-20240315-001",
    "type": "web",
    "target": "https://example.com",
    "startTime": "2024-03-15T10:00:00Z",
    "endTime": "2024-03-15T10:30:00Z",
    "status": "completed"
  },
  "summary": {
    "total": 25,
    "critical": 2,
    "high": 5,
    "medium": 10,
    "low": 8
  },
  "vulnerabilities": [
    {
      "id": "VULN-001",
      "name": "SQL Injection",
      "severity": "critical",
      "cvss": 9.8,
      "location": "/api/users?id=1",
      "description": "存在SQL注入漏洞，可获取数据库数据",
      "evidence": "1' OR '1'='1",
      "remediation": "使用参数化查询",
      "references": [
        "https://owasp.org/www-community/attacks/SQL_Injection"
      ]
    }
  ],
  "compliance": {
    "owasp-top-10": {
      "status": "fail",
      "passed": 8,
      "failed": 2
    }
  }
}
```

## 配置

```json
{
  "scanners": {
    "web": {
      "tool": "zap",
      "timeout": "30m",
      "maxDepth": 5
    },
    "code": {
      "tool": "sonarqube",
      "languages": ["javascript", "typescript", "python", "java", "go"]
    },
    "dependency": {
      "tool": "trivy",
      "databases": ["nvd", "github"]
    },
    "container": {
      "tool": "trivy",
      "ignoreUnfixed": true
    }
  },
  "reporting": {
    "formats": ["json", "html", "pdf"],
    "includeEvidence": true,
    "includeRemediation": true
  },
  "notification": {
    "onComplete": true,
    "onCriticalFound": true,
    "channels": ["email", "feishu"]
  }
}
```
