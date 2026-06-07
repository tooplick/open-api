# 配置说明

本文只说明运行后在控制台里需要配置的功能项。Docker 和本地运行的数据库、端口、JWT 密钥等基础配置见 [install.md](./install.md)。

## 入口

登录管理员账号后进入：

```text
系统设置
```

配置保存后立即生效，通常不需要重启服务。

## 邮件配置

邮件配置用于“邮箱验证码注册”。如果不启用邮箱注册，可以不配置 SMTP。

### 注册开关

在“注册设置”中：

- `开放注册`：总开关。关闭后所有注册方式都会被隐藏并拒绝。
- `邮箱验证码注册`：邮箱注册方式开关。需要先开启 `开放注册`。

### SMTP 字段

在“SMTP 邮件服务”中填写：

| 字段 | 说明 |
| --- | --- |
| SMTP 服务器 | 邮件服务地址，例如 `smtp.qq.com`、`smtp.gmail.com`、`smtp.office365.com` |
| 端口 | 常用 `587` 或 `465` |
| 账号 | 发信邮箱账号 |
| 密码 / 授权码 | 邮箱 SMTP 密码或应用授权码，不是所有邮箱都允许直接用登录密码 |
| 发件人 | 可留空；留空时默认使用账号地址 |
| 使用 SSL | 开启表示 465 隐式 SSL；关闭表示 587 STARTTLS |

### 常见填写方式

587 STARTTLS：

```text
SMTP 服务器: smtp.example.com
端口: 587
使用 SSL: 关闭
```

465 SSL：

```text
SMTP 服务器: smtp.example.com
端口: 465
使用 SSL: 开启
```

保存后，登录页会按公开设置显示邮箱注册入口。用户流程是：

1. 输入邮箱并发送验证码。
2. 输入用户名、密码、邮箱和验证码完成注册。

验证码为 6 位数字，10 分钟有效，同一邮箱 30 秒内只能发送一次。

## GitHub 配置

GitHub 配置用于“GitHub 直接登录/注册”。前端不会接触 GitHub Client Secret，OAuth code exchange 由后端完成。

### GitHub OAuth App

在 GitHub 创建 OAuth App 时填写：

| GitHub 字段 | 本地开发填写 | Docker / 生产填写 |
| --- | --- | --- |
| Application name | `openapi` | `openapi` 或你的产品名 |
| Homepage URL | `http://localhost:5173` | `http://localhost:8321` 或正式域名 |
| Application description | 可填 `AI Open Platform console login` | 可填 `AI Open Platform console login` |
| Authorization callback URL | `http://localhost:8321/api/auth/github/callback` | `https://你的域名/api/auth/github/callback` |

注意：本地前后端分离开发时，GitHub 的 `Authorization callback URL` 仍然填后端地址 `8321`，不要填 Vite 的 `5173`。

### 控制台字段

在“系统设置”中：

- 开启 `开放注册`。
- 开启 `GitHub 登录/注册`。
- 在“GitHub OAuth”中填写 GitHub OAuth App 的 `Client ID` 和 `Client Secret`。

本地开发时建议这样填：

```text
后端回调地址: http://localhost:8321/api/auth/github/callback
前端回调地址: http://localhost:5173/oauth/github/callback
```

Docker 或生产同源部署时：

```text
后端回调地址: https://你的域名/api/auth/github/callback
前端回调地址: 留空
```

前端回调地址留空时，后端会默认重定向到同源：

```text
/oauth/github/callback
```

### 登录流程

1. 登录页点击 GitHub。
2. 前端请求 `/api/auth/github/authorize-url` 获取 GitHub 授权地址。
3. GitHub 回调后端 `/api/auth/github/callback`。
4. 后端创建一次性 ticket，并重定向到前端 `/oauth/github/callback`。
5. 前端用 ticket 调 `/api/auth/github/exchange` 换取平台 JWT。

首次 GitHub 登录会创建普通用户账号；再次登录会通过 `github_id` 命中同一账号。

## 系统代理

系统代理用于后端发起的外部 HTTP 请求，包括：

- GitHub OAuth code 换 token、读取 GitHub 用户信息
- `/v1/chat/completions` 转发上游模型
- `/anthropic/v1/messages` 入站转换后请求上游模型
- 渠道管理里的“获取模型”请求

浏览器能访问外网不代表后端 JVM 或 Docker 容器也能访问外网。如果需要代理，在“系统设置”的“系统代理”中配置。

本地直接运行后端：

```text
启用出站 HTTP 代理: 开启
代理 Host: 127.0.0.1
代理端口: 7890
```

Docker 下如果代理运行在宿主机，容器内不能用 `127.0.0.1` 指向宿主机，应使用：

```text
启用出站 HTTP 代理: 开启
代理 Host: host.docker.internal
代理端口: 7890
```

如果你的 Docker 环境不能解析 `host.docker.internal`，需要在 `docker-compose.yml` 的 `app` 服务下补充：

```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

然后重启 app 容器。端口 `7890` 按你的本机代理端口修改。

### GitHub 后端网络要求

GitHub OAuth 的 code 换 token 是后端发起的请求，因此后端进程必须能访问：

```text
https://github.com/login/oauth/access_token
https://api.github.com/user
https://api.github.com/user/emails
```

浏览器能打开 GitHub 不代表后端 JVM 或 Docker 容器也能访问 GitHub。若日志中出现：

```text
java.net.http.HttpConnectTimeoutException: HTTP connect timed out
```

说明后端连 GitHub 超时，需要配置“系统代理”，或部署到能直连 GitHub 的网络。

## 既有数据库升级

如果是全新数据库，`schema.sql` 已包含所需字段。

如果是已有数据库，不要重跑 `schema.sql`，因为它是 `DROP` + `CREATE` 全量脚本。只执行下面的变更：

```sql
ALTER TABLE `user`
  ADD COLUMN `github_id` BIGINT DEFAULT NULL COMMENT 'GitHub user id',
  ADD COLUMN `github_login` VARCHAR(100) DEFAULT NULL COMMENT 'GitHub login',
  ADD COLUMN `avatar_url` VARCHAR(500) DEFAULT NULL COMMENT 'Avatar URL',
  ADD UNIQUE KEY `uk_github_id` (`github_id`);
```

邮件配置、GitHub OAuth 的 Client ID/Secret/回调地址、系统代理配置都存放在 `system_setting`，应用启动时会自动补默认项，不需要手动改表。
