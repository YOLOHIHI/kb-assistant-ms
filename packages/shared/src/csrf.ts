export function readCsrfToken(): string {
  if (typeof document === 'undefined') return ''
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : ''
}

let pendingCsrfBootstrap: Promise<string> | null = null

export async function ensureCsrfCookie(): Promise<string> {
  if (typeof window === 'undefined') return ''

  const token = readCsrfToken()
  if (token) return token

  if (!pendingCsrfBootstrap) {
    pendingCsrfBootstrap = fetch('/api/auth/csrf', {
      credentials: 'same-origin',
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`Failed to bootstrap CSRF cookie: HTTP ${response.status}`)
        }
        const nextToken = readCsrfToken()
        if (!nextToken) {
          throw new Error('Failed to bootstrap CSRF cookie: token missing from browser cookie jar')
        }
        return nextToken
      })
      .finally(() => {
        pendingCsrfBootstrap = null
      })
  }

  return pendingCsrfBootstrap
}

export function withCsrf(headers: HeadersInit = {}, method = 'GET'): HeadersInit {
  const normalizedMethod = String(method || 'GET').toUpperCase()
  if (['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(normalizedMethod)) {
    return headers
  }

  const token = readCsrfToken()
  if (!token) return headers

  if (headers instanceof Headers) {
    const next = new Headers(headers)
    next.set('X-XSRF-TOKEN', token)
    return next
  }

  if (Array.isArray(headers)) {
    return [...headers, ['X-XSRF-TOKEN', token]]
  }

  return {
    ...headers,
    'X-XSRF-TOKEN': token,
  }
}
