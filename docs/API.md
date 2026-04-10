# API (Gateway)

Base URL：`http://localhost:8080`

本文档只描述**对外公开的 Gateway API**。内部 `/internal/**` 接口不在此列。

## 1. 鉴权规则

- 匿名可访问：`/api/health`、`/api/health/live`、`/api/auth/**`
- 管理员专用（`ADMIN`）：`/api/admin/**`
- 租户管理员（`ADMIN` 或 `TENANT_ADMIN`）：`/api/tenant/**`
- 已登录用户（任意角色）：`/api/messages/**`
- 其他 `/api/**`：要求登录用户

支持两种主要认证方式：

### Basic Auth

在请求头中携带：

```http
Authorization: Basic BASE64(username:password)
```

### Session Cookie

先调用：

- `POST /api/auth/login`

随后在同一 Cookie 会话中访问其余接口。

> Session Cookie 设置了 `SameSite=Lax`，跨站 POST/PUT/DELETE 请求无法携带该 Cookie，可防范 CSRF 攻击。

### SSE 认证

`GET /api/chat/stream` 沿用标准鉴权链路：

- 同源场景下复用已建立的 Session Cookie
- 或显式携带 `Authorization: Basic BASE64(username:password)` 请求头

## 2. 健康检查

### `GET /api/health`

网关 readiness 检查。

- 全部下游服务（`ai`、`doc`、`index`）可达时返回 `200`
- 任一服务不可达时返回 `503`
- 返回体会包含各下游服务的探测结果

示例响应：

```json
{
  "status": "ok",
  "time": "2026-03-08T12:34:56Z",
  "services": {
    "ai": {
      "status": "ok",
      "httpStatus": 200,
      "url": "http://localhost:8083/internal/health"
    },
    "doc": {
      "status": "ok",
      "httpStatus": 200,
      "url": "http://localhost:8081/internal/health"
    },
    "index": {
      "status": "ok",
      "httpStatus": 200,
      "url": "http://localhost:8082/internal/health"
    }
  }
}
```

### `GET /api/health/live`

网关 liveness 检查。

- 不访问下游服务
- 只要网关进程存活就返回 `200`

示例响应：

```json
{
  "status": "ok",
  "time": "2026-03-08T12:34:56Z"
}
```

## 3. 认证相关

### `POST /api/auth/register`

创建普通用户，初始状态为 `PENDING`，需要管理员或租户管理员审批。

请求体：

```json
{
  "username": "alice",
  "password": "user123",
  "displayName": "Alice",
  "avatarDataUrl": "data:image/png;base64,...",
  "inviteCode": "ACME2024"
}
```

字段说明：

- `inviteCode`：可选。填入后用户自动归属对应租户；不填则归入默认租户

响应示例：

```json
{ "ok": true, "status": "PENDING" }
```

### `POST /api/auth/login`

登录并建立 Session。

请求体：

```json
{
  "username": "alice",
  "password": "user123"
}
```

常见返回：

- `401`：账号或密码错误
- `403`：账号未启用或待管理员审批
- `403`：若账号状态为 `DISABLED`，返回“您的账号已被禁用，请联系管理员”

### `GET /api/auth/me`

返回当前登录用户信息。

响应字段：

- `id`
- `username`
- `displayName`
- `avatarDataUrl`
- `role` — 基础角色（`USER` 或 `ADMIN`，旧 `TENANT_ADMIN` 枚举已统一为 `USER` + `isTenantAdmin=true`）
- `effectiveRole` — 实际生效角色（`USER` / `TENANT_ADMIN` / `ADMIN`）
- `isTenantAdmin` — 是否为租户管理员
- `tenantId` — 所属租户 ID
- `authorities` — Spring Security 权限字符串列表
- `status` — 账号状态（`PENDING` / `ACTIVE` / `REJECTED` / `DISABLED`）

### `PATCH /api/auth/profile`

更新当前登录用户的昵称与头像。

请求体示例：

```json
{
  "displayName": "Alice",
  "avatarDataUrl": "data:image/png;base64,..."
}
```

说明：

- `displayName` 可为空字符串
- `avatarDataUrl` 可为空字符串，表示移除自定义头像
- 支持 `png / jpg / jpeg / webp / gif` 的 Data URL

### `POST /api/auth/logout`

清理当前 Session。

## 4. 模型发现

### `GET /api/models`

返回当前所有**已启用**且其 Provider 也已启用的模型。

说明：

- 返回的 `id` 是模型 UUID
- 聊天接口中的 `model` 字段应传这个 UUID，而不是裸模型名

