# 红方文件汇聚平台 API 契约总览

> 本文档为网络安全红方文件汇聚平台前后端联调的契约源真理（Single Source of Truth）。
> 所有 REST API、gRPC 接口、Kafka 事件均以本目录下文件为准。
> 版本：v1.0  |  规范：OpenAPI 3.0.3 / gRPC proto3 / CloudEvents 1.0

---

## 一、服务清单

| 序号 | 服务名称          | 端口  | 职责                                                       | 技术栈                |
| ---- | ----------------- | ----- | ---------------------------------------------------------- | --------------------- |
| 1    | auth-service      | 8080  | 认证授权、用户/角色/权限管理、JWT 签发与校验               | Spring Boot + MyBatis |
| 2    | upload-service    | 8081  | 文件上传（单/分片）、秒传、下载、版本管理、分享           | Spring Boot + MinIO   |
| 3    | parse-service     | 8082  | 文件解析（文档/图片/二进制）、结构化提取                  | Spring Boot + Tika    |
| 4    | search-service    | 8083  | 混合检索（全文+元数据+向量）、搜索建议、热词              | Spring Boot + ES      |
| 5    | analyze-service   | 8084  | 实体识别、IOC 提取、沙箱分析、威胁情报关联                 | Spring Boot + Python  |
| 6    | profile-service   | 8085  | 目标管理、画像生成、关系图谱                               | Spring Boot + Neo4j   |
| 7    | feishu-service    | 8086  | 飞书消息推送、Agent 集成、Webhook 回调                    | Spring Boot           |
| 8    | task-service      | 8090  | 任务编排、状态机管理、时间线追踪                           | Spring Boot           |
| 9    | notification-service | 8091 | 站内信、邮件、飞书、短信多通道通知                         | Spring Boot + Kafka   |
| 10   | report-service    | 8092  | 报告生成、模板管理、导出（PDF/Word/HTML）                  | Spring Boot + Freemarker |
| -    | common            | -     | 公共库：Result/PageResult/异常/工具/JWT/上下文             | Java Library          |

> 注：feishu-service 的 OpenAPI 由飞书平台能力封装，未在本目录单独定义；其外部契约见 `docs/feishu-integration-guide.md`。

---

## 二、通用规范

### 2.1 API 版本控制

- 采用 **URL 路径版本**：`/api/v1/...`
- 重大不兼容变更升主版本：`/api/v2/...`
- 旧版本至少保留 2 个版本的兼容期

### 2.2 统一响应格式

所有 REST 接口统一返回如下 JSON 结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { },
  "timestamp": 1785086825000
}
```

| 字段       | 类型    | 说明                                   |
| ---------- | ------- | -------------------------------------- |
| code       | integer | 响应码，200 表示成功，其余为错误码     |
| message    | string  | 响应消息，可用于前端直接展示           |
| data       | any     | 响应数据，失败时为 null                |
| timestamp  | integer | 服务器时间戳（毫秒）                   |

### 2.3 统一分页格式

所有列表接口的 `data` 字段统一采用如下分页结构：

```json
{
  "records": [ ],
  "total": 128,
  "page": 1,
  "size": 20
}
```

| 字段     | 类型    | 说明                       |
| -------- | ------- | -------------------------- |
| records  | array   | 当前页数据列表             |
| total    | integer | 总记录数                   |
| page     | integer | 当前页码（从 1 开始）      |
| size     | integer | 每页大小（默认 20，最大 100）|

分页请求参数统一为 query：`?page=1&size=20`。

### 2.4 认证方式

- 认证方案：**Bearer JWT**
- 国密算法：JWT 签名使用 **SM2**（非对称）+ **SM4**（内容加密，敏感字段）
- 请求头：
  ```
  Authorization: Bearer <accessToken>
  X-Trace-Id: <uuid>           // 链路追踪 ID，可选
  X-Tenant-Id: <tenantId>      // 租户 ID，多租户场景必填
  ```
- Token 有效期：accessToken 2h，refreshToken 7d
- 刷新机制：accessToken 过期后调用 `POST /api/v1/auth/refresh` 携带 refreshToken 换取新 token

### 2.5 限流策略

| 服务               | 限流阈值        | 限流维度       | 超限响应 |
| ------------------ | --------------- | -------------- | -------- |
| upload-service     | 1000 QPS        | 单租户         | 429      |
| search-service     | 2000 QPS        | 单租户         | 429      |
| parse-service      | 500 QPS         | 单租户         | 429      |
| analyze-service    | 200 QPS         | 单租户         | 429      |
| 其他服务           | 500 QPS         | 单租户         | 429      |

限流响应头：
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1785086825
Retry-After: 1
```

