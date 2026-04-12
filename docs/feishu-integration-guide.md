# 飞书机器人接入指南

## 一、创建飞书应用

### 1. 访问飞书开放平台
- 打开 https://open.feishu.cn/app
- 使用飞书账号登录

### 2. 创建企业自建应用
- 点击「创建企业自建应用」
- 填写应用信息：
  - **应用名称**: PM Team Agent
  - **应用描述**: 产品全生命周期多角色协作AI助手
  - **应用图标**: 上传合适的图标

### 3. 获取应用凭证
创建完成后，在「凭证与基础信息」页面获取：
- **App ID**: 应用唯一标识
- **App Secret**: 应用密钥

## 二、配置机器人能力

### 1. 开通机器人功能
- 进入应用管理后台
- 找到「应用功能」→「机器人」
- 点击「启用机器人」
- 配置机器人信息：
  - **机器人名称**: PM Team Agent
  - **机器人描述**: 智能项目管理助手，支持需求分析、架构设计、代码开发等全流程协作
  - **机器人头像**: 上传头像图片

### 2. 配置权限
在「权限管理」中添加以下权限：

#### 消息权限
- `im:message` - 获取与发送消息
- `im:message:send_as_bot` - 以应用身份发消息
- `im:chat` - 获取群组信息
- `im:chat:readonly` - 读取群组信息

#### 用户权限
- `contact:user.base:readonly` - 获取用户基本信息

### 3. 配置事件订阅
- 进入「事件订阅」页面
- 点击「启用事件订阅」
- 配置请求网址：
  ```
  https://your-domain.com/feishu/webhook
  ```
- 添加事件：
  - `im.message.receive_v1` - 接收消息

### 4. 配置加密（可选但推荐）
- 在「事件订阅」页面配置：
  - **Encrypt Key**: 加密密钥
  - **Verification Token**: 验证令牌

## 三、部署后端服务

### 1. 配置环境变量
```bash
# 飞书应用配置
export FEISHU_APP_ID=your_app_id
export FEISHU_APP_SECRET=your_app_secret
export FEISHU_ENCRYPT_KEY=your_encrypt_key
export FEISHU_VERIFICATION_TOKEN=your_verification_token

# Agent服务配置
export AGENT_SERVICE_URL=http://localhost:8080
export NACOS_SERVER=localhost:8848
```

### 2. 启动服务
```bash
cd backend/feishu-service
mvn spring-boot:run
```

### 3. 验证服务
```bash
curl http://localhost:8090/actuator/health
```

## 四、发布应用

### 1. 配置可用范围
- 在「可用范围」中添加：
  - 部门/人员：选择可使用的成员
  - 群组：选择可添加的群组

### 2. 申请发布
- 点击「申请发布」
- 填写版本说明
- 等待管理员审核

### 3. 版本管理
- 发布后可在「版本管理」中管理版本
- 支持灰度发布和全量发布

## 五、使用机器人

### 1. 添加机器人
- 在飞书中搜索应用名称
- 点击「添加到群组」或「与机器人对话」

### 2. 开始对话
发送消息给机器人，例如：
```
帮我分析一个需求：用户需要实现文件上传功能
```

机器人会将消息转发给对应的Agent处理，并返回结果。

## 六、架构说明

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  飞书客户端  │ ←→  │  飞书开放平台     │ ←→  │  feishu-service │
└─────────────┘     └──────────────────┘     └────────┬────────┘
                                                       │
                                                       ↓
                                            ┌─────────────────────┐
                                            │   Agent Service     │
                                            │  (多Agent协作系统)   │
                                            └─────────────────────┘
```

### 消息流程
1. 用户在飞书中发送消息给机器人
2. 飞书开放平台通过Webhook推送事件到 `feishu-service`
3. `feishu-service` 解析消息，调用Agent服务
4. Agent服务处理请求，返回结果
5. `feishu-service` 将结果发送回飞书用户

## 七、支持的Agent角色

| Agent ID | 名称 | 职责 |
|----------|------|------|
| director | 项目总监 | 任务调度和协调 |
| requirement-analyst | 需求分析师 | 需求分析和PRD编写 |
| architect | 技术架构师 | 架构设计和技术选型 |
| product-designer | 产品设计师 | 原型设计和交互设计 |
| ui-designer | UI设计师 | 视觉设计 |
| backend-developer | 后端工程师 | 后端开发 |
| frontend-developer | 前端工程师 | 前端开发 |
| tester | 测试工程师 | 测试验证 |
| security-engineer | 安全工程师 | 安全审计 |
| code-reviewer | 代码审查员 | 代码质量审查 |

## 八、常见问题

### Q: 消息发送失败？
1. 检查App ID和App Secret是否正确
2. 检查权限配置是否完整
3. 查看服务日志排查问题

### Q: 无法接收消息？
1. 检查事件订阅是否启用
2. 验证Webhook地址是否可访问
3. 检查防火墙和网络配置

### Q: 如何切换不同的Agent？
可以通过消息前缀指定Agent：
```
@架构师 帮我设计一个微服务架构
@需求分析师 分析以下需求...
```

## 九、联系方式

如有问题，请联系开发团队或在GitHub提交Issue。
