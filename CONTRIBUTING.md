# 贡献指南

感谢你为 AI Open Platform 做贡献!本文档说明如何搭建开发环境、提交改动,以及必须遵守的若干**红线约束**。动代码前请先读 [CLAUDE.md](./CLAUDE.md)(权威指南:架构全景、构建陷阱、模块约定)与 [AGENTS.md](./AGENTS.md)(索引与最关键约束)。

---

## 开发环境

完整安装步骤见 [install.md](./docs/install.md)。最小开发环境:

- JDK 17+(已在 JDK 24 验证,注意下方 Lombok 约束)、Maven 3.9+、MySQL 8
- Node.js 18+(仅前端需要)

后端运行需要**可达的 MySQL**:本地无 MySQL 时只能做 `mvn compile` / `package` 校验,无法真正启动(`DataInitializer` 启动即写库)。

```bash
# 后端
mvn -q -DskipTests compile        # 快速编译校验
mvn spring-boot:run               # 本地运行(需 MySQL)

# 前端
cd frontend && npm install
npm run dev                       # :5173,代理 /api /v1 /anthropic -> localhost:8321
```

---

## 开发流程

1. 从 `main` 切出特性分支:`git checkout -b feat/xxx`(`feat/` `fix/` `docs/` `refactor/` 等前缀)。
2. 小步提交,提交信息见下方[提交规范](#提交规范)。
3. **提交前自检**(见下方[提交前检查](#提交前检查))必须全绿。
4. 推送并发起 PR,描述清楚**改了什么、为什么**;涉及接口或行为变化时同步更新相关文档([api.md](./docs/api.md) / [README.md](README.md) / [install.md](./docs/install.md))。

---

## 提交前检查

提交 PR 前,改动涉及的部分必须本地通过:

```bash
# 后端:至少编译通过;改了较多代码建议跑完整打包
mvn -q -DskipTests compile
mvn -DskipTests clean package     # 产出 target/ai-open-platform-0.0.1-SNAPSHOT.jar

# 前端:build 内含 vue-tsc 类型检查,且开启 noUnusedLocals / noUnusedParameters
cd frontend && npm run build      # 类型错误、未用变量都会导致失败
npm run typecheck                 # 仅类型检查
```

> 目前**没有测试**:后端尚无测试用例(将来约定 `mvn test -Dtest=ClassName#methodName`),前端无测试运行器。因此请务必本地手动验证你的改动,UI 改动请在浏览器里实际走一遍主流程。

---

## 提交规范

- 主题行用**祈使句**、简短(参考现有历史:`Add ...`、`Migrate ...`、`Fix ...`),中英文皆可,但同一条信息内保持一致。
- 一次提交聚焦一件事;body 解释**为什么**而非罗列改了哪些行。
- 不要提交密钥或本地配置:`.env`、真实数据库密码、JWT 密钥等(`.env` 已在 `.gitignore`,提交 `.env.example`)。

---

## 代码规范

### 后端(Spring Boot + MyBatis-Plus)

- 模块按 `com.aiopen.platform.modules.<feature>.{entity,mapper,dto,service,service.impl,controller}` 组织;跨切面代码放 `common/` `config/` `security/`。
- 实体一般继承 `BaseEntity`(`createTime`/`updateTime` 自动填充、`deleted` 逻辑删除);**`Log` 与 `Ability` 是刻意的例外**,不继承、表也无 `deleted` 列,新增类似派生表时沿用此约定。
- `group`、`model` 是 SQL 保留字,实体字段映射要加反引号:``@TableField("`group`")``。
- 控制器分页用 MyBatis-Plus `Page` 并转 `PageResult.of(page)`,**不要直接序列化 `IPage`**。
- 敏感写字段(如 `user.password`、`channel.apiKey`)必须 `@JsonIgnore`,绝不回传。
- 授权用方法级 `@PreAuthorize("hasRole('ADMIN')")`,当前用户用 `@AuthenticationPrincipal AuthUser` 注入;非角色类的归属校验(如用户只能动自己的 key)保留为 `BusinessException(FORBIDDEN)`。

### 前端(Vue 3 + shadcn-vue)

- UI 基于 **shadcn-vue**(Reka UI + Tailwind v4),组件生成在 `src/components/ui/` 并**在仓库内自持、可直接改**。扩展请 `npx shadcn-vue@latest add <name>` 新增组件,**不要再叠加其他 UI 组件库**(Element Plus / Ant Design Vue 等)。
- 图标用 `@lucide/vue`,表单用 vee-validate + zod,提示用 vue-sonner(`toast.success/error/info`,可在 axios 拦截器等非组件环境调用),确认框用 shadcn `AlertDialog` 按视图声明式使用(无全局 toast/confirm 单例)。
- 所有 API 调用走相对路径(依赖 dev 代理),经 `src/api/http.ts` 的 `get/post/put/del`——它们已**拆掉 `Result<T>` 信封**返回 `.data.data`,新接口请复用这层而非裸 axios。
- `cn()` 助手在 `src/lib/utils.ts`;主题变量在 `src/styles/main.css`。

---

## 红线 / 标准决定(细节见 [CLAUDE.md](./CLAUDE.md))

这些是**不可随意改动**的架构约束,改动前请先在 issue/PR 里讨论:

- **不加计费 / 配额**:平台只在 `log` 表记录 token 用量,不计价、不限额。
- **`schema.sql` 是 `DROP` + `CREATE`**:重跑会清库,无迁移脚本;演进既有库请**手写 `ALTER`**,别依赖重跑建表。
- **Lombok 注解处理是关键配置**:`pom.xml` 中 `maven-compiler-plugin` 的 `annotationProcessorPaths`(Lombok 1.18.38)**不可删**——JDK 23+ 删掉它会让所有 Lombok 生成的 getter/构造器静默失效。
- **两套独立鉴权域**不可混用:`/api/**`(Spring Security + JWT,响应 `Result` 包裹,业务错误是 HTTP 200 + `code != 200`,角色无权限是真实 HTTP 403)vs `/v1`、`/anthropic`(平台 API Key,透传上游、保持 OpenAI / Anthropic 原始形状,错误用 `RelayController` 内的局部 `@ExceptionHandler`)。
- **模型由渠道派生**:没有 `model` 表;可用模型 = 启用渠道 `models` 列的去重并集;转发选路走派生的 `ability` 表(`group × models` 展开),**不要扫描 `channel.models` 字符串**。渠道增改/状态变更要调 `rebuildForChannel`,删除调 `deleteForChannel`,且与渠道写操作同事务。
- **SPA 回退守卫**:`WebConfig` 对以 `api/`、`v1/`、`anthropic/` 开头的路径返回 `null`(不返回 `index.html`)以保住真实路由/404,且 `static/index.html` 缺失时也返回 `null`——这两个判断都别删。

---

## 报告问题

提交 issue 时请尽量包含:复现步骤、期望与实际行为、相关日志(`docker compose logs -f app` 或后端控制台)、运行环境(JDK / OS / 部署方式)。
