# AGENTS.md

> 面向 AI 编码代理(Cursor / Codex / 通用)的入口指南。本文件只做**索引**与**最关键约束**;完整权威说明见 [CLAUDE.md](./CLAUDE.md),改代码前请先读它。

## 项目一句话

AI Open Platform — 把多家 LLM 提供商聚合在单一 **OpenAI 兼容** API 背后的网关。后端 Spring Boot 3 + MyBatis-Plus + MySQL 8(`src/main/...`);前端控制台 Vue 3 + Vite + TypeScript(`frontend/`)。**无计费 / 配额** —— 只在 `log` 表记录 token 用量,不计价、不限额。

## 文档索引(先读这些)

| 文件 | 内容 |
| --- | --- |
| [CLAUDE.md](./CLAUDE.md) | **权威指南**:架构全景、构建陷阱、各模块约定。动代码前必读。 |
| [api.md](./docs/api.md) | 接口文档:控制台 `/api/**` 与转发 `/v1`、`/anthropic` 的鉴权、响应、端点清单。 |
| [TODO.md](./docs/TODO.md) | 待办路线图。 |
| [README.md](./README.md) | 端点表与 curl 演练(部分内容早于重构,可能已过时)。 |
| [install.md](./docs/install.md) | Docker 与手动(JDK / Maven / MySQL / Node)两种部署路径。 |

## 快速命令

后端(运行需可达的 MySQL):

```bash
mvn -q -DskipTests compile        # 快速编译校验
mvn -DskipTests clean package     # 打 fat jar -> target/ai-open-platform-0.0.1-SNAPSHOT.jar
mvn spring-boot:run               # 本地运行
```

前端:

```bash
cd frontend
npm install
npm run dev        # :5173,代理 /api /v1 /anthropic -> localhost:8321
npm run build      # 先 vue-tsc 类型检查,再 vite build(类型/未用变量报错会失败)
```

容器化:`docker compose up -d --build` —— 前后端合并为单镜像,UI 与 API 同在端口 **8321**。

## 红线 / 标准决定(细节见 CLAUDE.md)

- **UI 用 shadcn-vue**(Reka UI + Tailwind v4):组件自持在 `src/components/ui/`,扩展靠 `npx shadcn-vue@latest add <name>` 新增并自持组件,**不要再叠加其他 UI 组件库**(Element Plus / Ant Design Vue 等);Pinia / axios / Vue Router 照常可用。
- **不加计费 / 配额**:平台只记录用量。
- **`schema.sql` 是 `DROP` + `CREATE`**:重跑会清库,无迁移脚本;演进既有库请手写 `ALTER`。
- **Lombok 注解处理是关键配置**:`pom.xml` 中 `maven-compiler-plugin` 的 `annotationProcessorPaths`(Lombok 1.18.38)不可删 —— JDK 23+ 删掉它会让所有 Lombok 生成代码静默失效。
- **两套独立鉴权域**:`/api/**`(**Spring Security 6** + JWT,`Result` 包裹,业务错误是 HTTP 200 + `code != 200`,基于角色的无权限是真实 HTTP 403) vs `/v1`、`/anthropic`(平台 API Key,透传上游、保持 OpenAI / Anthropic 原始形状)。授权用方法级 `@PreAuthorize`,当前用户用 `@AuthenticationPrincipal AuthUser` 注入。
- **模型由渠道派生**:没有 `model` 表;可用模型 = 启用渠道 `models` 列的并集;转发路由走派生的 `ability` 表,不扫描 `channel.models` 字符串。
- **SPA 回退守卫**:`WebConfig` 对以 `api/`、`v1/`、`anthropic/` 开头的路径返回 `null`(不返回 `index.html`),保住真实路由 / 404,别动。
