# AI Open Platform

聚合多家大模型服务商,对外提供**统一的 OpenAI 兼容接口**的开放平台网关。一次接入,即可用标准 OpenAI / Anthropic 协议调用背后任意渠道。

- 后端:Spring Boot 3 + MyBatis-Plus + MySQL 8
- 前端控制台:Vue 3 + Vite + TypeScript(组件手写,未引入 UI 组件库)

## 特性

- **OpenAI 兼容**:`/v1/chat/completions`、`/v1/models`,可直接配合 OpenAI SDK;并提供 **Anthropic 兼容入站**(`/anthropic/v1/messages`)。
- **多渠道聚合与智能选路**:同一模型可挂多个渠道,先按 `priority` 取最高,再在同优先级内按 `weight` 加权随机;渠道类型支持 OpenAI、Anthropic(适配器可扩展)。
- **分组隔离**:API Key 与渠道以 `group` 关联——key 只能路由到同组渠道,并可设模型白名单。
- **API Key 管理**:`sk-` 开头密钥,支持启停、过期、模型限制。
- **调用日志与统计**:记录每次转发的 token 用量(**不计费、不限额**)。
- **流式转发**:基于 JDK `HttpClient` 的 SSE 透传。

控制台含 仪表盘 / 渠道 / API Key / 模型 / 日志 / 用户 / 个人资料 等页面。

## 快速开始(Docker)

```bash
docker compose up -d --build
```

打开 `http://localhost:8321`,默认管理员 `admin / admin`(请尽快改密码)。可选配置见 `.env.example`;手动部署见 [install.md](install.md)。

## 架构一览

- **两套独立鉴权域**:控制台 `/api/**` 用 JWT、响应统一 `Result` 包裹;转发 `/v1/**`、`/anthropic/**` 用 API Key、响应是对应协议的原始 JSON(不包裹)。
- **模型由渠道聚合**:不单独维护模型表,可用模型 = 所有**启用渠道** `models` 字段的去重并集;路由经由派生的 `ability` 表(渠道 `group × models` 展开)做索引化选路。

## 文档

| 文档 | 内容 |
| --- | --- |
| [install.md](install.md) | 安装与运行(Docker / 手动部署) |
| [api.md](api.md) | 接口列表、鉴权与响应约定、curl 与 SDK 用法 |

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.4、MyBatis-Plus 3.5 |
| 数据库 | MySQL 8 |
| 鉴权 | JWT(jjwt)+ BCrypt |
| 转发 | JDK `java.net.http.HttpClient`(支持 SSE 流式) |
| 前端 | Vue 3、Vite、TypeScript、Pinia、Vue Router、axios |

## 许可证

本项目以 [MIT License](LICENSE) 发布。
