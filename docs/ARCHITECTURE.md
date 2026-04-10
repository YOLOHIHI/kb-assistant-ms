# Architecture

本文档描述当前仓库内已经落地的微服务结构，而不是理想化目标图。

## 1. 总体拓扑

系统由一个公网入口网关、三个核心 Java 微服务、两个 Python 辅助服务，以及一个 PostgreSQL 数据库组成。可选开启 Eureka 注册中心实现服务发现。

```text
Browser / API Client
        |
        v
   kb-gateway:8080
   ├─ Static UI (`/`, `/chat`, `/admin`)
   ├─ Auth / Session / Admin APIs
   ├─ Multi-tenant / User Messages
   ├─ Provider & Model management
   └─ Proxy to internal services with X-Internal-Token
        |
        +--> kb-doc-service:8081
        |      └─ parse / chunk / OCR glue / batch / resumable upload
        |
        +--> kb-index-service:8082
        |      └─ KB metadata / docs / embeddings / hybrid retrieval
        |
        +--> kb-ai-service:8083
               └─ RAG orchestration / sessions / LLM call

Support services:
- kb-embedder:8090
- kb-ocr-service:8070 (optional)
- kb-eureka-server:8761 (optional, EUREKA_ENABLED=true)
- PostgreSQL:5432
```

## 2. 组件职责

### `kb-common`（公共库，非独立服务）

`services/kb-common` 是所有 Java 服务共用的类库，不独立部署：

| 包 | 内容 |
| --- | --- |
| `util.HashUtil` | SHA-256 哈希工具 |
| `util.IdUtil` | UUID 前缀 ID 生成 |
| `util.WebUtils` | `safeTrim / clamp / safeKbId` 跨服务复用方法 |
| `security.InternalAuthFilter` | 通用 `X-Internal-Token` 过滤器（无 `@Component`，各服务通过 `@Bean` 注册） |
| `web.BaseApiExceptionHandler` | 抽象异常处理基类，所有服务的 `ApiExceptionHandler` 继承自此 |
| `Dtos` | 跨服务共享 DTO（record） |

### `kb-gateway` (`8080`)

- 唯一的对外入口
- 提供静态前端页面：`/`、`/chat`、`/admin`
- 负责 Spring Security 鉴权，支持三种角色：`USER`、`TENANT_ADMIN`、`ADMIN`
- 暴露用户注册（支持邀请码）、登录、个人资料、知识库、会话、留言、管理员后台等公共 API
- 管理 OpenAI-compatible Provider 与模型清单
- 管理租户创建、邀请码生成与租户管理员功能
- 通过 `X-Internal-Token` 调用内部服务
- 代理批量上传和断点续传请求到 `kb-doc-service`
- 使用 PostgreSQL 保存：用户、租户、审批状态、头像、Provider、模型、知识库归属关系、知识库公开标记、知识库检索配置、用户留言等

**Gateway 控制器结构：**