---

## 三、错误码规范

### 3.1 HTTP 通用错误码

| code | 含义           | 触发场景                         |
| ---- | -------------- | -------------------------------- |
| 200  | 成功           | 请求处理成功                     |
| 400  | 参数错误       | 请求参数校验失败                 |
| 401  | 未认证         | 未携带或携带无效 Token           |
| 403  | 无权限         | 已认证但无访问权限               |
| 404  | 不存在         | 资源不存在                       |
| 405  | 方法不支持     | HTTP 方法与路由不匹配            |
| 409  | 资源冲突       | 唯一约束冲突、状态冲突           |
| 429  | 请求过于频繁   | 触发限流                         |
| 500  | 服务器错误     | 服务内部异常                     |
| 503  | 服务不可用     | 服务降级或维护中                 |
| 504  | 网关超时       | 上游服务超时                     |

### 3.2 业务错误码（5 位分段）

业务错误码采用 5 位数字，按服务域分段：

| 段位       | 服务域                | 说明                       |
| ---------- | --------------------- | -------------------------- |
| 10000-10999 | auth-service         | 认证/用户/角色/权限        |
| 11000-11999 | auth-service (扩展)  | MFA/SSO/Token 扩展         |
| 20000-20999 | upload-service       | 文件上传/下载/版本         |
| 21000-21999 | upload-service (扩展)| 分片/秒传/分享扩展         |
| 30000-30999 | parse-service        | 文件解析                   |
| 40000-40999 | search-service       | 检索/索引/向量             |
| 50000-50999 | analyze-service      | 分析/实体/IOC/沙箱         |
| 60000-60999 | profile-service      | 目标/画像/图谱             |
| 70000-70999 | task-service         | 任务/状态机/时间线         |
| 80000-80999 | notification-service | 通知/通道/模板             |
| 90000-90999 | report-service       | 报告/模板/导出             |
| 99000-99999 | system               | 系统/公共/网关             |

常用业务错误码示例：

| code   | 名称                    | 含义                       |
| ------ | ----------------------- | -------------------------- |
| 10001  | LOGIN_FAILED            | 用户名或密码错误           |
| 10002  | USER_EXISTS             | 用户已存在                 |
| 10003  | USER_NOT_FOUND          | 用户不存在                 |
| 10004  | PASSWORD_ERROR          | 密码错误                   |
| 10005  | ACCOUNT_DISABLED        | 账号已被禁用               |
| 10006  | TOKEN_INVALID           | Token 无效或已过期         |
| 10007  | CAPTCHA_ERROR           | 验证码错误                 |
| 10008  | MFA_REQUIRED            | 需要二次验证               |
| 10009  | MFA_CODE_ERROR          | MFA 验证码错误             |
| 10010  | REFRESH_TOKEN_INVALID   | 刷新令牌无效               |
| 20001  | FILE_NOT_FOUND          | 文件不存在                 |
| 20002  | FILE_UPLOAD_FAILED      | 文件上传失败               |
| 20003  | FILE_SIZE_EXCEEDED      | 文件大小超限               |
| 20004  | FILE_TYPE_NOT_SUPPORTED | 文件类型不支持             |
| 20005  | FILE_PARSE_FAILED       | 文件解析失败（向上抛出）   |
| 20006  | FILE_EXISTS             | 文件已存在（秒传命中）     |
| 20007  | FILE_DOWNLOAD_FAILED    | 文件下载失败               |
| 20008  | CHUNK_MERGE_FAILED      | 分片合并失败               |
| 20009  | SHARE_LINK_EXPIRED      | 分享链接已过期             |
| 30001  | PARSE_NOT_SUPPORTED     | 文件类型不支持解析         |
| 30002  | PARSE_TASK_NOT_FOUND    | 解析任务不存在             |
| 30003  | PARSE_RESULT_EMPTY      | 解析结果为空               |
| 40001  | SEARCH_FAILED           | 检索失败                   |
| 40002  | INDEX_CREATE_FAILED     | 索引创建失败               |
| 40003  | INDEX_DELETE_FAILED     | 索引删除失败               |
| 40004  | VECTOR_INDEX_FAILED     | 向量索引失败               |
| 50001  | ANALYZE_FAILED          | 分析任务失败               |
| 50002  | ANALYZE_TASK_NOT_FOUND  | 分析任务不存在             |
| 50003  | SANDBOX_ERROR           | 沙箱执行异常               |
| 50004  | IOC_EXTRACT_FAILED      | IOC 提取失败               |
| 60001  | TARGET_NOT_FOUND        | 目标不存在                 |
| 60002  | PROFILE_GENERATE_FAILED | 画像生成失败               |
| 60003  | GRAPH_BUILD_FAILED      | 关系图谱构建失败           |
| 70001  | TASK_NOT_FOUND          | 任务不存在                 |
| 70002  | TASK_STATUS_INVALID     | 任务状态不允许该操作       |
| 70003  | TASK_DEPENDENCY_FAILED  | 任务依赖未满足             |
| 80001  | NOTIFICATION_SEND_FAILED| 通知发送失败               |
| 80002  | CHANNEL_NOT_CONFIGURED  | 通知通道未配置             |
| 90001  | REPORT_GENERATE_FAILED  | 报告生成失败               |
| 90002  | TEMPLATE_NOT_FOUND      | 报告模板不存在             |
| 99001  | SYSTEM_MAINTENANCE      | 系统维护中                 |
| 99002  | DEPENDENCY_SERVICE_DOWN | 依赖服务不可用             |

