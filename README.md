# KB Assistant - Microservices

一个面向课程设计 / 毕设演示的企业知识库 RAG 微服务项目。

项目以“可运行、可演示、可公开交付”为目标，采用 `gateway + doc-service + index-service + ai-service + python embedder (+ optional ocr)` 的结构。

当前对外推荐的交付路径有两条：

- `deploy/docker/`：源码构建 + Docker Compose，本地演示最直接
- `deploy/k8s/overlays/demo`：GHCR 镜像 + Kustomize，适合集群部署

OCR 与 Eureka 都是可选模块；`deploy/k8s/00*.yaml` 这类 flat YAML 只保留为历史参考。

## 项目现状

- 后端采用 Maven 多模块 + Spring Boot 3.2 / Java 21。
- 网关服务负责静态页面、用户鉴权、管理员后台、模型/Provider 管理与对内转发。
- 文档服务负责上传、解析、分块，并把结果写入索引服务。
- 索引服务负责知识库管理、BM25 + 向量混合检索，以及 embedding 调用。
- AI 服务负责 RAG 编排、会话持久化和大模型调用。
- Python `kb-embedder` 提供本地 embedding HTTP 接口；`kb-ocr-service` 提供可选 OCR。
- 可选的 `kb-eureka-server` 提供 Spring Cloud Eureka 服务注册中心，通过 `EUREKA_ENABLED` 环境变量开关，默认关闭。
- 当前对外推荐的 happy path 有两条：`deploy/docker/` 的源码构建路径，以及 `deploy/k8s/overlays/demo` 的 GHCR + Kustomize 路径；仓库仍保留 legacy flat YAML 作为历史参考，但不再是推荐入口。

## 当前功能

- 用户注册、管理员审批、账号禁用 / 恢复、登录态维持（Basic Auth 或 Session Cookie）
- 用户与管理员都支持修改昵称、上传自定义头像；注册时也可直接填写昵称并上传头像
- **多租户支持（邀请码注册模型）**：超管可创建租户并生成邀请码；用户注册时填写邀请码自动归属对应租户；支持 `TENANT_ADMIN`（租户管理员）角色，可审批本租户用户、管理本租户知识库
- 用户级知识库管理：系统内置一个所有用户都可见的系统知识库 `公司章程`（`default`，不可删除、仅管理员可维护）；管理员可继续新增”全体知识库”，默认对所有用户可见且仅管理员可维护；普通用户可在聊天页通过”我的知识库”创建、查看、编辑、上传文档和删除自己的私有知识库，且这些私有知识库仅本人可见、可用、可维护
- 文档上传与解析：`txt / log / csv / md / markdown / html / htm / docx / pdf / xls / xlsx`
- **批量文档上传**：支持多文件 `multipart` 并发上传，以及 ZIP 文件夹批量导入（自动解压遍历入库）
- **大文件断点续传**：4 MB 分片上传协议（`POST` 初始化 → `PATCH` 上传分片 → `GET` 查询进度），服务端纯文件系统组装，完成后自动触发解析入库
- 图片 OCR（可选，需配置 `KB_OCR_URL`）
- 固定窗口分块（`900` 字符，`120` 字符 overlap）
- BM25 + dense hybrid 检索（默认权重 `0.35 / 0.65`）
- 会话管理、引用溯源、chunk 详情查看
- **用户→管理员留言系统**：普通用户可发送留言给管理员（含主题与内容）；管理员可查看、回复、关闭留言；用户可在聊天页查看回复详情（替代已移除的反馈点赞功能）
- 管理员后台：用户审批、账号禁用 / 恢复、租户管理、用户留言管理、全体知识库/文档管理、模型 Provider 管理、模型启停
- 知识库创建为云端嵌入模式时，可直接选择后台”模型管理”里已启用的嵌入模型，无需在单个知识库中重复填写地址或 Key
- 聊天页与管理后台侧边栏支持收起 / 展开、拖拽调宽，页面会自动适配布局比例
- OpenAI-compatible Provider 接入；支持从 OpenRouter 风格 `/models` 接口同步模型
- **Spring Cloud Eureka 服务发现**（可选）：通过 `EUREKA_ENABLED=true` 开启，各服务自动注册，网关使用负载均衡 RestClient
- Docker Compose 一键启动；本地 PowerShell 脚本辅助前端构建与环境检查

## 架构总览