响应示例：

```json
{
  "models": [
    {
      "id": "f8b6...",
      "modelId": "openai/gpt-4o-mini",
      "displayName": "GPT-4o Mini",
      "providerName": "OpenRouter",
      "tags": ["chat"],
      "capabilities": ["text"]
    }
  ]
}
```

## 5. 聊天

### `POST /api/chat`

执行一次完整问答。

请求体常用字段：

```json
{
  "sessionId": "",
  "message": "什么是 BM25？",
  "topK": 6,
  "kbIds": ["kb_xxx", "kb_yyy"],
  "kbTopK": {"kb_xxx": 4},
  "model": "f8b6...",
  "appendUser": true,
  "contextSize": 6
}
```

字段说明：

- `sessionId`：为空时由服务端创建会话
- `message`：用户问题
- `topK`：总体检索上限；实际每个知识库还会叠加各自 `documentCount` 配置
- `kbIds`：指定要检索的知识库列表
  - 省略或传 `null`：默认检索当前用户当前可访问的全部知识库（包括系统知识库 `公司章程` 与管理员创建的全体知识库）
  - 传空数组：表示不使用知识库
- `kbTopK`：细粒度覆盖单个知识库的检索数量，格式为 `{kbId: 数量}`；省略时各知识库使用各自的 `documentCount` 配置
- `model`：可选，填 `GET /api/models` 返回的模型 UUID
- `appendUser`：是否把用户问题附加到 prompt 中
- `contextSize`：放入 prompt 的历史消息条数，默认 `0`（不携带历史）

响应示例：

```json
{
  "sessionId": "ses_xxx",
  "answer": "BM25 是一种...",
  "answerHash": "a0d9...",
  "citations": [
    {
      "docId": "doc_xxx",
      "filename": "bm25.txt",
      "chunkId": "chk_xxx",
      "chunkIndex": 0,
      "sourceHint": "bm25.txt#chunk=0",
      "snippet": "BM25 is a ranking function...",
      "kbId": "kb_xxx"
    }
  ]
}
```

### `GET /api/chat/stream`

SSE 形式返回问答结果。

常用查询参数：

- `sessionId`
- `message`（必填）
- `topK`（默认 `6`）
- `kbs`：逗号 / 空格分隔的知识库 ID
- `useKb`：`true | false`，默认 `true`
- `model`：模型 UUID
- `appendUser`：默认 `true`
- `contextSize`（默认 `0`）：放入 prompt 的历史消息条数

说明：

- `kbs` 为空且 `useKb=true` 时，默认使用当前用户全部知识库
- `useKb=false` 时，不走知识库检索
- 认证方式使用现有 Session Cookie 或 `Authorization: Basic ...` 请求头
- SSE 事件包括：
  - `token`：分段文本
  - `meta`：最终的 `sessionId / answerHash / citations`

## 6. 会话

### `GET /api/sessions`

列出当前用户的全部会话。

### `POST /api/sessions`

创建会话。

请求体示例：

```json
{ "title": "新会话" }
```

### `GET /api/sessions/{id}`

读取单个会话详情。

### `PATCH /api/sessions/{id}`

修改会话标题。

请求体示例：

```json
{ "title": "重命名后的标题" }
```

### `DELETE /api/sessions/{id}`

删除会话。

## 7. 用户留言

普通用户向管理员发送留言，并查看回复。

### `POST /api/messages`

发送留言。

请求体：

```json
{
  "subject": "关于知识库无法检索的问题",
  "content": "我上传了 PDF 文件，但搜索时找不到相关内容，请帮忙排查。"
}
```

响应：`{ "id": "...", "status": "OPEN", "createdAt": "..." }`

### `GET /api/messages`

查看当前用户发送的所有留言及管理员回复。

响应示例：

```json
[
  {
    "id": "uuid",
    "subject": "关于知识库无法检索的问题",
    "content": "...",
    "status": "REPLIED",
    "reply": "已排查，请重新上传。",
    "repliedAt": "2026-03-15T10:00:00Z",
    "createdAt": "2026-03-14T08:00:00Z"
  }
]
```

`status` 可能值：`OPEN` / `REPLIED` / `CLOSED`

### `DELETE /api/messages/{id}`

删除自己发送的留言（仅限未回复的 `OPEN` 状态留言）。

---

## 8. 用户知识库

### `GET /api/kbs`

列出当前用户可见的知识库，包括：

