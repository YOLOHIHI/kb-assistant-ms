import { ensureCsrfCookie, withCsrf } from "@kb/shared"
import {
  canAccessAdminShell,
  getAllowedAdminSections,
  getEffectiveRole,
  isSuperAdminUser,
  isTenantAdminUser,
} from "@kb/shared/auth"

class ApiError extends Error {
  constructor(message, status, body) {
    super(message)
    this.name = "ApiError"
    this.status = status
    this.body = body
  }
}

const AVATAR_TYPES = ["image/png", "image/jpg", "image/jpeg", "image/webp", "image/gif"]
const MAX_AVATAR_DATA_URL_LENGTH = 700_000

function parseErrorMessage(status, text) {
  const raw = String(text || "").trim()
  if (!raw) return `HTTP ${status}`

  try {
    const data = JSON.parse(raw)
    return data?.message || data?.error || data?.reason || raw
  } catch {
    return raw
  }
}

export async function apiFetch(path, options = {}) {
  const { json, silent401 = false, headers, ...rest } = options
  const request = {
    credentials: "same-origin",
    headers: {
      ...(headers || {}),
    },
    ...rest,
  }

  if (json !== undefined) {
    request.headers["Content-Type"] = "application/json"
    request.body = JSON.stringify(json)
  }

  const method = String(request.method || "GET")
  if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method.toUpperCase())) {
    await ensureCsrfCookie()
  }
  request.headers = withCsrf(request.headers, method)

  const response = await fetch(path, request)

  if (response.status === 401) {
    const error = new ApiError("登录已失效，请重新登录", 401)
    if (!silent401) throw error
    return null
  }

  if (!response.ok) {
    let text = ""
    try {
      text = await response.text()
    } catch {}
    throw new ApiError(parseErrorMessage(response.status, text), response.status, text)
  }

  const contentType = response.headers.get("content-type") || ""
  if (contentType.includes("application/json")) return response.json()
  return response.text()
}

export function requireAdminUser(user) {
  if (!user) throw new ApiError("登录已失效，请重新登录", 401)
  if (!canAccessAdminShell(user)) {
    throw new ApiError("当前账号无法访问管理后台", 403)
  }
  return user
}

export { getAllowedAdminSections, getEffectiveRole, isSuperAdminUser } from "@kb/shared/auth"

export function isUnauthorizedError(error) {
  return Number(error?.status) === 401
}

export function isForbiddenError(error) {
  return Number(error?.status) === 403
}

export async function getCurrentUser({ silent401 = true } = {}) {
  return apiFetch("/api/auth/me", { silent401 })
}

export async function loginAdmin(credentials) {
  const user = await apiFetch("/api/auth/login", {
    method: "POST",
    json: credentials,
  })
  return requireAdminUser(user)
}

export async function updateMyProfile(payload) {
  const user = await apiFetch("/api/auth/profile", {
    method: "PATCH",
    json: payload,
  })
  return requireAdminUser(user)
}

export async function logoutAdmin() {
  return apiFetch("/api/auth/logout", {
    method: "POST",
    silent401: true,
  })
}

export async function listAdminUsers(status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : ""
  return apiFetch(`/api/admin/users${query}`)
}

export async function listTenantUsers(status) {
  const data = await apiFetch("/api/tenant/users")
  if (!status || !Array.isArray(data?.users)) return data
  return {
    ...data,
    users: data.users.filter((user) => user?.status === status),
  }
}

export async function approveAdminUser(id) {
  return apiFetch(`/api/admin/users/${encodeURIComponent(id)}/approve`, {
    method: "POST",
  })
}

export async function approveTenantUser(id) {
  return apiFetch(`/api/tenant/users/${encodeURIComponent(id)}/approve`, {
    method: "POST",
  })
}

export async function rejectAdminUser(id) {
  return apiFetch(`/api/admin/users/${encodeURIComponent(id)}/reject`, {
    method: "POST",
  })
}

export async function disableAdminUser(id) {
  return apiFetch(`/api/admin/users/${encodeURIComponent(id)}/disable`, {
    method: "POST",
  })
}