| 组件 | 默认端口 | 作用 | 主要存储 |
| --- | --- | --- | --- |
| `kb-gateway` | `8080` | Web UI、鉴权、多租户、留言、管理后台、统一 API 入口 | PostgreSQL (`public`) |
| `kb-doc-service` | `8081` | 文档上传、批量导入、断点续传、解析、分块、OCR 编排 | `data/doc/uploads`、`data/doc/resumable` |
| `kb-index-service` | `8082` | 知识库、文档索引、混合检索、embedding 调用 | PostgreSQL (`idx`) |
| `kb-ai-service` | `8083` | RAG 编排、会话、大模型调用 | PostgreSQL (`ai`) |
| `kb-embedder` | `8090` | 本地 embedding 服务 | 模型缓存 / venv |
| `kb-ocr-service` | `8070` | 可选 OCR 服务 | venv / tesseract |
| `kb-eureka-server` | `8761` | 可选 Eureka 服务注册中心（`EUREKA_ENABLED=true` 时生效） | 内存 |
| `kb-db` | `5432` | 共享 PostgreSQL 实例（三个服务各用一个 schema） | PostgreSQL data |

更详细说明见 `docs/ARCHITECTURE.md`。

## 仓库结构

```text
kb-assistant-ms/
├─ services/
│  ├─ kb-common/         # 公共库：util / security / web 包（非独立服务）
│  ├─ kb-gateway/        # Web UI + public API + auth + admin + multi-tenant + messages
│  ├─ kb-doc-service/    # upload / parse / chunk / OCR glue / batch / resumable
│  ├─ kb-index-service/  # KB metadata / embeddings / hybrid retrieval
│  ├─ kb-ai-service/     # RAG orchestration / sessions / streaming
│  ├─ kb-eureka-server/  # Spring Cloud Eureka 注册中心（可选）
│  ├─ kb-embedder/       # Python embedding service
│  └─ kb-ocr-service/    # Python OCR service
├─ apps/
│  ├─ chat/              # 聊天前端（Next.js 15，Tailwind v4）
│  ├─ admin/             # 管理后台前端（Next.js 15，Tailwind v4）
│  └─ home/              # 首页前端（Next.js 15，Tailwind v3）
├─ packages/
│  └─ shared/            # 前端共享工具（cn() 等）
├─ package.json          # pnpm workspace 根配置
├─ pnpm-workspace.yaml
├─ scripts/             # 本地开发 / 演示脚本
├─ deploy/
│  ├─ docker/           # Docker Compose 与 Dockerfile
│  └─ k8s/              # Kubernetes 清单（含 PostgreSQL + Eureka）
├─ docs/
├─ docker-compose.dev.yml  # 纯本地开发 PostgreSQL（统一使用 kb-db / kb-db-data）
└─ data/               # 本地运行期产物（doc-service 上传目录等）
```

## 运行前提

### 本地运行

- JDK `21`
- Maven `3.9+`
- Python `3.11` 或 `3.12`（推荐 `3.12`）
- Docker Desktop 或本机可用的 PostgreSQL `16+`

说明：

- 三个 JPA 服务（`kb-gateway`、`kb-index-service`、`kb-ai-service`）需要 PostgreSQL 才能启动。
  - **推荐**：确保 Docker Desktop 已运行，并统一复用项目的 `kb-db` 容器（`postgres:16-alpine`，数据库 `kb`，用户 `kb`，密码 `kb`）。
  - **备选**：若 `kb-db` 容器不存在，执行 `docker compose -f docker-compose.dev.yml up -d` 重建；该 Compose 也会创建同名 `kb-db` 容器并使用 `kb-db-data` 数据卷，不会再额外起第二套 dev 库。
  - `kb-doc-service` 不使用 PostgreSQL，可独立启动。
- `scripts/ensure-postgres.ps1` 会优先检测 `localhost:5432`，若端口未监听且本机安装了 Docker，则创建或启动同一套 `kb-db` / `kb-db-data`。
- 第一次启动 `kb-embedder` 时通常需要下载 embedding 模型，要求网络可访问 Hugging Face。
- 如果未配置 `SILICONFLOW_*`，聊天仍可运行，但回答会退化为基于检索片段的兜底输出。

### Docker Compose

- Docker Desktop / Docker Engine
- 首次构建镜像与首次下载 embedding 模型时需要联网

## 支持的部署模式