- 系统知识库 `公司章程`（知识库 ID 固定为 `default`，不可删除，仅管理员可维护）
- 当前用户自己绑定的知识库
- 管理员创建并显式公开的全体知识库
- 为兼容旧数据，当前仍保留“未绑定给任何用户的知识库默认视为公开”的兜底逻辑

返回项包含：

- `id`
- `name`
- `embeddingMode`
- `embeddingModel`
- `embeddingBaseUrl`
- `createdAt`
- `updatedAt`
- `isDefault`
- `isPublic`
- `owned`
- `isSystem`
- `documentCount`

### `POST /api/kbs`

创建当前用户的知识库。

请求体：

```json
{
  "name": "我的知识库",
  "embeddingMode": "api",
  "embeddingModel": "7f4d0b9b-2ad2-4c74-8f1c-3f1cb8d1b6c2",
  "embeddingBaseUrl": null,
  "documentCount": 6
}
```

字段说明：

- `embeddingMode`：`local | api`
- `embeddingModel` / `embeddingBaseUrl`：仅在 `embeddingMode=api` 时有意义
- 推荐做法：`embeddingModel` 直接传后台“模型管理”中已启用的嵌入模型 ID（UUID），此时 `embeddingBaseUrl` 传 `null`
- 兼容做法：如果仍想直连外部 OpenAI-compatible embedding 接口，也可以传普通模型名 + `embeddingBaseUrl`
- `documentCount`：该知识库聊天时默认取回文档数，范围会被限制在 `1..50`

### `DELETE /api/kbs/{kbId}`

删除当前用户自己的知识库。

限制：

- 系统知识库 `公司章程` 不可删除
- 只能删除当前用户自己绑定的知识库，不能删除仅”可见但不归属”的公共知识库
- 删除后系统必须仍至少保留一个”当前用户可访问的知识库”

### `PATCH /api/kbs/{kbId}`

更新当前用户自己的知识库信息。

请求体：

```json
{
  “name”: “新名称”,
  “documentCount”: 8
}
```

字段说明：

- `name`：知识库名称
- `documentCount`：聊天时默认取回文档数，范围 `1..50`

限制：

- 只能更新当前用户自己绑定的知识库

### `GET /api/kbs/{kbId}/chunks/{chunkId}`

查看指定 chunk 详情。

说明：

- 只要该知识库对当前用户“可访问”，即可查看引用片段详情
- 因此系统知识库 `公司章程` 与管理员创建的全体知识库中的引用，也可以被普通用户点开查看

### `GET /api/kbs/{kbId}/documents`

列出该知识库下的文档。

### `POST /api/kbs/{kbId}/documents/upload`

上传文档到指定知识库。

请求类型：`multipart/form-data`

表单字段：

- `file`：必填
- `category`：可选
- `tags`：可选，支持逗号 / 空格分隔

### `POST /api/kbs/{kbId}/documents/batch`

批量上传多个文档到指定知识库。

**权限**：仅限当前用户**自有**知识库；可访问但不归属用户的公共知识库（管理员创建）不可作为目标，传入将返回 403。

请求类型：`multipart/form-data`

表单字段：

- `files`：必填，可选择多个文件

响应示例：

```json
{
  "results": [
    { "filename": "a.txt", "docId": "doc_xxx", "status": "ok" },
    { "filename": "b.pdf", "status": "error", "error": "parse failed" }
  ]
}
```

### 断点续传接口

适用于大文件（建议 5 MB 以上）。

#### `POST /api/kbs/{kbId}/uploads`

初始化上传会话。

**权限**：仅限当前用户**自有**知识库（与批量上传相同，公共知识库传入返回 403）。

请求体：

```json
{
  "filename": "large-doc.pdf",
  "totalSize": 52428800,
  "contentType": "application/pdf"
}
```

响应示例：

```json
{
  "uploadId": "550e8400-e29b-41d4-a716-446655440000",
  "chunkSize": 4194304,
  "filename": "large-doc.pdf",
  "totalSize": 52428800,
  "status": "UPLOADING"
}
```

#### `PATCH /api/kbs/{kbId}/uploads/{uploadId}`

上传一个分片。

请求头：

```http
Content-Range: bytes 0-4194303/52428800
Content-Type: application/octet-stream
```

请求体：分片二进制数据。

响应示例：

```json
{ "received": 4194304, "total": 52428800, "status": "UPLOADING" }
```

最后一片上传后：

```json
{ "received": 52428800, "total": 52428800, "status": "COMPLETE" }
```

#### `GET /api/kbs/{kbId}/uploads/{uploadId}`

查询上传进度。

