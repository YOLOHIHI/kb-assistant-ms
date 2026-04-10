# Docker Compose

`deploy/docker/` 是当前仓库正式支持的源码构建部署路径。

目标是让第三方用户在 clone 后，只做一组明确的环境变量确认，就可以通过源码构建把默认栈拉起来。

## 默认栈

默认 `docker compose up -d --build` 会启动：

- `kb-db`
- `kb-embedder`
- `kb-index-service`
- `kb-doc-service`
- `kb-ai-service`
- `kb-gateway`

默认路径**不包含**：

- `kb-ocr-service`
- `kb-eureka-server`

它们都是可选模块，必须显式启用。

## 1. 准备

```powershell
cd .\kb-assistant-ms\deploy\docker
Copy-Item .\.env.example .\.env
```

`.env.example` 中只保留了安全占位值，不再提交旧的开发默认 Secret。

## 2. 核对 `.env`

需要重点确认：

- `KB_INTERNAL_TOKEN`
- `KB_DB_PASSWORD`
- `KB_BOOTSTRAP_ADMIN_USER`
- `KB_BOOTSTRAP_ADMIN_PASSWORD`
- `KB_CRYPTO_MASTER_KEY`
- `SILICONFLOW_API_KEY`
- `SILICONFLOW_MODEL`

规则：

- `KB_ALLOW_INSECURE_DEFAULTS` 默认应保持 `false`。
- `SILICONFLOW_API_KEY` 和 `SILICONFLOW_MODEL` 可以留空；系统会启动，但聊天会退化为基于检索片段的兜底回答。
- `KB_OCR_URL` 默认留空，表示文档服务不会调用 OCR。
- `EUREKA_ENABLED` 默认 `false`，Eureka 不属于默认启动路径。

## 3. 启动默认栈

```powershell
docker compose up -d --build
```

默认情况下，只有 `kb-gateway` 的 `8080` 会暴露到宿主机。

`kb-db` 以及其余内部服务保留在 Compose 网络内，避免与本机已有 PostgreSQL 或同名容器发生冲突。

常用排查命令：

```powershell
docker compose ps
docker compose logs -f kb-gateway
docker compose logs -f kb-index-service
docker compose logs -f kb-embedder
```

## 4. 启动可选模块

### 启用 OCR

OCR 采用 profile 激活，不会自动进入默认启动链路。

```powershell
$env:COMPOSE_PROFILES = 'ocr'
```

同时把 `.env` 中的 `KB_OCR_URL` 设置为：

```text
KB_OCR_URL=http://kb-ocr-service:8070
```

然后执行：

```powershell
docker compose up -d --build
```

### 启用 Eureka

Eureka 也是 profile 激活，并且需要显式打开客户端注册：

```text
EUREKA_ENABLED=true
EUREKA_SERVER_URL=http://kb-eureka-server:8761
```

```powershell
$env:COMPOSE_PROFILES = 'eureka'
docker compose up -d --build
```

如果同时启用 OCR 和 Eureka：

```powershell
$env:COMPOSE_PROFILES = 'ocr,eureka'
docker compose up -d --build
```

## 5. 启动后检查

### 页面与健康检查

```powershell
curl.exe http://localhost:8080/api/health
curl.exe http://localhost:8080/api/health/live
curl.exe http://localhost:8080/
curl.exe http://localhost:8080/chat
curl.exe http://localhost:8080/admin
```

说明：

- `/api/health` 是 readiness 检查，会串行探测 `ai/doc/index` 三个下游服务；全部可达时返回 `200`，任一服务不可达时返回 `503`
- `/api/health/live` 是 liveness 检查，不访问下游服务，网关进程存活时恒定返回 `200`

### 管理员登录检查

下面的命令会先拿 CSRF Cookie，再用你在 `.env` 中配置的管理员账号登录：

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-WebRequest -UseBasicParsing -WebSession $session http://localhost:8080/api/auth/csrf | Out-Null
$csrf = ($session.Cookies.GetCookies('http://localhost:8080/') | Where-Object { $_.Name -eq 'XSRF-TOKEN' }).Value
$loginBody = @{
  username = '<your KB_BOOTSTRAP_ADMIN_USER>'
  password = '<your KB_BOOTSTRAP_ADMIN_PASSWORD>'
} | ConvertTo-Json
Invoke-WebRequest `
  -UseBasicParsing `
  -WebSession $session `
  -Method POST `
  -ContentType 'application/json' `
  -Headers @{ 'X-XSRF-TOKEN' = $csrf } `
  -Body $loginBody `
  http://localhost:8080/api/auth/login
```

## 6. 访问地址

- 首页：`http://localhost:8080/`
- 聊天页：`http://localhost:8080/chat`
- 管理后台：`http://localhost:8080/admin`
- 健康检查（readiness）：`http://localhost:8080/api/health`
- 存活检查（liveness）：`http://localhost:8080/api/health/live`

## 7. 管理员与普通用户

系统首次启动后会自动创建一个管理员账号：

- 用户名来自 `KB_BOOTSTRAP_ADMIN_USER`
- 密码来自 `KB_BOOTSTRAP_ADMIN_PASSWORD`

普通用户不会自动创建，流程仍然是：

1. 调用 `POST /api/auth/register` 注册。
2. 管理员登录 `/admin` 审批。
3. 审批通过后再登录使用。
4. 如需暂停某个账号，可在 `/admin` 的“用户管理”里禁用；禁用不会删除数据，恢复后可继续使用原账号。

## 8. 数据持久化

Compose 默认只使用两个命名卷：

- `kb-db-data`
- `kb-doc-data`

分别保存：

- `kb-db-data`：PostgreSQL 数据
- `kb-doc-data`：文档上传原始文件

## 9. 已知行为

- 首次启动 `kb-embedder` 时会下载 embedding 模型，默认路径可能需要几分钟。
- 如果 PostgreSQL 已起来，但 `kb-gateway` 健康检查迟迟不过，优先看 Flyway migration 日志。
- 如果没有配置 `SILICONFLOW_*`，UI 仍可访问，但聊天回答会退化。
- OCR 和 Eureka 都是可选模块；默认路径不依赖它们。