| 类 | 包 | 路由 |
| --- | --- | --- |
| `GatewayHealthController` | `gateway` | `GET /api/health`, `GET /api/health/live` |
| `AuthController` | `gateway.auth` | `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `PATCH /api/auth/profile`, `POST /api/auth/logout`, `GET /api/auth/csrf` |
| `ChatController` | `gateway` | `POST /api/chat`, `GET /api/chat/stream` |
| `SessionController` | `gateway` | `/api/sessions/**` |
| `ModelsController` | `gateway.models` | `GET /api/models` |
| `MessageController` | `gateway.messages` | `GET /api/messages`, `POST /api/messages`, `DELETE /api/messages/{id}` |
| `UserKbController` | `gateway.kb` | `/api/kbs/**`（含批量上传与断点续传代理） |
| `AdminUsersController` | `gateway.admin` | `GET /api/admin/users`, `POST /api/admin/users/{id}/approve`, `POST /api/admin/users/{id}/reject`, `POST /api/admin/users/{id}/disable`, `POST /api/admin/users/{id}/restore`, `POST /api/admin/users/{id}/tenant-admin`, `DELETE /api/admin/users/{id}/tenant-admin` |
| `AdminDocumentController` | `gateway.admin` | `/api/admin/kbs/**`（知识库、文档、统计、reindex） |
| `AdminProvidersController` | `gateway.admin` | `/api/admin/providers/**` |
| `AdminModelsController` | `gateway.admin` | `/api/admin/models/**` |
| `AdminMessageController` | `gateway.admin` | `/api/admin/messages/**` |
| `AdminTenantController` | `gateway.admin` | `/api/admin/tenants/**` |
| `TenantAdminController` | `gateway.tenant` | `/api/tenant/**` |
| `InternalEmbeddingController` | `gateway` | `/internal/embed/**`（embedding 代理，供 index-service 回调） |

### `kb-doc-service` (`8081`)

- 接收文件上传并落盘
- 支持解析：`txt / log / csv / md / markdown / html / htm / docx / pdf / xls / xlsx`
- 当配置 `KB_OCR_URL` 时，对图片走 OCR 解析
- 对文本进行固定窗口分块：`900` 字符，`120` 字符 overlap
- 将文档与 chunk 结果写入 `kb-index-service`
- **批量上传**：接收 `multipart/form-data` 多文件请求，逐文件解析入库；接收 ZIP 文件并解压后批量入库
- **断点续传**：实现 4 MB 分片协议，文件数据以 `RandomAccessFile` 随机写入 `data/resumable/` 目录，完整接收后自动触发解析入库

### `kb-index-service` (`8082`)

- 管理知识库、文档、chunk、向量数据（存储于 PostgreSQL `idx` schema）
- 调用 `kb-embedder` 计算向量，或调用外部 OpenAI-compatible embeddings API
- 提供 BM25 + dense hybrid retrieval
- 默认融合权重：`bm25Weight=0.35`，`denseWeight=0.65`
- BM25 索引在内存中维护（按需从数据库重建），向量存储为 TEXT 或 pgvector `vector(512)` 类型
- **`EmbeddingFacade`**：封装 embedding 策略选择逻辑（`local` / `api` / `managed-uuid`），`IndexService` 通过注入该 `@Service` 调用，隐藏三段式 if-else

### `kb-ai-service` (`8083`)

- 负责聊天主链路编排
- 查询索引服务拿回检索结果与引用片段
- 维护会话与消息（存储于 PostgreSQL `ai` schema）
- 支持 `contextSize` 参数，控制放入 prompt 的历史消息条数（默认 `0`）
- 优先使用请求中传入的动态 `llm` 配置或模型 UUID；否则回退到 `siliconflow.*` 配置（由 `SiliconflowConfig` 绑定）
- 若大模型未配置或调用失败，则返回基于检索片段的兜底答案
- **真实 SSE 流式输出**：`POST /internal/chat/stream` 向 LLM 发起 `stream:true` 请求，通过 `OpenAiChatClient.streamChat()` 逐 token 转发给客户端；`RagService.chatStream()` 负责流式编排，完成后追加 `event: meta` 事件

### `kb-embedder` (`8090`)

- Python FastAPI 服务
- 默认使用 `BAAI/bge-small-zh-v1.5`
- 暴露：
  - `GET /health`
  - `POST /embed`

### `kb-ocr-service` (`8070`, optional)

- Python FastAPI 服务
- 基于 tesseract 提供 OCR 能力
- 仅在 `kb-doc-service` 配置 `KB_OCR_URL` 时参与链路

### `kb-eureka-server` (`8761`, optional)

- Spring Cloud Netflix Eureka 注册中心
- 通过 `EUREKA_ENABLED=true` 环境变量激活
- 激活后，所有 Java 服务（`kb-gateway`、`kb-doc-service`、`kb-index-service`、`kb-ai-service`）自动向 Eureka 注册
- `kb-gateway` 使用 `@LoadBalanced` RestClient，通过服务名（如 `http://kb-ai-service`）路由请求，无需硬编码 URL
- 默认关闭，不影响现有无 Eureka 部署

### `PostgreSQL` (`5432`)

- `kb-gateway`、`kb-index-service`、`kb-ai-service` 均直接连接同一 PostgreSQL 实例，通过不同 schema 隔离
- `public` schema（由 `kb-gateway` 管理）：
  - 用户与角色（`USER` / `TENANT_ADMIN` / `ADMIN`）
  - 审批状态
  - 用户昵称与自定义头像
  - 租户（`tenant`）与邀请码
  - 用户留言（`user_message`，含 OPEN / REPLIED / CLOSED 状态）
  - AI Provider / 模型配置
  - 用户与知识库绑定关系
  - 知识库检索数量、公开访问标记、租户归属（`tenant_id`）等设置
- `idx` schema（由 `kb-index-service` 管理）：
  - 知识库元数据（含可选 `tenant_id`，`NULL` 为系统级 KB）
  - 文档信息（文件名、大小、类型、sha256、分类、标签）
  - chunk（分块内容、embedding 向量，可选升级为 pgvector 类型）
- `ai` schema（由 `kb-ai-service` 管理）：
  - 会话（sessionId、userId、title）
  - 消息（sender、content、model 引用）

## 3. 数据存储划分

| 位置 | 内容 |
| --- | --- |
| PostgreSQL `public` | 用户、租户、管理员、Provider、模型、知识库绑定与设置、用户留言 |
| PostgreSQL `idx` | 知识库元数据（含 tenant_id）、文档、chunk、embedding 向量 |
| PostgreSQL `ai` | 会话、消息 |
| `data/doc/uploads` | 原始上传文件（按 kbId/docId 分目录存放） |
| `data/doc/resumable` | 断点续传临时数据（meta.json + data.bin，完成后自动入库） |
## 4. 鉴权模型

### 对外鉴权

- `/api/health`、`/api/health/live` 与 `/api/auth/**` 允许匿名访问
- `/api/admin/**` 要求管理员权限（`ADMIN`）
- `/api/tenant/**` 要求 `ADMIN` 或 `TENANT_ADMIN`
- `/api/messages/**` 要求已登录用户（任意角色）
- 其他 `/api/**` 默认要求已登录用户

支持两种访问方式：

- `Authorization: Basic ...`
- 先调用 `POST /api/auth/login`，后续依赖 Session Cookie

`/api/chat/stream` 不提供额外的查询参数鉴权旁路，仍然使用同一套 Session Cookie 或 `Authorization: Basic ...` 鉴权。

Session Cookie 设置了 `SameSite=Lax`、`HttpOnly`，在标准浏览器跨站场景下提供基础 CSRF 防护。

### 对内鉴权

- 所有 `/internal/**` 基本都依赖 `X-Internal-Token`
- Gateway 是默认的唯一公共入口

## 5. 关键业务流

### 5.1 用户注册与租户归属

1. 用户调用 `POST /api/auth/register`，可同时提交昵称、头像与可选 `inviteCode`
2. 若填写邀请码：系统查找对应租户并将 `tenant_id` 写入用户记录；否则归入默认租户
3. 账号被创建为 `PENDING`
4. 超管或对应 `TENANT_ADMIN` 调用审批接口
5. 若后续需要冻结账号，仅超管可把用户状态切到 `DISABLED`；这不会删除用户数据，但会阻止该账号再次登录
6. `kb-index-service` 会始终确保系统知识库 `公司章程`（`default`）存在
7. 网关会把 `公司章程` 暴露为所有用户都可见的系统知识库；普通用户可读但不可维护，只有管理员可维护
8. 管理员后续在后台创建的知识库，默认也会作为”全体知识库”对所有用户可见
9. 用户和管理员后续都可通过 `PATCH /api/auth/profile` 修改昵称与头像

### 5.2 文档入库（单文件 / 批量 / ZIP / 断点续传）

**单文件上传：**
1. 管理员或普通用户向网关发起上传（`POST .../upload`）
2. Gateway 将 multipart 请求转发给 `kb-doc-service`
3. `kb-doc-service` 解析文本并分块，调用 `kb-index-service` 执行 upsert
4. `kb-index-service` 调用 `kb-embedder` 或外部 embeddings API 计算向量

**批量上传（多文件 / ZIP）：**
1. 网关接收多文件请求（`POST .../batch`）或 ZIP 文件（`POST .../import-zip`），转发至 `kb-doc-service`
2. `kb-doc-service` 逐文件调用相同解析链路，返回每个文件的 `status: ok/error`

**断点续传：**
1. 客户端初始化：`POST .../uploads` → 获取 `uploadId` 与 `chunkSize: 4MB`
2. 客户端逐片上传：`PATCH .../uploads/{uploadId}`，携带 `Content-Range: bytes start-end/total`
3. 客户端查询进度：`GET .../uploads/{uploadId}`
4. `kb-doc-service` 接收最后一片后自动触发解析入库；`status` 变为 `COMPLETE` 或 `INGEST_ERROR`

### 5.3 用户留言

1. 用户在聊天页点击”联系管理员”，填写主题与内容，调用 `POST /api/messages`
2. 用户可通过 `GET /api/messages` 查看自己发送的留言及管理员回复
3. 管理员在后台”用户留言”页面查看所有留言（支持按 `status` 筛选：`OPEN / REPLIED / CLOSED`）
4. 管理员通过 `POST /api/admin/messages/{id}/reply` 回复，留言状态自动更新为 `REPLIED`
5. 管理员可通过 `PATCH /api/admin/messages/{id}` 将留言标记为 `CLOSED`

### 5.4 聊天问答

**同步模式（`POST /api/chat`）：**

1. Gateway 根据当前用户权限计算可用知识库集合与 topK 配置
2. Gateway 解析 `model` 字段为动态 LLM 配置（UUID → `baseUrl/apiKey/modelId`）
3. Gateway 调用 `kb-ai-service /internal/chat`
4. AI 服务完成 KB 检索 → prompt 构建 → LLM 调用 → 会话持久化
5. 响应返回 `sessionId / answer / answerHash / citations`

**SSE 流式模式（`GET /api/chat/stream`）：**

1. Gateway 完成知识库解析与模型解析（同上 1-2 步）
2. Gateway 向 AI 服务发起 `POST /internal/chat/stream`，通过 `RestClient.exchange()` 将上游 SSE 字节流直接 `transferTo` 到客户端响应
3. AI 服务调用 `RagService.chatStream()`：同步完成 KB 检索、prompt 构建，随后调用 `OpenAiChatClient.streamChat()` 向 LLM 发起 `stream:true` 请求
4. LLM 每个 delta token 以 `event: token / data: <text>` 形式实时写出
5. 流结束后追加 `event: meta / data: {sessionId, answerHash, citations}` 并持久化会话

### 5.5 模型 Provider 管理

1. 管理员在网关录入 OpenAI-compatible Provider
2. API Key 会用 `KB_CRYPTO_MASTER_KEY` 加密后写入 PostgreSQL
3. 管理员可从 OpenRouter 风格 `/models` 接口抓取模型列表
4. 模型默认同步为禁用状态，需要管理员手动启用
5. 前端聊天时通过 `GET /api/models` 获取可用模型，再把其 UUID 写入 `model` 字段

## 6. 运行模式

### 本地脚本模式

- `scripts/start-demo.ps1`：适合演示
- `scripts/run-local.ps1`：适合开发
- `scripts/ensure-postgres.ps1`：保证 PostgreSQL 可用

### IDEA / 本地开发排障

- IDEA 的 Run/Services 面板不一定稳定显示端口号；本项目以启动日志中的 `Tomcat started on port ...` 作为实际监听端口来源。
- 默认端口为：`kb-gateway=8080`、`kb-doc-service=8081`、`kb-index-service=8082`、`kb-ai-service=8083`、`kb-eureka-server=8761`。
- 修改 `@Configuration`、Bean 装配、事务注解或环境变量绑定后，建议执行一次 `Build > Rebuild Project`，并停止后重启相关服务，而不要只依赖热更新。

### Docker Compose 模式

- `deploy/docker/docker-compose.yml` 提供相对完整的本地容器编排
- 同时包含 `kb-db`
- 是当前最接近“一键运行”的容器化方案

### Kubernetes 模式

- `deploy/k8s/` 清单已完整包含：PostgreSQL StatefulSet + PVC、Eureka Server、Secrets（含 `KB_BOOTSTRAP_ADMIN_USER/PASSWORD`、`KB_DB_*`、`KB_CRYPTO_MASTER_KEY` 等所有必需字段）、网关所需全部环境变量
- 可按序 `kubectl apply` 后获得可用演示环境
- **仍需**在 `01-secrets.yaml` 中手动填写 `SILICONFLOW_API_KEY`、`SILICONFLOW_MODEL` 等敏感值
- 如需 Eureka，需在各服务 Deployment 中设置 `EUREKA_ENABLED=true` 并部署 `00c-eureka.yaml`

## 7. 已知技术限制

- **LLM 配置静默降级**：`llm` 参数未配置或无效时，`RagService` 返回 `null` 而非抛出异常，最终退化为基于检索片段的兜底答案，调用方无明确报错。
- **无重试与熔断**：各微服务间的 HTTP 调用为单次请求（`RestClient`），无重试机制与熔断器，任一内部服务不可用时错误直接向用户暴露。
- **`KB_CRYPTO_MASTER_KEY` 未启动时校验**：该变量缺失不会在启动时报错，而是在 Provider API Key 加密 / 解密时于运行期失败，排查较困难。
- **BM25 索引无预热**：BM25 索引完全在内存中维护，服务重启后由首次检索请求触发从数据库重建，无主动预热机制。
- **用户知识库禁止自定义 embeddingBaseUrl**：普通用户通过 `POST /api/kbs` 创建知识库时，`embeddingBaseUrl` 字段必须为 `null`，传入非空值返回 `400 Bad Request`，以防止 SSRF 风险。embedding 模型应通过后台管理的模型 UUID 选择。
- **断点续传无过期清理**：`data/doc/resumable/` 中的中断上传临时文件不会自动删除，长期积累需手动清理。
- **知识库检索失败与“无结果”是两类状态**：`kb-ai-service` 仅在本次请求的所有知识库检索都失败时才返回“知识库检索服务暂时不可用”；如果检索调用成功但没有命中文档，则返回“知识库中暂无相关信息”。排障时应优先查看 `kb-index-service` 日志，而不是只看前端提示。
- **切换 embedding 配置后需重建索引**：知识库变更 `embeddingMode`、嵌入模型或外部 embedding Provider 后，历史文档向量不会自动重算，需要显式执行 reindex。
- **OpenAI-compatible `429` 多为上游限流**：`chat/completions HTTP 429` 通常来自外部模型渠道的限流、排队或配额控制，不代表本地微服务链路本身不可用。