响应示例：

```json
{ "received": 12582912, "total": 52428800, "status": "UPLOADING" }
```

`status` 可能值：`UPLOADING` / `COMPLETE` / `INGEST_ERROR`

### `DELETE /api/kbs/{kbId}/documents/{docId}`

删除指定文档。

## 9. 管理员：用户审批

### `GET /api/admin/users`

查询参数：

- `status`：可选，支持 `PENDING | ACTIVE | REJECTED | DISABLED`

### `POST /api/admin/users/{id}/approve`

审批通过用户，并确保其登录后至少拥有一个可访问知识库。

当前默认行为：

- 系统知识库 `公司章程` 会对所有用户自动可见
- 如果管理员后续再创建“全体知识库”，新老用户也都会自动可见

### `POST /api/admin/users/{id}/reject`

驳回用户。

### `POST /api/admin/users/{id}/disable`

禁用用户账号，但**不删除任何用户数据**。

限制：

- 仅 `ADMIN` 可调用
- 只能禁用当前处于 `ACTIVE` 状态的非管理员用户
- 不能禁用当前登录的管理员自己
- 被禁用后用户登录会收到 `403` 与提示“您的账号已被禁用，请联系管理员”

### `POST /api/admin/users/{id}/restore`

将 `DISABLED` 状态的用户恢复为 `ACTIVE`。

限制：

- 仅 `ADMIN` 可调用
- 仅允许 `DISABLED -> ACTIVE`
- 不影响该用户已有数据、租户归属、租户管理员标记与知识库绑定

### `POST /api/admin/users/{id}/tenant-admin`

将指定租户用户授予为租户管理员。

限制：

- 仅 `ADMIN` 可调用
- 目标用户必须已归属某个租户
- 成功后返回 `{ "ok": true, "user": ... }`，其中 `user` 为更新后的用户行，`effectiveRole` 会变为 `TENANT_ADMIN`

### `DELETE /api/admin/users/{id}/tenant-admin`

撤销指定用户的租户管理员身份。

限制：

- 仅 `ADMIN` 可调用
- 成功后返回 `{ "ok": true, "user": ... }`，其中 `user` 为更新后的用户行，`effectiveRole` 会恢复为 `USER`

## 10. 管理员：知识库与文档

### 系统知识库快捷接口（公司章程 / `default`）

这些接口默认作用于固定知识库 `default`：

- 仅当 `default` 知识库仍然存在时可用
- 当前 `default` 对应系统知识库 `公司章程`，不可删除，仅管理员可维护
- 新接入依然更建议优先使用下面的显式知识库接口

- `POST /api/admin/upload`
- `GET /api/admin/documents`
- `DELETE /api/admin/documents/{id}`
- `POST /api/admin/reindex`
- `GET /api/admin/stats`

### 推荐的知识库显式接口

- `GET /api/admin/kbs`
- `POST /api/admin/kbs`
- `DELETE /api/admin/kbs/{id}`
- `POST /api/admin/kbs/{kbId}/upload`
- `GET /api/admin/kbs/{kbId}/documents`
- `DELETE /api/admin/kbs/{kbId}/documents/{id}`
- `POST /api/admin/kbs/{kbId}/reindex`
- `GET /api/admin/kbs/{kbId}/stats`

其中：

- `GET /api/admin/kbs` 返回项额外带有 `isPublic`，用于标识该知识库是否对普通用户公开
- `GET /api/admin/kbs` 返回项额外带有 `isSystem`，用于标识是否为系统知识库 `公司章程`
- `GET /api/admin/stats` 与 `GET /api/admin/kbs/{kbId}/stats` 当前主要返回 `documents / chunks / sessions`，管理后台默认不再展示反馈统计

### `POST /api/admin/kbs`

请求体：

```json
{
  "name": "公共知识库",
  "embeddingMode": "local",
  "embeddingModel": null,
  "embeddingBaseUrl": null
}
```

说明：

- 管理员通过该接口创建的知识库，当前会默认标记为公开，普通用户可在 `/api/kbs` 中看到它
- 这些知识库在产品语义上属于“全体知识库”：所有用户都可见，但默认只有管理员可维护
- 删除管理员知识库时，系统会强制要求“全局至少保留一个知识库”

### `DELETE /api/admin/kbs/{id}`

限制：

- 系统知识库 `公司章程`（`default`）不可删除
- 其余知识库删除时，系统会强制要求“全局至少保留一个知识库”

### `POST /api/admin/kbs/{kbId}/upload`

请求类型：`multipart/form-data`

