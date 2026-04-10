# Chat App

`apps/chat/` 是用户对话页，使用 Next.js App Router 构建。

## 本地开发

先启动后端服务，再运行前端开发服务器：

```powershell
# 启动后端（Java + Python）
cd .\kb-assistant-ms
.\scripts\setup-python.ps1 -Service embedder  # 首次运行，初始化 embedding 服务虚拟环境
.\scripts\ensure-postgres.ps1 # 确认 PostgreSQL 已启动
```

另开终端：

```powershell
cd .\kb-assistant-ms\apps\chat
pnpm dev
```

默认地址：

- 前端开发服务器：`http://localhost:3000`
- 网关 API 目标：`http://localhost:8080`

`next dev` 会自动将 `/api/*` 代理到网关，修改聊天页代码后立即生效，无需重建 `kb-gateway`。

## 生产构建

三个前端 app 统一由根目录脚本一起构建：

```powershell
cd .\kb-assistant-ms
.\scripts\build.ps1
```

构建产物输出到 `services/kb-gateway/src/main/resources/static/chat-app`，由网关静态托管。

## 前端 App 总览

| App | 路径 | 状态 | 网关托管路径 |
|---|---|---|---|
| chat（对话页） | `apps/chat/` | 已上线 | `/static/chat-app` |
| admin（管理后台） | `apps/admin/` | 已上线 | `/static/admin` |
| home（首页） | `apps/home/` | 已上线 | `/static`（根） |

## 共享工具

- `apps/chat/lib/app-config.js`：路由与环境配置
- `apps/chat/lib/kb-api.js`：封装所有后端 API 调用