- Docker Compose from source：当前正式支持的公开路径。复制 `deploy/docker/.env.example` 到 `.env` 后执行 `docker compose up -d --build`。
- Kubernetes from published images：复制 `deploy/k8s/overlays/demo/.env.example` 到 `.env`，在 `deploy/k8s/overlays/demo/kustomization.yaml` 中把 `replace-me-owner` 和 tag 改成你的 GHCR 命名，然后执行 `kubectl apply -k deploy/k8s/overlays/demo`。

## 快速开始

### 方式一：IDEA 本地运行（推荐）

```powershell
cd .\kb-assistant-ms

# 1) 复制本地开发环境变量
Copy-Item .\.env.example .\.env

# 2) 确保 PostgreSQL 可用（需要 Docker Desktop）
.\scripts\ensure-postgres.ps1

# 3) 准备 Python 依赖（至少 embedder）
.\scripts\setup-python.ps1 -Service embedder

# 4) 构建 Java 服务
mvn -DskipTests package

# 5) 在 IDEA 中逐个启动各服务（或使用 Services 面板一键启动）
```

前端修改后需要重新构建：

```powershell
.\build.cmd   # 构建三个前端（apps/chat、apps/admin、apps/home）并复制产物到 gateway 静态资源
```

### IDEA 本地运行排障

- 项目根目录的 `.env` 会被各个 Spring Boot 服务自动导入；如果你是第一次拉代码，本地运行前先执行 `Copy-Item .\.env.example .\.env`。
- IDEA 的 Run/Services 面板不一定始终显示端口号；请以控制台日志中的 `Tomcat started on port ...` 为准。默认端口分别为：`kb-gateway=8080`、`kb-doc-service=8081`、`kb-index-service=8082`、`kb-ai-service=8083`、`kb-eureka-server=8761`。
- 修改 Spring Bean、`@Configuration`、事务注解、环境变量绑定等启动期配置后，建议执行 `Build > Rebuild Project`，然后停止并重新启动对应服务；仅依赖热更新时，新配置可能不会生效。
- 若某个服务已经启动但 IDEA 面板未显示端口，可直接搜索控制台关键字 `Tomcat started on port`，或访问对应页面 / 健康检查确认服务是否已就绪。
- 如果 `kb-gateway` 启动时报 `KB_INTERNAL_TOKEN is using an insecure development default`，说明本地 `.env` 没有生效，先确认工作目录是仓库根目录并重新复制 `.env.example`。
- 如果 IDEA 打开项目时弹出“链接数据库”并连接失败，请先确保 `.\scripts\ensure-postgres.ps1` 或 `docker compose -f docker-compose.dev.yml up -d` 已成功启动本地库；连接参数应为 `host=localhost`、`port=5432`、`database=kb`、`user=kb`、`password=kb`。

### 方式二：Docker Compose（推荐用于本地演示）

#### 前置条件

- 已安装并启动 Docker Desktop

---

#### 第一步：创建配置文件

1. 用资源管理器打开项目目录 `deploy/docker/`
2. 找到文件 `.env.example`，**右键 → 复制**，再**右键 → 粘贴**，得到一个副本
3. 将副本**重命名**为 `.env`（把 `.example` 后缀删掉）
   > Windows 如果提示"更改扩展名可能导致文件不可用"，点**是**
   > 如果看不到扩展名，在资源管理器顶部勾选"查看 → 显示 → 文件扩展名"

4. **右键 `.env` → 打开方式 → 记事本**

---

#### 第二步：填写配置

用记事本打开后，逐项修改以下内容（其余行不用动）：

```
# 本地测试必须设为 true，否则启动会报安全校验错误
KB_ALLOW_INSECURE_DEFAULTS=true

# 数据库密码，自定义，记住就行
KB_DB_PASSWORD=自定义密码

# 管理员账号，等会登录后台用这个
KB_BOOTSTRAP_ADMIN_USER=admin
KB_BOOTSTRAP_ADMIN_PASSWORD=自定义密码

# 加密主密钥，随便填一串字符，但必须达到32个字符
KB_CRYPTO_MASTER_KEY=随便填满32个字符例如aaabbbccc123456789

# 服务间通信令牌，随便填一串字符即可
KB_INTERNAL_TOKEN=随便填
```

**关于大模型（可选）：**

```
# 如果你有 SiliconFlow 的 API Key，填在这里，聊天才能得到 AI 回答
# 没有就留空，聊天会直接返回检索到的原文片段
SILICONFLOW_API_KEY=
SILICONFLOW_MODEL=
```

填完后 **Ctrl+S 保存，关闭记事本**。

---

#### 第三步：启动服务