字段：

- `file`：必填
- `category`：可选
- `tags`：可选

### `POST /api/admin/kbs/{kbId}/documents/batch`

管理员批量上传多个文档。

请求类型：`multipart/form-data`，字段名 `files`（多文件）。

响应格式同用户批量上传（`results` 数组）。

### `POST /api/admin/kbs/{kbId}/documents/import-zip`

管理员上传 ZIP 文件，系统自动解压并将其中全部支持格式文档批量入库。

请求类型：`multipart/form-data`，字段名 `file`（单个 ZIP 文件）。

### `POST /api/admin/kbs/{kbId}/uploads`

管理员初始化断点续传会话（协议与用户接口相同，见第 8 章断点续传接口）。

## 11. 管理员：Provider 与模型

### `GET /api/admin/providers`

列出所有已录入的 Provider。

返回项包括：

- `id`
- `name`
- `baseUrl`
- `enabled`
- `apiKeyMasked`
- `createdAt`
- `updatedAt`

### `POST /api/admin/providers`

创建 Provider。

请求体：

```json
{
  "name": "OpenRouter",
  "baseUrl": "https://openrouter.ai/api/v1",
  "apiKey": "sk-...",
  "enabled": true
}
```

要求：

- `baseUrl` 应是 OpenAI-compatible 根路径
- 末尾是否带 `/` 问题不大，服务端会标准化

### `PATCH /api/admin/providers/{id}`

可更新：

- `name`
- `baseUrl`
- `apiKey`
- `enabled`

### `DELETE /api/admin/providers/{id}`

删除 Provider。

### `GET /api/admin/providers/{id}/openrouter/models`

从远端 `/models` 接口抓取模型清单并做本地对照，不落库。

### `POST /api/admin/providers/{id}/openrouter/sync-models`

将远端模型同步到本地数据库。

说明：

- 新同步出来的模型默认 `enabled=false`
- 需要管理员手动启用后，`GET /api/models` 才会返回它们

### `GET /api/admin/models`

可选查询参数：

- `providerId`

### `PATCH /api/admin/models/{id}`

请求体：

```json
{
  "displayName": "Claude 3.7 Sonnet",
  "enabled": true
}
```

### `DELETE /api/admin/models/{id}`

删除模型记录。

---

## 12. 管理员：用户留言管理

### `GET /api/admin/messages`

查询参数：

- `status`：可选，`OPEN | REPLIED | CLOSED`；不填返回全部

响应：留言对象数组，每项包含 `id / subject / content / status / reply / repliedAt / userId / createdAt`。

### `POST /api/admin/messages/{id}/reply`

管理员回复留言，回复后 `status` 自动更新为 `REPLIED`。

请求体：

```json
{ "reply": "您好，问题已排查，原因是文档格式不支持，请转换后重新上传。" }
```

### `PATCH /api/admin/messages/{id}`

管理员修改留言状态（如关闭）。

请求体：

```json
{ "status": "CLOSED" }
```

---

## 13. 管理员：租户管理

### `GET /api/admin/tenants`

列出所有租户。

响应项包含：`id / name / slug / inviteCode / enabled / createdAt`

### `POST /api/admin/tenants`

创建租户（系统自动生成唯一邀请码）。

请求体：

```json
{
  "name": "ACME 科技",
  "slug": "acme"
}
```

说明：

- `slug`：租户唯一短名（小写字母 + 连字符），用于内部标识
- `inviteCode` 由系统随机生成，返回于响应中；用户注册时使用此码归属该租户

### `PATCH /api/admin/tenants/{id}`

修改租户名称或启停状态。

请求体：

```json
{
  "name": "ACME 科技（新）",
  "enabled": false
}
```

### `POST /api/admin/tenants/{id}/rotate-code`

重新生成租户邀请码（旧码立即失效）。

响应：`{ "inviteCode": "NEW_CODE_XXXX" }`

---

## 14. 租户管理员 API（`/api/tenant/**`）

需要 `ADMIN` 或 `TENANT_ADMIN` 角色。`TENANT_ADMIN` 只能访问本租户数据。

### `GET /api/tenant/users`

查看本租户下所有用户（含 `PENDING` / `ACTIVE` / `REJECTED` / `DISABLED` 状态）。

### `POST /api/tenant/users/{id}/approve`

审批本租户用户（将状态从 `PENDING` 改为 `ACTIVE`）。

### `POST /api/tenant/users/{id}/reject`

驳回本租户用户。

### `GET /api/tenant/kbs`

查看本租户下所有知识库。






