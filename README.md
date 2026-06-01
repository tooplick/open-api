# AI Open Platform(后端)

基于 **Spring Boot 3 + MyBatis-Plus + MySQL 8** 的 AI 模型聚合开放平台后端。

统一接入多个大模型服务商,对外提供 **OpenAI 兼容** 的标准接口,支持渠道管理、模型管理、API Key、额度与日志统计。用户无需分别对接各家服务商,只需调用本平台的统一接口。

> 后端 MVP 已完成;前端控制台(Vue3 + Vite + TS)已实现,见下方「前端控制台」。

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言/框架 | Java 17、Spring Boot 3.4.x |
| 持久层 | MyBatis-Plus 3.5.x |
| 数据库 | MySQL 8 |
| 鉴权 | 无状态 JWT(jjwt)+ BCrypt 密码加密 |
| 转发 | JDK 内置 `java.net.http.HttpClient`(支持流式 SSE) |

> 控制台接口使用 JWT 鉴权;转发接口(`/v1/**`)使用 API Key 鉴权,二者相互独立。

---

## 环境要求

- JDK 17 及以上(已在 JDK 24 上验证编译/打包)
- Maven 3.9+
- MySQL 8

---

## 快速开始

### 1. 初始化数据库

导入建表脚本(会创建库 `ai_open_platform` 并预置 3 个示例模型):

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

### 2. 修改数据库连接

编辑 `src/main/resources/application.yml` 中的 `spring.datasource`,改为你的 MySQL 账号密码。

JWT 密钥建议用环境变量覆盖(生产必须修改):

```bash
# Windows PowerShell
$env:AIOPEN_JWT_SECRET = "你的足够长的随机密钥(>=32字节)"
```

### 3. 启动

```bash
mvn spring-boot:run
```

或打包后运行:

```bash
mvn -DskipTests clean package
java -jar target/ai-open-platform-0.0.1-SNAPSHOT.jar
```

服务默认监听 `http://localhost:8080`。

### 4. 默认管理员

首次启动自动创建管理员账号(若不存在):

```
用户名: admin
密码:   admin
```

> 请登录后尽快修改密码。

---

## 使用流程(curl 示例)

### ① 登录拿 token

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
# 返回 data.token,后续控制台接口在 Header 携带: Authorization: Bearer <token>
```

### ② 管理员创建渠道(接入上游服务商)

```bash
curl -X POST http://localhost:8080/api/channels \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
        "name":"OpenAI官方",
        "type":"openai",
        "baseUrl":"https://api.openai.com",
        "apiKey":"sk-上游真实密钥",
        "models":"gpt-4o,gpt-4o-mini,gpt-3.5-turbo",
        "weight":1,
        "priority":0
      }'
```

> `baseUrl` 填上游根地址(不含 `/v1`),平台转发时会自动拼接请求路径。
> 同一模型可配置多个渠道:先按 `priority` 取最高,再在同优先级内按 `weight` 加权随机。

### ③ 创建调用用的 API Key

```bash
curl -X POST http://localhost:8080/api/keys \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"我的key"}'
# 返回 data.apiKey,形如 sk-xxxxxxxx...
```

### ④ 调用统一接口(OpenAI 兼容)

```bash
# 非流式
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <上一步的 sk- key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"你好"}]}'

# 流式
curl -N -X POST http://localhost:8080/v1/chat/completions \
  -H "Authorization: Bearer <sk- key>" \
  -H "Content-Type: application/json" \
  -d '{"model":"gpt-4o-mini","stream":true,"messages":[{"role":"user","content":"你好"}]}'
```

也可直接配合 OpenAI SDK:把 `base_url` 指向 `http://localhost:8080/v1`,`api_key` 用平台的 sk- key。

---

## 接口一览

### 控制台接口(`/api/**`,JWT 鉴权)

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 注册 | 公开 |
| POST | `/api/auth/login` | 登录 | 公开 |
| GET | `/api/user/me` | 当前用户信息 | 登录 |
| PUT | `/api/user/password` | 修改密码 | 登录 |
| GET | `/api/user/page` | 用户分页 | 管理员 |
| PUT | `/api/user/{id}/status` | 启用/禁用用户 | 管理员 |
| PUT | `/api/user/{id}/quota` | 设置用户额度 | 管理员 |
| GET | `/api/keys` | 我的 API Key 列表 | 登录 |
| POST | `/api/keys` | 创建 API Key | 登录 |
| PUT | `/api/keys/{id}/status` | 启用/禁用 | 本人/管理员 |
| DELETE | `/api/keys/{id}` | 删除 | 本人/管理员 |
| GET | `/api/channels/page` | 渠道分页 | 管理员 |
| GET | `/api/channels/{id}` | 渠道详情 | 管理员 |
| POST | `/api/channels` | 新建渠道 | 管理员 |
| PUT | `/api/channels/{id}` | 更新渠道 | 管理员 |
| PUT | `/api/channels/{id}/status` | 启用/禁用 | 管理员 |
| DELETE | `/api/channels/{id}` | 删除 | 管理员 |
| GET | `/api/models/page` | 模型分页 | 登录 |
| GET | `/api/models/enabled` | 启用模型列表 | 登录 |
| POST | `/api/models` | 新建模型 | 管理员 |
| PUT | `/api/models/{id}` | 更新模型 | 管理员 |
| DELETE | `/api/models/{id}` | 删除模型 | 管理员 |
| GET | `/api/logs/page` | 调用日志分页 | 登录(普通用户仅本人) |
| GET | `/api/logs/statistics` | 用量统计 | 登录(普通用户仅本人) |