export async function restoreAdminUser(id) {
  return apiFetch(`/api/admin/users/${encodeURIComponent(id)}/restore`, {
    method: "POST",
  })
}

export async function rejectTenantUser(id) {
  return apiFetch(`/api/tenant/users/${encodeURIComponent(id)}/reject`, {
    method: "POST",
  })
}

export async function grantTenantAdminRole(id) {
  return apiFetch(`/api/admin/users/${encodeURIComponent(id)}/tenant-admin`, {
    method: "POST",
  })
}

export async function revokeTenantAdminRole(id) {
  return apiFetch(`/api/admin/users/${encodeURIComponent(id)}/tenant-admin`, {
    method: "DELETE",
  })
}

export async function listScopedUsers(user, status) {
  return isTenantAdminUser(user) ? listTenantUsers(status) : listAdminUsers(status)
}

export async function approveScopedUser(user, id) {
  return isTenantAdminUser(user) ? approveTenantUser(id) : approveAdminUser(id)
}

export async function rejectScopedUser(user, id) {
  return isTenantAdminUser(user) ? rejectTenantUser(id) : rejectAdminUser(id)
}

export async function listProviders() {
  return apiFetch("/api/admin/providers")
}

export async function createProvider(payload) {
  return apiFetch("/api/admin/providers", {
    method: "POST",
    json: payload,
  })
}

export async function deleteProvider(id) {
  return apiFetch(`/api/admin/providers/${encodeURIComponent(id)}`, {
    method: "DELETE",
  })
}

export async function syncProviderModels(id) {
  return apiFetch(`/api/admin/providers/${encodeURIComponent(id)}/openrouter/models?sync=true`)
}

export async function listAdminModels(providerId) {
  const query = providerId ? `?providerId=${encodeURIComponent(providerId)}` : ""
  return apiFetch(`/api/admin/models${query}`)
}

export async function updateAdminModel(id, payload) {
  return apiFetch(`/api/admin/models/${encodeURIComponent(id)}`, {
    method: "PATCH",
    json: payload,
  })
}

export async function listEnabledModels() {
  return apiFetch("/api/models")
}

export async function listAdminKbs() {
  return apiFetch("/api/admin/kbs")
}

export async function listTenantKbs() {
  return apiFetch("/api/tenant/kbs")
}

export async function createAdminKb(payload) {
  return apiFetch("/api/admin/kbs", {
    method: "POST",
    json: payload,
  })
}

export async function createTenantKb(payload) {
  return apiFetch("/api/tenant/kbs", {
    method: "POST",
    json: payload,
  })
}

export async function deleteAdminKb(id) {
  return apiFetch(`/api/admin/kbs/${encodeURIComponent(id)}`, {
    method: "DELETE",
  })
}

export async function deleteTenantKb(id) {
  return apiFetch(`/api/tenant/kbs/${encodeURIComponent(id)}`, {
    method: "DELETE",
  })
}

export async function getAdminKbStats(id) {
  return apiFetch(`/api/admin/kbs/${encodeURIComponent(id)}/stats`)
}

export async function getTenantKbStats(id) {
  return apiFetch(`/api/tenant/kbs/${encodeURIComponent(id)}/stats`)
}

export async function listAdminKbDocuments(id) {
  return apiFetch(`/api/admin/kbs/${encodeURIComponent(id)}/documents`)
}

export async function listTenantKbDocuments(id) {
  return apiFetch(`/api/tenant/kbs/${encodeURIComponent(id)}/documents`)
}

export async function uploadAdminKbDocument(id, file, extra = {}) {
  const body = new FormData()
  body.append("file", file, file.name)

  const category = String(extra.category || "").trim()
  const tags = String(extra.tags || "").trim()
  if (category) body.append("category", category)
  if (tags) body.append("tags", tags)

  return apiFetch(`/api/admin/kbs/${encodeURIComponent(id)}/upload`, {
    method: "POST",
    body,
  })
}

