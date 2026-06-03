# AI Open Platform

聚合多家大模型服务商,对外提供**统一的 OpenAI 兼容接口**的开放平台网关。一次接入,即可用标准 OpenAI / Anthropic 协议调用背后任意渠道。

- 后端:Spring Boot 3 + MyBatis-Plus + MySQL 8
- 前端控制台:Vue 3 + Vite + TypeScript,UI 基于 shadcn-vue(Reka UI + Tailwind v4)

## 特性

- **OpenAI 兼容**:`/v1/chat/completions`、`/v1/models`,可直接配合 OpenAI SDK,并提供 **Anthropic 兼容入站**(`/anthropic/v1/messages`)。
- **多渠道聚合与智能选路**:同一模型可挂多个渠道,先按 `priority` 取最高,再在同优先级内按 `weight` 加权随机,渠道类型支持 OpenAI、Anthropic(适配器可扩展)。
- **分组隔离**:API Key 与渠道以 `group` 关联——key 只能路由到同组渠道,并可设模型白名单。
- **API Key 管理**:`sk-` 开头密钥,支持启停、过期、模型限制。
- **调用日志与统计**:记录每次转发的 token 用量(**不计费、不限额**)。
- **流式转发**:基于 JDK `HttpClient` 的 SSE 透传。

控制台含 仪表盘 / 渠道 / API Key / 模型 / 日志 / 用户 / 个人资料 等页面。

## 快速开始(Docker)

直接拉取镜像(推荐,无需本地构建):

```bash
docker pull tooplick/open-api:latest
```

或从源码构建:

```bash
git clone https://github.com/tooplick/open-api.git && cd open-api
docker compose up -d --build
```

打开 `http://localhost:8321`,默认管理员 `admin / admin`(请尽快改密码)。可选配置见 `.env.example`;  

> 每次推送到 `main` 分支时,GitHub Actions 会自动构建并发布 Docker 镜像到 Docker Hub。  
> 详见 [install.md](/docs/install.md#方式一直接拉取预构建镜像推荐)。

## 架构一览

- **两套独立鉴权域**:控制台 `/api/**` 用 **Spring Security + JWT**、响应统一 `Result` 包裹;转发 `/v1/**`、`/anthropic/**` 用 API Key、响应是对应协议的原始 JSON(不包裹)。
- **模型由渠道聚合**:不单独维护模型表,可用模型 = 所有**启用渠道** `models` 字段的去重并集;路由经由派生的 `ability` 表(渠道 `group × models` 展开)做索引化选路。

## 文档

| 文档 | 内容 |
| --- | --- |
| [install.md](/docs/install.md) | 安装与运行(Docker / 手动部署) |
| [api.md](/docs/api.md) | 接口列表、鉴权与响应约定、curl 与 SDK 用法 |
| [TODO.md](/docs/TODO.md) | 待办事项与功能规划 |

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.4、MyBatis-Plus 3.5 |
| 数据库 | MySQL 8 |
| 鉴权 | Spring Security 6 + JWT(jjwt)+ BCrypt |
| 转发 | JDK `java.net.http.HttpClient`(支持 SSE 流式) |
| 前端 | Vue 3、Vite、TypeScript、Pinia、Vue Router、axios、shadcn-vue(Reka UI + Tailwind v4) |

## 许可证

本项目以 [MIT License](LICENSE) 发布。