### 3.3 错误响应示例

```json
{
  "code": 10001,
  "message": "用户名或密码错误",
  "data": null,
  "timestamp": 1785086825000
}
```

字段校验错误时，`data` 携带字段级错误明细：

```json
{
  "code": 400,
  "message": "参数错误",
  "data": {
    "fields": [
      { "field": "username", "message": "不能为空" },
      { "field": "password", "message": "长度必须在 8-32 位之间" }
    ]
  },
  "timestamp": 1785086825000
}
```

---

## 四、目录结构

```
docs/api-contracts/
├── README.md                    # 本文件：契约总览
├── auth-service.yaml            # 认证服务 OpenAPI 3.0
├── upload-service.yaml          # 文件上传服务 OpenAPI 3.0
├── parse-service.yaml           # 文件解析服务 OpenAPI 3.0
├── search-service.yaml          # 检索服务 OpenAPI 3.0
├── analyze-service.yaml         # 分析服务 OpenAPI 3.0
├── profile-service.yaml         # 画像服务 OpenAPI 3.0
├── task-service.yaml            # 任务管理服务 OpenAPI 3.0
├── notification-service.yaml    # 通知服务 OpenAPI 3.0
├── report-service.yaml          # 报告服务 OpenAPI 3.0
└── events.md                    # Kafka 事件契约（CloudEvents）

proto/
├── common.proto                 # 通用消息：Result/PageRequest/PageResult/Empty
├── auth.proto                   # 认证服务 gRPC
├── file.proto                   # 文件服务 gRPC
├── task.proto                   # 任务服务 gRPC
└── notification.proto           # 通知服务 gRPC
```

---

## 五、契约使用约定

1. **前后端联调**：前端依据 OpenAPI YAML 生成 TypeScript 类型和请求客户端；后端依据 YAML 实现接口签名与字段。
2. **跨服务调用**：服务间高性能调用优先使用 gRPC（见 `proto/`），异步解耦使用 Kafka 事件（见 `events.md`）。
3. **变更管理**：任何契约变更必须先修改本目录文件并提交 MR，经架构评审通过后再实施代码改动。
4. **兼容性**：新增字段视为兼容变更；删除字段、修改字段类型、修改语义视为破坏性变更，需升版本。
5. **Mock 与联调**：前端可基于 OpenAPI YAML 启动 Mock Server（如 Prism），在后端就绪前完成联调。

---

## 六、参考实现

- 统一响应封装：[Result.java](../../backend/common/src/main/java/com/redteam/common/result/Result.java)
- 分页响应封装：[PageResult.java](../../backend/common/src/main/java/com/redteam/common/result/PageResult.java)
- 错误码枚举：[ResultCode.java](../../backend/common/src/main/java/com/redteam/common/result/ResultCode.java)
- 全局异常处理：[GlobalExceptionHandler.java](../../backend/common/src/main/java/com/redteam/common/exception/GlobalExceptionHandler.java)

> 注：现有 `ResultCode.java` 使用 4 位错误码（1001-5002），本契约升级为 5 位分段（10001-99999）。后端需在迁移期内同时支持，最终统一至 5 位。