### 转发接口(`/v1/**`,API Key 鉴权)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/v1/chat/completions` | 对话补全,支持 `stream` |
| GET | `/v1/models` | 可用模型列表 |

统一响应结构(控制台接口):`{ "code": 200, "message": "...", "data": ..., "timestamp": ... }`。
转发接口透传上游响应;错误以 OpenAI 格式返回:`{ "error": { "message": ..., "type": ..., "code": null } }`。

---

## 额度与计费

- 额度单位为抽象“点数”。模型表中 `prompt_price` / `completion_price` 为**每 token 消耗点数**。
- 单次消耗 = `prompt_tokens × prompt_price + completion_tokens × completion_price`(向上取整)。
- 预置示例模型价格为 0(即默认不计费);需要计费时由管理员设置价格,并通过 `/api/user/{id}/quota` 给用户充值额度。
- 调用前校验:用户 / API Key 额度若 `>0` 且已用尽则拒绝。调用成功后扣减实际消耗。
- 流式响应仅在上游返回 `usage`(如携带 `stream_options:{include_usage:true}`)时统计 token,否则记 0。

---

## 目录结构

```
src/main/java/com/aiopen/platform
├── AiOpenPlatformApplication.java
├── common/        # 统一响应 Result、分页、异常、基础实体
├── config/        # MyBatis-Plus、JWT 属性、CORS+拦截器、密码编码器、数据初始化
├── security/      # JwtUtil、UserContext、AuthInterceptor
└── modules/
    ├── user/      # 用户与鉴权
    ├── apikey/    # API Key
    ├── channel/   # 渠道
    ├── model/     # 模型
    ├── relay/     # 请求转发(OpenAI 兼容)
    └── log/       # 调用日志与统计
src/main/resources
├── application.yml
└── db/schema.sql  # 建表脚本

frontend/          # 控制台前端(Vue3 + Vite + TS,详见「前端控制台」)
├── src/api/       # axios 封装 + 各模块接口
├── src/stores/    # pinia(鉴权状态)
├── src/router/    # 路由 + 登录/管理员守卫
├── src/components/# 手写基础组件(布局、弹窗、分页、Toast…)
└── src/views/     # 各功能页面
```

---

## 前端控制台(Vue3 + Vite + TS)

控制台前端位于 `frontend/`,技术栈 **Vue 3 + Vite + TypeScript + Pinia + Vue Router + axios**,UI 组件全部手写,未引入 UI 组件库。

### 运行

```bash
cd frontend
npm install
npm run dev        # 开发服务器 http://localhost:5173
```

开发态已配置反向代理:`/api` 与 `/v1` 自动转发到后端 `http://localhost:8080`(见 `vite.config.ts`),前端统一用相对路径请求,无需关心跨域。请先启动后端。

### 构建

```bash
npm run build      # 先类型检查(vue-tsc)再打包到 frontend/dist
npm run preview    # 本地预览构建产物
```

将 `dist/` 交给任意静态服务器托管即可;生产环境需把 `/api`、`/v1` 反代到后端服务。

### 功能页面

| 页面 | 说明 | 权限 |
| --- | --- | --- |
| 登录 / 注册 | JWT 登录、注册新用户 | 公开 |
| 概览 | 用量统计(请求数 / Tokens / 额度)、账户额度、快捷入口 | 登录 |
| API Key | 创建 / 启停 / 删除 / 复制,设独立额度与过期时间 | 登录 |
| 模型 | 分页查看;管理员可增删改与定价 | 登录(改:管理员) |
| 渠道管理 | 接入上游服务商,增删改启停 | 管理员 |
| 用户管理 | 分页、启停、设额度 | 管理员 |
| 调用日志 | 多条件筛选 + 用量汇总 | 登录(普通用户仅本人) |
| 个人资料 | 账户信息、修改密码 | 登录 |

> 菜单按角色显示:管理员可见全部菜单与全局数据,普通用户仅见自助菜单与本人数据(前端隐藏入口,后端二次校验)。

---

## 安全与生产注意

- **务必修改** `AIOPEN_JWT_SECRET`,并使用足够长的随机值。
- API Key 当前以**明文**存储(网关需用其匹配请求);如有更高要求可改为哈希存储 + 仅创建时展示一次。
- CORS 默认放开所有来源,生产环境应收敛到前端域名。
- 转发上游密钥(`channel.api_key`)不对外返回(`@JsonIgnore`),为只写字段。

## 后续规划(MVP 之外)

- 前端控制台(Vue3 + Vite + TS)✅ 已实现
- 渠道连通性测试、模型重命名映射生效
- Redis:令牌黑名单、限流、额度缓存
- 更多端点(embeddings、images)与更多渠道类型(Azure、Anthropic)适配
```