在 `deploy/docker/` 目录下打开 PowerShell，运行：

```powershell
docker compose -f docker-compose.ghcr.yml up -d
```

> 这条命令使用已发布的预构建镜像，**无需本地编译**，首次启动约 3~5 分钟。
>
> 如需从本地源码编译（速度较慢），改用：`docker compose up -d --build`

---

#### 第四步：等待启动完成

Docker Desktop 中可以看到 6 个容器逐渐变绿（状态变为 running）。

全部变绿后，浏览器访问以下地址验证：

```
http://localhost:8080/api/health
```

返回内容包含 `"status":"UP"` 即表示启动成功。

---

#### 访问地址与登录

| 页面 | 地址 |
| --- | --- |
| 首页 | http://localhost:8080/ |
| 聊天页 | http://localhost:8080/chat |
| 管理后台 | http://localhost:8080/admin |

**登录账号**：用你在 `.env` 里填写的 `KB_BOOTSTRAP_ADMIN_USER` 和 `KB_BOOTSTRAP_ADMIN_PASSWORD`。

> 更多部署细节见 `deploy/docker/README.md`。

### 方式三：Kubernetes（GHCR + Kustomize）

```powershell
cd .\kb-assistant-ms
Copy-Item .\deploy\k8s\overlays\demo\.env.example .\deploy\k8s\overlays\demo\.env
```

然后编辑：

- `deploy/k8s/overlays/demo/.env`
- `deploy/k8s/overlays/demo/kustomization.yaml` 里的 `replace-me-owner` 和 `newTag`

渲染并部署：

```powershell
kubectl kustomize .\deploy\k8s\overlays\demo
kubectl apply -k .\deploy\k8s\overlays\demo
kubectl rollout status statefulset/kb-postgres -n kb-assistant
kubectl rollout status deployment/kb-gateway -n kb-assistant
kubectl port-forward svc/kb-gateway 8080:8080 -n kb-assistant
```

默认 overlay 不包含 OCR 和 Eureka。需要 OCR 时改用 `deploy/k8s/overlays/ocr`；需要 Eureka 时改用 `deploy/k8s/overlays/eureka`。部署细节见 `deploy/k8s/README.md`。

## 默认访问地址

- 首页：`http://localhost:8080/`
- 聊天页：`http://localhost:8080/chat`
- 管理后台：`http://localhost:8080/admin`
- 健康检查（readiness）：`http://localhost:8080/api/health`
- 存活检查（liveness）：`http://localhost:8080/api/health/live`

## 鉴权与账号模型

### 管理员账号

系统启动时会自动创建一个管理员账号（同时创建”默认租户”）：

- 用户名来自 `KB_BOOTSTRAP_ADMIN_USER`
- 密码来自 `KB_BOOTSTRAP_ADMIN_PASSWORD`

可通过环境变量覆盖：

- `KB_BOOTSTRAP_ADMIN_USER`
- `KB_BOOTSTRAP_ADMIN_PASSWORD`
- `KB_ALLOW_INSECURE_DEFAULTS=true` 仅用于本地开发演示；生产环境必须显式提供 `KB_INTERNAL_TOKEN`、`KB_BOOTSTRAP_ADMIN_*`、`KB_CRYPTO_MASTER_KEY`

### 普通用户

项目**不会**自动创建默认普通用户。

正确流程是：

1. 调用 `POST /api/auth/register` 注册（可选填 `inviteCode` 邀请码加入对应租户）
2. 管理员（或对应租户的 `TENANT_ADMIN`）在 `/admin` 页面或通过 API 审批
3. 系统会始终保留一个所有用户都可见的系统知识库：`公司章程`（知识库 ID 为 `default`）
4. 管理员可在后台继续创建”全体知识库”，新老用户都会自动可见，但只有管理员能维护这些知识库
5. 用户或管理员登录后，都可通过 `PATCH /api/auth/profile` 修改昵称与头像
6. 普通用户可在聊天页点击”我的知识库”创建和维护自己的私有知识库；管理员后台不会显示这些私有知识库

知识库可见性边界：

- **租户共享知识库** 与 **用户私有知识库** 是两种不同类型的知识库
- 用户私有知识库始终只对创建者本人可见、可用、可维护，且不会写入 `kb_kb_settings.tenant_id`
- 租户共享知识库才会绑定 `tenant_id`，同租户成员可见并可用于检索；`TENANT_ADMIN` / `ADMIN` 可按租户范围维护这类知识库

### 角色体系

