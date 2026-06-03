# AI Open Platform 控制台(前端)

Vue 3 + Vite + TypeScript 单页应用,是 AI Open Platform 的管理控制台。UI 基于 shadcn-vue(Reka UI + Tailwind v4),状态管理用 Pinia,路由用 Vue Router,HTTP 用 axios。

## 技术栈

| 用途 | 选型 |
| --- | --- |
| 框架 / 构建 | Vue 3、Vite 6、TypeScript |
| 状态 / 路由 | Pinia、Vue Router |
| HTTP | axios(封装在 `src/api/http.ts`) |
| UI 组件 | shadcn-vue(Reka UI 基元 + Tailwind v4),组件落在 `src/components/ui/` 并在仓库内维护 |
| 图标 / 表单 / 提示 | `@lucide/vue`、vee-validate + zod、vue-sonner |

## 前置条件

- Node.js ≥ 18(推荐 20+)与 npm。
- 后端 Spring Boot 跑在 `localhost:8321`(开发态 Vite 把 `/api`、`/v1`、`/anthropic` 反向代理到它)。后端启动方式见仓库根目录 `CLAUDE.md` / `install.md`。

## 常用命令

```bash
cd frontend
npm install        # 安装依赖

npm run dev        # 开发服务器,端口 5173,自动代理后端接口
npm run typecheck  # 仅类型检查(vue-tsc --noEmit)
npm run build      # 先 vue-tsc --noEmit 再 vite build —— 类型检查是构建的一部分
npm run preview    # 本地预览 build 产物
```

> `build` 会因**类型错误**以及 **未使用的变量/参数**(`tsconfig.json` 开启了 `noUnusedLocals` / `noUnusedParameters`)而失败。仓库内没有前端测试运行器,`npm run build` 通过即视为前端可交付。

## 开发流程

1. **起后端**:确保 Spring Boot 在 `localhost:8321` 可访问(需要可连的 MySQL)。
2. **起前端**:`npm run dev`,浏览器打开 `http://localhost:5173`。所有接口走相对路径,由 Vite 代理转发,无需关心跨域。
3. **登录**:首次启动后端会自动创建管理员 `admin / admin`。
4. **改代码**:按下方目录约定新增/修改页面、接口、组件;Vite 热更新即时生效。
5. **提交前自检**:跑 `npm run build`(等于类型检查 + 打包)。**构建不过不要提交**。
6. **联调真实部署**:容器化部署时前端会被打进后端 jar、同源由 8321 提供;本地无需关心,详见根目录 `CLAUDE.md` 的「Docker / deployment」。

## 目录结构

```
src/
├── api/            # 各业务模块的接口封装(channel/apikey/model/user…),统一调用 http.ts
│   └── http.ts     # axios 实例 + 拦截器 + get/post/put/del(关键,见下)
├── components/ui/  # shadcn-vue 组件,仓库内拥有、可直接编辑
├── lib/utils.ts    # cn() 合并 class 的工具
├── router/         # 路由表与守卫(登录校验、meta.admin 鉴权)
├── stores/         # Pinia store(auth.ts 持久化 token+user 到 localStorage)
├── styles/main.css # 主题(shadcn CSS 变量)与 Tailwind 入口
├── types/          # 全局 TS 类型(含 Result envelope)
├── utils/          # constants.ts(localStorage key)、format.ts 等
└── views/          # 页面级组件(Login/Dashboard/Channels/Keys/Models/Logs/Users…)
```

## 关键约定

### 调接口走 `src/api/http.ts`

后端控制台接口统一用 `Result<T>` 包裹,`http.ts` 的 `get/post/put/del` 已**拆包**并返回 `.data.data`,业务代码直接拿到纯数据。错误处理对齐后端两套约定:

- **HTTP 401**(JWT 缺失/失效)→ 清空登录态并跳转 `/login`。
- **HTTP 200 但 `body.code !== 200`**(业务异常,如校验失败、归属 403)→ 抛 `ApiError` 并弹错误 toast。
- **真实 HTTP 403**(角色越权)→ 弹 toast,但不登出。

新增接口时在 `src/api/<模块>.ts` 里调 `get/post/...`,**不要**直接 new axios 或写绝对地址。

### 加 UI 组件

用 CLI 生成,不要手搓也不要再引第三方 UI 库:

```bash
npx shadcn-vue@latest add <组件名>   # 例如 dropdown-menu、tooltip
```

组件会生成到 `src/components/ui/`,之后**在仓库内维护**(可直接改)。`components.json` 已配置好(new-york 风格、lucide 图标)。

### 主题与样式

- 主题色板与 shadcn CSS 变量都在 `src/styles/main.css`;改色改间距在这里调,不要散落到各组件。
- 合并 class 用 `cn()`(`@/lib/utils`)。

### 提示与确认

- 提示统一 vue-sonner:`import { toast } from 'vue-sonner'`,任意位置(含 axios 拦截器)可直接 `toast.success/error/info`。`<Toaster/>` 已在 `App.vue` 挂载一次。
- 确认弹窗用 shadcn `AlertDialog`,在各页面内声明式使用(自持 `showDelete` 等状态),没有全局单例。

### 鉴权与路由

- `stores/auth.ts` 把 token+user 存 localStorage,key 定义在 `utils/constants.ts`。
- 路由守卫做登录校验与 `meta.admin` 鉴权;`AppLayout.vue` 会隐藏管理员菜单。**前端的角色判断只是体验优化,后端每个管理操作都会再校验一次。**
```
