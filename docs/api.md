# 接口文档

平台分为两个**相互独立**的接口域,鉴权方式与响应格式都不同。

- **控制台接口** `/api/**`:Spring Security + JWT 鉴权,响应统一用 `Result` 包裹。
- **转发接口** `/v1/**`、`/anthropic/**`:平台 API Key 鉴权,透传上游、保持对应协议的原始结构。

默认服务地址 `http://localhost:8321`(见 `application.yml` 的 `server.port`)。

---

## 鉴权与响应约定

### 控制台接口(`/api/**`)

- **鉴权**:先登录拿 JWT,后续请求带 `Authorization: Bearer <jwt>`;仅 `/api/auth/login`、`/api/auth/register`、`/api/auth/email-code`、`/api/auth/email-register` 公开。登录由 Spring Security 的 `AuthenticationManager` 认证,管理员接口用方法级 `@PreAuthorize("hasRole('ADMIN')")` 鉴权。
- **响应**:统一结构

  ```json
  { "code": 200, "message": "success", "data": {}, "timestamp": 1730000000000 }
  ```

- **错误**:业务失败可能是 **HTTP 200 但 `code != 200`**(参数校验、资源不存在、**非角色类**的无权限如「只能操作自己的资源」等),需按 `code` 判断;基于角色的无权限(`@PreAuthorize`)是**真实 HTTP 403**;`401` 表示未登录 / JWT 失效。两类错误体都是 `Result` 结构,`message` 可直接展示。

### 转发接口(`/v1/**`、`/anthropic/**`)

- **鉴权**:平台 API Key(`sk-` 开头)。
  - `/v1`:`Authorization: Bearer sk-...`
  - `/anthropic`:`x-api-key: sk-...`(也兼容 `Authorization: Bearer sk-...`)
- **响应**:**不**做 `Result` 包裹,透传上游;流式为 SSE。
- **错误**:`/v1` 用 OpenAI 形状 `{"error":{"message","type","code"}}`;`/anthropic` 用 Anthropic 错误格式。
- **路由**:用 API Key 的 `group` 匹配同组渠道;若 key 设了模型白名单,请求模型不在其中则拒绝。

---

## 控制台接口一览(`/api/**`,JWT)

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 注册普通用户(账号密码) | 公开 |
| POST | `/api/auth/email-code` | 发送注册邮箱验证码 | 公开 |
| POST | `/api/auth/email-register` | 邮箱验证码注册 | 公开 |
| POST | `/api/auth/login` | 登录,返回 `{ token, user }` | 公开 |
| GET | `/api/user/me` | 当前登录用户信息 | 登录 |
| PUT | `/api/user/password` | 修改自己的密码 | 登录 |
| GET | `/api/user/page` | 用户分页(`?current&size&username`) | 管理员 |
| PUT | `/api/user/{id}/status` | 启用/禁用用户(`?status=1\|0`) | 管理员 |
| GET | `/api/keys` | 我的 API Key 列表 | 登录 |
| POST | `/api/keys` | 创建 API Key | 登录 |
| PUT | `/api/keys/{id}/status` | 启用/禁用(`?status=1\|0`) | 本人/管理员 |
| DELETE | `/api/keys/{id}` | 删除 API Key | 本人/管理员 |
| GET | `/api/channels/page` | 渠道分页(`?current&size&name`) | 管理员 |
| GET | `/api/channels/{id}` | 渠道详情 | 管理员 |
| POST | `/api/channels` | 新建渠道 | 管理员 |
| POST | `/api/channels/fetch-models` | 探测上游可用模型 | 管理员 |
| PUT | `/api/channels/{id}` | 更新渠道 | 管理员 |
| PUT | `/api/channels/{id}/status` | 启用/禁用(`?status=1\|0`) | 管理员 |
| DELETE | `/api/channels/{id}` | 删除渠道 | 管理员 |
| GET | `/api/models` | 所有可用模型(`string[]`) | 登录 |
| GET | `/api/logs/page` | 调用日志分页 | 登录(普通用户仅本人) |
| GET | `/api/logs/statistics` | 用量统计 | 登录(普通用户仅本人) |

> `api_key`、`channel.apiKey` 等敏感写字段为 `@JsonIgnore`,不会在响应里回传;编辑渠道需重新填写上游密钥。

### 邮箱验证码注册流程