export async function uploadTenantKbDocument(id, file, extra = {}) {
  const body = new FormData()
  body.append("file", file, file.name)

  const category = String(extra.category || "").trim()
  const tags = String(extra.tags || "").trim()
  if (category) body.append("category", category)
  if (tags) body.append("tags", tags)

  return apiFetch(`/api/tenant/kbs/${encodeURIComponent(id)}/upload`, {
    method: "POST",
    body,
  })
}

export async function deleteAdminKbDocument(kbId, documentId) {
  return apiFetch(`/api/admin/kbs/${encodeURIComponent(kbId)}/documents/${encodeURIComponent(documentId)}`, {
    method: "DELETE",
  })
}

export async function deleteTenantKbDocument(kbId, documentId) {
  return apiFetch(`/api/tenant/kbs/${encodeURIComponent(kbId)}/documents/${encodeURIComponent(documentId)}`, {
    method: "DELETE",
  })
}

export async function reindexAdminKb(id) {
  return apiFetch(`/api/admin/kbs/${encodeURIComponent(id)}/reindex`, {
    method: "POST",
  })
}

export async function reindexTenantKb(id) {
  return apiFetch(`/api/tenant/kbs/${encodeURIComponent(id)}/reindex`, {
    method: "POST",
  })
}

export async function listScopedKbs(user) {
  return isTenantAdminUser(user) ? listTenantKbs() : listAdminKbs()
}

export async function createScopedKb(user, payload) {
  return isTenantAdminUser(user) ? createTenantKb(payload) : createAdminKb(payload)
}

export async function deleteScopedKb(user, id) {
  return isTenantAdminUser(user) ? deleteTenantKb(id) : deleteAdminKb(id)
}

export async function getScopedKbStats(user, id) {
  return isTenantAdminUser(user) ? getTenantKbStats(id) : getAdminKbStats(id)
}

export async function listScopedKbDocuments(user, id) {
  return isTenantAdminUser(user) ? listTenantKbDocuments(id) : listAdminKbDocuments(id)
}

export async function uploadScopedKbDocument(user, id, file, extra = {}) {
  return isTenantAdminUser(user)
    ? uploadTenantKbDocument(id, file, extra)
    : uploadAdminKbDocument(id, file, extra)
}

export async function deleteScopedKbDocument(user, kbId, documentId) {
  return isTenantAdminUser(user)
    ? deleteTenantKbDocument(kbId, documentId)
    : deleteAdminKbDocument(kbId, documentId)
}

export async function reindexScopedKb(user, id) {
  return isTenantAdminUser(user) ? reindexTenantKb(id) : reindexAdminKb(id)
}

export async function listTenants() {
  return apiFetch("/api/admin/tenants")
}

export async function createTenant(payload) {
  return apiFetch("/api/admin/tenants", {
    method: "POST",
    json: payload,
  })
}

export async function updateTenant(id, payload) {
  return apiFetch(`/api/admin/tenants/${encodeURIComponent(id)}`, {
    method: "PATCH",
    json: payload,
  })
}

export async function rotateTenantCode(id) {
  return apiFetch(`/api/admin/tenants/${encodeURIComponent(id)}/rotate-code`, {
    method: "POST",
    json: {},
  })
}

export async function listAdminMessages(status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : ""
  return apiFetch(`/api/admin/messages${query}`)
}

export async function replyAdminMessage(id, reply) {
  return apiFetch(`/api/admin/messages/${encodeURIComponent(id)}/reply`, {
    method: "POST",
    json: { reply },
  })
}

export async function updateAdminMessageStatus(id, status) {
  return apiFetch(`/api/admin/messages/${encodeURIComponent(id)}`, {
    method: "PATCH",
    json: { status },
  })
}

export function normalizeKnowledgeBases(data) {
  const kbs = Array.isArray(data?.kbs) ? data.kbs : []
  return kbs.map((kb, index) => ({
    id: kb?.id || `kb_${index}`,
    name: kb?.name || `知识库 ${index + 1}`,
    embeddingMode: kb?.embeddingMode || "local",
    embeddingModel: kb?.embeddingModel || "",
    documentCount: Number(kb?.documentCount || 0),
    isPublic: Boolean(kb?.isPublic),
    isSystem: Boolean(kb?.isSystem),
  }))
}

