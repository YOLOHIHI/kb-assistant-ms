// 认证 API 封装

import { ensureCsrfCookie, withCsrf } from '@kb/shared'
import type { CurrentUser } from '@kb/shared/auth'

export type User = CurrentUser

export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  displayName: string
  avatarDataUrl?: string
  inviteCode?: string
}

export interface RegisterResponse {
  ok: boolean
  status: string
}

export interface LogoutResponse {
  ok: boolean
}

async function readErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const text = (await response.text()).trim()
    if (!text) return fallback
    try {
      const data = JSON.parse(text)
      return data?.message || data?.error || fallback
    } catch {
      return text
    }
  } catch {
    return fallback
  }
}

// 获取当前登录用户
export async function getCurrentUser(): Promise<User | null> {
  try {
    const response = await fetch('/api/auth/me', {
      credentials: 'include',
    })
    if (response.status === 401) {
      return null
    }
    if (!response.ok) {
      throw new Error('Failed to fetch user')
    }
    return await response.json()
  } catch {
    return null
  }
}

// 登录
export async function login(data: LoginRequest): Promise<{ user?: User; error?: string }> {
  try {
    await ensureCsrfCookie()
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: withCsrf({
        'Content-Type': 'application/json',
      }, 'POST'),
      credentials: 'include',
      body: JSON.stringify(data),
    })

    if (response.status === 400) {
      return { error: '请输入账号和密码' }
    }
    if (response.status === 401) {
      return { error: await readErrorMessage(response, '账号或密码错误') }
    }
    if (response.status === 403) {
      return { error: await readErrorMessage(response, '账号未启用或待管理员审批') }
    }
    if (!response.ok) {
      return { error: '登录失败，请稍后重试' }
    }

    const user = await response.json()
    return { user }
  } catch {
    return { error: '网络错误，请稍后重试' }
  }
}

// 注册
export async function register(data: RegisterRequest): Promise<{ success?: boolean; error?: string }> {
  try {
    await ensureCsrfCookie()
    const response = await fetch('/api/auth/register', {
      method: 'POST',
      headers: withCsrf({
        'Content-Type': 'application/json',
      }, 'POST'),
      credentials: 'include',
      body: JSON.stringify(data),
    })

    if (response.status === 409) {
      return { error: '用户名已存在' }
    }
    if (response.status === 400) {
      const errorData = await response.json().catch(() => ({}))
      return { error: errorData.message || '字段格式不合法' }
    }
    if (!response.ok) {
      return { error: '注册失败，请稍后重试' }
    }

    return { success: true }
  } catch {
    return { error: '网络错误，请稍后重试' }
  }
}

// 退出登录
export async function logout(): Promise<boolean> {
  try {
    await ensureCsrfCookie()
    const response = await fetch('/api/auth/logout', {
      method: 'POST',
      credentials: 'include',
      headers: withCsrf({}, 'POST'),
    })
    return response.ok
  } catch {
    return false
  }
}
