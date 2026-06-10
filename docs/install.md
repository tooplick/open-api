# 安装与运行

AI Open Platform 提供三种安装方式:**直接拉取预构建镜像(推荐)**、**从源码构建** 与 **手动部署**。

---

## 方式一:直接拉取预构建镜像(推荐)

本项目通过 GitHub Actions 在每次推送到 `main` 分支时自动构建并发布 Docker 镜像到 Docker Hub,无需本地构建环境(JDK/Maven/Node)。

### 一键启动

```bash
git clone https://github.com/tooplick/open-api.git && cd open-api
docker compose -f docker-compose.prebuilt.yml up -d
```

### 自定义配置(可选)

```bash
cp .env.example .env
```

按需修改 `.env` 后重新启动:

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `AIOPEN_JWT_SECRET` | JWT 签名密钥,**生产必须改** | `change-me-please-...` |
| `MYSQL_PASSWORD` | MySQL root 密码 | `root` |
| `APP_PORT` | 对外暴露端口 | `8321` |

### 指定版本

```bash
docker pull tooplick/open-api:sha-<commit>
# 编辑 docker-compose.prebuilt.yml 中 image 字段
```

### 日常管理

```bash
docker compose -f docker-compose.prebuilt.yml logs -f app    # 跟踪后端日志
docker compose -f docker-compose.prebuilt.yml down            # 停止,保留数据
docker compose -f docker-compose.prebuilt.yml down -v         # 停止并清空数据库卷(重新建表)
```

- 控制台 / API / 转发统一入口:`http://localhost:8321`
- 默认管理员:`admin / admin`(首次启动自动创建,请尽快改密码)
- 数据库:容器内 MySQL 8,首次用 `src/main/resources/db/schema.sql` 自动建表

### CI/CD 说明

本项目的 GitHub Actions 工作流(`.github/workflows/docker-publish.yml`)会在每次推送到 `main` 分支时:

1. 使用 Docker Buildx 构建多阶段镜像(前端 Node 构建 + 后端 Maven 构建 + JRE 运行时)
2. 推送到 Docker Hub,标签为 `latest` 和 `sha-<commit>`
3. 使用 GitHub Actions 缓存加速后续构建

如需手动触发构建,只需向 `main` 分支推送任意 commit。

---

## 方式二:从源码构建(推荐开发使用)

clone 到本地

```bash
git clone https://github.com/tooplick/open-api.git && cd open-api
```

起docker容器

```bash
docker compose up -d --build
```

- 控制台 / API / 转发统一入口:`http://localhost:8321`
- 默认管理员:`admin / admin`(首次启动自动创建,请尽快改密码)
- 数据库:容器内 MySQL 8,首次用 `src/main/resources/db/schema.sql` 自动建表

常用命令:

```bash
docker compose logs -f app    # 跟踪后端日志
docker compose down           # 停止,保留数据
docker compose down -v        # 停止并清空数据库卷(重新建表)
```

可选配置:复制 `.env.example` 为 `.env` 后修改,compose 会自动读取(不创建也能直接跑,用默认值):

```bash
cp .env.example .env
```

可配置项:`AIOPEN_JWT_SECRET`(JWT 密钥,**生产必须改**)、`MYSQL_PASSWORD`(数据库密码)、`APP_PORT`(对外端口)。

> 数据源由 compose 通过环境变量注入(`SPRING_DATASOURCE_*`),无需改 `application.yml`。

---

## 方式三:手动部署

### 前置环境

- JDK 17+(已在 JDK 24 上验证;见下方「JDK 24 注意事项」)
- Maven 3.9+
- MySQL 8
- Node.js 18+(仅前端需要)

### 1. 初始化数据库

```bash
mysql -u root -p < src/main/resources/db/schema.sql
```

会创建库 `ai_open_platform` 及全部表。脚本为 `DROP`+`CREATE`,**重复执行会清空数据**;演进已有库请手写 `ALTER`。

### 2. 配置数据源

编辑 `src/main/resources/application.yml` 的 `spring.datasource`,改成你的 MySQL 账号密码;或用环境变量覆盖(优先级更高,无需改文件):

```bash
# Windows PowerShell
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3306/ai_open_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
$env:SPRING_DATASOURCE_USERNAME = "root"
$env:SPRING_DATASOURCE_PASSWORD = "root"
$env:AIOPEN_JWT_SECRET = "至少32字节的随机密钥"   # 生产必须设置
```

### 3. 启动后端

```bash
mvn spring-boot:run
# 或打包后运行:
mvn -DskipTests clean package
java -jar target/ai-open-platform-0.0.1-SNAPSHOT.jar
```

后端监听 `http://localhost:8321`。

### 4. 启动前端(开发态)

```bash
cd frontend
npm install
npm run dev        # 开发服务器 http://localhost:5173
```

Vite 已把 `/api`、`/v1`、`/anthropic` 反向代理到后端 `http://localhost:8321`(见 `vite.config.ts`),前端用相对路径请求,无需关心跨域。**请先启动后端。**

若启用 GitHub OAuth 登录,GitHub OAuth App 的 Authorization callback URL 填后端地址:

```text
http://localhost:8321/api/auth/github/callback
```

本地 Vite 开发时,系统设置里的“前端回调地址”填:

```text
http://localhost:5173/oauth/github/callback
```

Docker 或生产同源部署时可留空,后端会默认重定向到同源 `/oauth/github/callback`。

前端生产构建:

```bash
npm run build      # 先 vue-tsc 类型检查,再打包到 frontend/dist
```

> 手动部署若也想让后端直接托管前端页面,可把 `frontend/dist` 拷到 `src/main/resources/static/` 后再打包后端(这正是 Docker 镜像的做法)。

---

## 验证

```bash
curl -X POST http://localhost:8321/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'
```

返回含 `data.token` 即成功。浏览器打开 `http://localhost:8321`(Docker)或 `http://localhost:5173`(本地前端)登录控制台。

---

## 常见问题

- **JDK 24 注意事项**:JDK 23+ 不再自动运行 classpath 上的注解处理器,Lombok 会失效。本项目已在 `pom.xml` 的 `maven-compiler-plugin` 显式配置 `annotationProcessorPaths`(Lombok 1.18.38),**请勿删除该配置**,否则所有 Lombok 生成的 getter/构造器都会消失。
- **启动报无法连接数据库**:应用启动即连 MySQL(`DataInitializer` 要写入 admin 账号),请确认 MySQL 可达且已执行建表脚本。本地无 MySQL 时只能 `mvn compile/package`,无法真正启动。
- **端口被占用**:默认端口 8321 定义在 `application.yml`(`server.port`);如需改动,记得同步 `frontend/vite.config.ts` 的代理目标与 `docker-compose.yml`、`Dockerfile` 的端口。