两步,均为公开接口;需管理员先在「系统设置」开启邮箱注册并配置 SMTP 邮件服务:

1. **发送验证码** `POST /api/auth/email-code`,体 `{ "email": "user@example.com" }`。
2. **提交注册** `POST /api/auth/email-register`,体 `{ "username", "password", "email", "code" }` —— 仍需用户名 + 密码,验证码仅校验邮箱所有权(并非无密码登录)。

验证码为 6 位数字、10 分钟内有效、校验成功即失效;同一邮箱 30 秒内只能发送一次。相关业务错误(HTTP 200 + `code`):`1007` 邮箱已注册、`1008` 验证码错误或已过期、`1009` 发送过于频繁、`1010` 邮件服务未配置、`1011` 邮件发送失败。

```bash
# ① 发送验证码到邮箱
curl -X POST http://localhost:8321/api/auth/email-code \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com"}'

# ② 用收到的验证码完成注册
curl -X POST http://localhost:8321/api/auth/email-register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"secret123","email":"user@example.com","code":"123456"}'
```

## 转发接口(OpenAI 兼容,`/v1/**`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/v1/chat/completions` | 对话补全,支持 `stream` |
| GET | `/v1/models` | 可用模型列表(按 key 的 `group` + 白名单过滤) |

## Anthropic 兼容入站(`/anthropic/**`)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/anthropic/v1/messages` | Anthropic Messages,支持 `stream` |
| GET | `/anthropic/v1/models` | 可用模型列表(Anthropic 格式) |

> 客户端把 base_url 设为 `.../anthropic`,Anthropic SDK 会自动拼出 `/anthropic/v1/messages`。

---

## 使用流程(curl 示例)

### ① 登录拿 token

```bash
curl -X POST http://localhost:8321/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
# 返回 data.token,后续控制台接口带 Header: Authorization: Bearer <token>
```

### ② 管理员创建渠道(接入上游服务商)

```bash
curl -X POST http://localhost:8321/api/channels \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{
        "name":"OpenAI 官方",
        "type":"openai",
        "baseUrl":"https://api.openai.com",
        "apiKey":"sk-上游真实密钥",
        "models":"gpt-4o,gpt-4o-mini",
        "group":"default",
        "weight":1,
        "priority":0
      }'
```

字段说明:

- `baseUrl` 填上游根地址(**不含** `/v1`),转发时自动拼接路径。
- `type`:`openai` / `anthropic`(决定用哪个适配器)。
- `apiKey`:必填;支持**多个 key 换行分隔**,转发时随机取一个。
- `models`:该渠道支持的模型,逗号分隔。
- `group`:逗号分隔,可属多组。
- `modelMapping`(可选):JSON,把请求模型名重命名为上游模型名。
- 同一模型挂多个渠道时:先按 `priority` 取最高,再在同优先级内按 `weight` 加权随机。

### ③ 创建调用用的 API Key

```bash
curl -X POST http://localhost:8321/api/keys \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"name":"我的 key","group":"default"}'
# 返回 data.apiKey,形如 sk-xxxxxxxx...
# 可选:"models":"gpt-4o-mini" 设白名单;"expireTime":"2026-12-31T23:59:59" 设过期
```

### ④ 调用统一接口(OpenAI 兼容)

```bash
# 非流式
curl -X POST http://localhost:8321/v1/chat/completions \
  -H "Authorization: Bearer sk-<平台 key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"你好"}]}'

# 流式(SSE)
curl -N -X POST http://localhost:8321/v1/chat/completions \
  -H "Authorization: Bearer sk-<平台 key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4o-mini","stream":true,"messages":[{"role":"user","content":"你好"}]}'
```

### ⑤ 调用 Anthropic 兼容接口

```bash
curl -X POST http://localhost:8321/anthropic/v1/messages \
  -H "x-api-key: sk-<平台 key>" \
  -H "anthropic-version: 2023-06-01" \
  -H "Content-Type: application/json" \
  -d '{"model":"claude-3-5-sonnet-20241022","max_tokens":1024,"messages":[{"role":"user","content":"你好"}]}'
```

---

## 配合 SDK

- **OpenAI SDK**:`base_url` 指向 `http://localhost:8321/v1`,`api_key` 用平台的 `sk-` key。
- **Anthropic SDK**:`base_url` 指向 `http://localhost:8321/anthropic`,`api_key` 用平台的 `sk-` key。
