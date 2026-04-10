import { ensureCsrfCookie, withCsrf } from "@kb/shared"
import { appCopy } from "./copy"

export async function apiFetch(path, options = {}) {
  const { json, silent401 = false, headers, ...rest } = options
  const request = {
    credentials: 'same-origin',
    headers: {
      ...(headers || {}),
    },
    ...rest,
  }

  if (json !== undefined) {
    request.headers['Content-Type'] = 'application/json'
    request.body = JSON.stringify(json)
  }

  const method = String(request.method || 'GET')
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method.toUpperCase())) {
    await ensureCsrfCookie()
  }
  request.headers = withCsrf(request.headers, method)

  const response = await fetch(path, request)

  if (response.status === 401) {
    const error = new Error('UNAUTHORIZED')
    error.status = 401
    if (!silent401) throw error
    return null
  }

  if (!response.ok) {
    let message = `HTTP ${response.status}`
    try {
      const text = await response.text()
      if (text) {
        try {
          const data = JSON.parse(text)
          message = data?.message || data?.error || data?.reason || text
        } catch {
          message = text
        }
      }
    } catch {}
    const error = new Error(message)
    error.status = response.status
    throw error
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) return response.json()
  return response.text()
}

export function safeJsonParse(value, fallback) {
  if (!value) return fallback
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

export function readStored(key, fallback) {
  if (typeof window === 'undefined') return fallback
  try {
    const raw = window.localStorage.getItem(key)
    return safeJsonParse(raw, fallback)
  } catch {
    return fallback
  }
}

export function writeStored(key, value) {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(key, JSON.stringify(value))
  } catch {}
}

export function readTextStorage(key, fallback = '') {
  if (typeof window === 'undefined') return fallback
  try {
    const value = window.localStorage.getItem(key)
    return value == null ? fallback : value
  } catch {
    return fallback
  }
}

export function writeTextStorage(key, value) {
  if (typeof window === 'undefined') return
  try {
    if (value == null || value === '') window.localStorage.removeItem(key)
    else window.localStorage.setItem(key, String(value))
  } catch {}
}

export function createClientId(prefix = 'id') {
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`
}

export function displayNameForUser(user) {
  if (!user) return appCopy.guestLabel
  return String(user.displayName || user.username || appCopy.guestLabel).trim() || appCopy.guestLabel
}

export function initials(text) {
  const src = String(text || '').trim()
  if (!src) return 'AI'
  const parts = src.split(/\s+/).filter(Boolean)
  if (parts.length >= 2) return `${parts[0][0]}${parts[1][0]}`.toUpperCase()
  return src.slice(0, 2).toUpperCase()
}

export function formatDateTime(value) {
  if (!value) return ''
  try {
    return new Date(value).toLocaleString()
  } catch {
    return String(value)
  }
}

export function bytesToSize(size) {
  const num = Number(size || 0)
  if (!Number.isFinite(num) || num <= 0) return '0 KB'
  const units = ['B', 'KB', 'MB', 'GB']
  let index = 0
  let current = num
  while (current >= 1024 && index < units.length - 1) {
    current /= 1024
    index += 1
  }
  return `${current >= 10 || index === 0 ? current.toFixed(0) : current.toFixed(1)} ${units[index]}`
}

export function normalizeKbDocumentsResponse(data) {
  const documents = Array.isArray(data?.documents) ? data.documents : []
  return documents.map((doc, index) => {
    const sizeBytes = Number(doc?.sizeBytes ?? doc?.size ?? doc?.fileSize ?? 0)
    return {
      id: doc.id || doc.documentId || `doc_${index}`,
      name: doc.filename || doc.name || doc.originalFilename || `文档 ${index + 1}`,
      size: bytesToSize(sizeBytes),
      sizeBytes,
      uploadedAt: doc.createdAt || doc.uploadedAt || doc.updatedAt || '',
      raw: doc,
    }
  })
}