export function normalizeKbStats(data) {
  return {
    documents: Number(data?.documents || 0),
    chunks: Number(data?.chunks || 0),
    sessions: Number(data?.sessions || 0),
  }
}

export function normalizeDocumentsResponse(data) {
  const documents = Array.isArray(data?.documents) ? data.documents : []
  return documents.map((doc, index) => {
    const sizeBytes = Number(doc?.sizeBytes ?? doc?.size ?? doc?.fileSize ?? 0)
    return {
      id: doc?.id || doc?.documentId || `doc_${index}`,
      name: doc?.filename || doc?.name || doc?.originalFilename || `文档 ${index + 1}`,
      category: doc?.category || "",
      tags: Array.isArray(doc?.tags) ? doc.tags.map((tag) => String(tag)) : [],
      uploadedAt: doc?.uploadedAt || doc?.createdAt || doc?.updatedAt || "",
      sizeBytes,
      sizeText: bytesToSize(sizeBytes),
      raw: doc,
    }
  })
}

export function getModelTags(model) {
  if (!Array.isArray(model?.tags)) return []
  return model.tags
    .map((tag) => {
      if (typeof tag === "string") return tag.trim().toLowerCase()
      if (tag && typeof tag === "object") {
        return String(tag.key || tag.id || tag.name || tag.label || "")
          .trim()
          .toLowerCase()
      }
      return ""
    })
    .filter(Boolean)
}

export function filterEmbeddingModels(models) {
  return Array.isArray(models)
    ? models.filter((model) => getModelTags(model).includes("embed"))
    : []
}

export function formatDateTime(value) {
  if (!value) return "-"
  try {
    return new Date(value).toLocaleString("zh-CN")
  } catch {
    return String(value)
  }
}

export function bytesToSize(size) {
  const num = Number(size || 0)
  if (!Number.isFinite(num) || num <= 0) return "0 KB"
  const units = ["B", "KB", "MB", "GB"]
  let index = 0
  let current = num
  while (current >= 1024 && index < units.length - 1) {
    current /= 1024
    index += 1
  }
  return `${current >= 10 || index === 0 ? current.toFixed(0) : current.toFixed(1)} ${units[index]}`
}

export function getUserRoleLabel(role) {
  const value = String(role || "").toUpperCase()
  if (value === "ADMIN") return "管理员"
  if (value === "TENANT_ADMIN") return "租户管理员"
  return "用户"
}

export function getInitials(text) {
  const clean = String(text || "").trim()
  if (!clean) return "AD"
  return clean.slice(0, 2).toUpperCase()
}

export function getDisplayName(user) {
  return String(user?.displayName || user?.username || "管理员").trim() || "管理员"
}

export async function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ""))
    reader.onerror = () => reject(new Error("读取图片失败，请重试"))
    reader.readAsDataURL(file)
  })
}

export async function validateAvatarFile(file) {
  if (!file) return { valid: false, error: "请选择图片" }
  if (!AVATAR_TYPES.includes(file.type)) {
    return { valid: false, error: "仅支持 PNG、JPG、JPEG、WebP、GIF 格式的图片" }
  }

  const dataUrl = await readFileAsDataUrl(file)
  if (dataUrl.length > MAX_AVATAR_DATA_URL_LENGTH) {
    return { valid: false, error: "头像文件过大，请压缩后再上传" }
  }

  return { valid: true, dataUrl }
}

export async function copyToClipboard(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    const textarea = document.createElement("textarea")
    textarea.value = text
    textarea.setAttribute("readonly", "readonly")
    textarea.style.position = "fixed"
    textarea.style.opacity = "0"
    document.body.appendChild(textarea)
    textarea.select()
    const copied = document.execCommand("copy")
    document.body.removeChild(textarea)
    return copied
  }
}