| 角色 | 描述 |
| --- | --- |
| `ADMIN`（超管） | 管理所有租户、所有用户、用户审批与账号禁用 / 恢复、所有知识库、Provider 等 |
| `TENANT_ADMIN`（租户管理员） | 审批本租户用户、管理本租户知识库；只能由 `ADMIN` 显式授予 / 撤销 |
| `USER`（普通用户） | 访问自己的及系统/全体知识库，管理自己的私有知识库和留言 |

### 多租户（邀请码模型）

超管在管理后台”租户管理”中创建租户，系统自动生成邀请码。用户注册时在”邀请码（可选）”字段中填入，即自动归属该租户。不填邀请码则归入默认租户。

邀请码只决定用户归属哪个租户，不会自动授予 `TENANT_ADMIN`。租户管理员身份必须由超管单独设置。

### 支持的登录方式

- 标准 `Authorization: Basic ...`
- `POST /api/auth/login` 建立 Session Cookie
- `GET /api/chat/stream` 通过标准 Session Cookie 或 `Authorization: Basic ...` 鉴权，不再支持旧的查询参数鉴权方式
- 当账号状态为 `DISABLED` 时，登录会返回 `403`，提示“您的账号已被禁用，请联系管理员”

### 身份返回契约

- `GET /api/auth/me` 返回 `role`、`effectiveRole`、`isTenantAdmin`、`tenantId`、`authorities`、`status`
- 当用户被授予租户管理员时，`role` 仍可能是 `USER`，但 `effectiveRole` 会提升为 `TENANT_ADMIN`
- 浏览器侧 `POST` / `PATCH` / `DELETE` 请求需要携带 CSRF token；项目内三个前端已自动从 `XSRF-TOKEN` Cookie 读取并回传 `X-XSRF-TOKEN`

## 模型调用方式

项目支持两种大模型来源：

### 1. 默认 SiliconFlow 配置

通过 AI 服务环境变量：

- `SILICONFLOW_API_KEY`
- `SILICONFLOW_BASE_URL`
- `SILICONFLOW_MODEL`

当聊天请求未指定 `model` 时，AI 服务优先使用这组默认配置。

### 2. 管理后台动态 Provider / Model

管理员可通过：

- `POST /api/admin/providers`
- `GET /api/admin/providers/{id}/openrouter/models`
- `POST /api/admin/providers/{id}/openrouter/sync-models`
- `PATCH /api/admin/models/{id}`

录入 OpenAI-compatible Provider，并同步 / 启用模型。

此时前端或 API 调用可把 `model` 字段设置为 **模型 UUID**（来自 `GET /api/models`），网关会解析出对应 `baseUrl / apiKey / modelId` 并传给 AI 服务。

## 关键环境变量

### 网关 / 鉴权 / 数据库

- `KB_ALLOW_INSECURE_DEFAULTS`（仅本地开发使用；默认 `false`）
- `KB_DB_URL`
- `KB_DB_USER`
- `KB_DB_PASSWORD`
- `KB_BOOTSTRAP_ADMIN_USER`
- `KB_BOOTSTRAP_ADMIN_PASSWORD`
- `KB_CRYPTO_MASTER_KEY`
- `KB_INTERNAL_TOKEN`

### 服务互调

- `KB_AI_URL`
- `KB_DOC_URL`
- `KB_GATEWAY_URL`
- `KB_INDEX_URL`
- `KB_EMBEDDER_URL`
- `KB_OCR_URL`
- `KB_DOC_DATA_DIR`（文档服务上传目录，默认 `data`）

其中：

- `KB_GATEWAY_URL` 由 `kb-index-service` 用来回调 `kb-gateway` 的内部 embedding 代理接口。
- 这样知识库使用”云端 API 嵌入”且选择的是后台已管理模型时，Provider 的 `baseUrl` / `apiKey` 仍只保存在网关侧，不会落到知识库配置中。

### Eureka 服务发现（可选）

- `EUREKA_ENABLED`（默认 `false`，设为 `true` 时各服务自动注册到 Eureka）
- `EUREKA_SERVER_URL`（Eureka 服务地址，默认 `http://localhost:8761`）

Eureka 开启后，网关使用 `@LoadBalanced` RestClient 通过服务名路由请求，无需硬编码各服务 URL。

### Embeddings / LLM

- `KB_EMBED_API_BASE_URL`
- `KB_EMBED_API_KEY`
- `SILICONFLOW_API_KEY`
- `SILICONFLOW_BASE_URL`
- `SILICONFLOW_MODEL`

