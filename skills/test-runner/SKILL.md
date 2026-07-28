# Skill: Test Runner (已废弃 / DEPRECATED)

> **废弃说明**: 本 Skill 已于 v3.1.0 合并至 `automation-tester` Skill。
>
> **迁移指引**:
> - 所有调用方应改用 `./skills/automation-tester`
> - API 完全兼容：`action: "run"`、`type: "unit|integration|performance"` 等参数保持不变
> - 配置字段（`parallel`、`maxWorkers`、`retry`、`timeout`、`reporters`、`outputDir`）已并入 automation-tester 的配置
>
> **废弃原因**: `automation-tester` 是 `test-runner` 的功能超集，除原有 4 项功能外还提供 API 接口测试、UI 自动化测试、覆盖率分析、CI/CD 集成配置等扩展能力。保留两个 Skill 会导致职责重叠和调度歧义。
>
> 本文件保留仅为兼容性引用，实际功能请查阅 [`../automation-tester/SKILL.md`](../automation-tester/SKILL.md)。