示例值见 `deploy/docker/.env.example`。

## 数据落盘说明

- `kb-gateway`：用户、租户、审批状态、AI Provider、模型启停配置、用户留言等保存在 PostgreSQL（`public` schema）
- `kb-doc-service`：上传原文件落在 `data/doc/uploads/{kbId}/{docId}/`；断点续传临时文件落在 `data/doc/resumable/{kbId}/{uploadId}/`
- `kb-index-service`：知识库元数据、文档、chunk、向量索引保存在 PostgreSQL（`idx` schema）；BM25 索引在内存中按需从数据库重建
- `kb-ai-service`：会话与消息保存在 PostgreSQL（`ai` schema）

## 本地环境变量

根目录提供 `.env.example`，包含本地开发所需的全部环境变量（`localhost` 地址）。复制为 `.env` 后按需修改：

```powershell
Copy-Item .\.env.example .\.env
```

从仓库根目录启动的 Spring Boot 服务（包括 IDEA 的默认本地运行）会自动导入这个 `.env`。

Docker Compose 版本见 `deploy/docker/.env.example`。

## 常用脚本

| 脚本 | 作用 |
| --- | --- |
| `build.cmd` | 根目录快捷入口：调用 `scripts/build.ps1` 构建三个前端并复制产物 |
| `scripts/build.ps1` | pnpm workspace 根安装 + 构建三个前端（`apps/chat` `apps/admin` `apps/home`），并把静态导出结果复制到 `kb-gateway` 静态目录 |
| `scripts/ensure-postgres.ps1` | 检测 `localhost:5432`；若不可用则通过 `docker-compose.dev.yml` 拉起 PostgreSQL |
| `scripts/setup-python.ps1 -Service embedder\|ocr` | 为指定 Python 服务创建 venv 并安装依赖 |

## 文档索引

- 架构说明：`docs/ARCHITECTURE.md`
- 网关 API：`docs/API.md`
- 代码导读（功能实现路径）：`docs/代码导读.md`
- 部署矩阵：`docs/DEPLOYMENT-MATRIX.md`
- 发布说明：`docs/RELEASE.md`
- Docker Compose：`deploy/docker/README.md`
- Kubernetes 说明：`deploy/k8s/README.md`


---

## 常见运行时报错

- `知识库检索服务暂时不可用，请稍后再试。`：表示本次请求里所有知识库检索都失败了。请优先查看 `kb-index-service` 控制台；若日志中出现 `Transaction silently rolled back because it has been marked as rollback-only`，请确认已更新到最新代码并重启 `IndexServiceApplication`。
- `知识库中暂无相关信息。你可以换个关键词或上传相关文档后再试。`：表示检索链路已执行成功，但没有命中可用片段。可尝试更换关键词、补充文档，或在切换 embedding 模型 / 配置后重新索引已有知识库。
- `OpenAI-compatible call failed: chat/completions HTTP 429`：通常表示上游模型渠道限流、排队过多或账号配额受限，而不是本地 Java 微服务启动失败。建议稍后重试、降低并发，或切换其他可用模型。
- `您的账号已被禁用，请联系管理员`：表示该用户已被超管在后台“用户管理”中禁用；数据不会删除，但该账号无法继续登录，需由超管恢复为 `ACTIVE`。
- 若最近修改了知识库的 embedding 模式、嵌入模型或 Provider，历史向量不会自动重算；需要在管理端重新索引，或调用 `kb-index-service` 的 `/internal/reindex`、`/internal/kbs/{kbId}/reindex` 接口重建索引。

## 已知限制

- LLM 配置缺失或无效时会静默降级为基于检索片段的兜底答案，不会主动抛出错误；排查时请确认 `SILICONFLOW_*` 环境变量或后台 Provider 已正确配置。
- 服务间 HTTP 调用无重试机制与熔断器，任一内部服务不可用时错误会直接暴露给用户。
- 生产环境必须显式设置 `KB_INTERNAL_TOKEN`、`KB_BOOTSTRAP_ADMIN_*`、`KB_CRYPTO_MASTER_KEY`；只有本地开发才应设置 `KB_ALLOW_INSECURE_DEFAULTS=true`。
- 首次拉起 Python embedder 时，模型下载耗时可能较长。
- `deploy/k8s/00*.yaml` 仍保留为 legacy 参考清单；正式路径是 `deploy/k8s/overlays/demo` 及其可选 OCR / Eureka overlays。
