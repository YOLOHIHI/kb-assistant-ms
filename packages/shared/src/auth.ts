export type UserRole = 'USER' | 'TENANT_ADMIN' | 'ADMIN'
export type BaseUserRole = 'USER' | 'ADMIN'
export type UserStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'DISABLED'

export interface CurrentUser {
  id: string
  username: string
  displayName: string
  avatarDataUrl: string
  role: BaseUserRole
  effectiveRole: UserRole
  isTenantAdmin: boolean
  tenantId: string
  authorities: string[]
  status: UserStatus
}

export const SUPER_ADMIN_SECTIONS = ['overview', 'users', 'models', 'kb', 'tenants', 'messages'] as const
export const TENANT_ADMIN_SECTIONS = ['users', 'kb'] as const

export type AdminSection = (typeof SUPER_ADMIN_SECTIONS)[number]

type UserLike = Pick<CurrentUser, 'role' | 'effectiveRole'> | null | undefined

export function getEffectiveRole(user: UserLike): UserRole | '' {
  const role = String(user?.effectiveRole || user?.role || '').toUpperCase()
  if (role === 'ADMIN' || role === 'TENANT_ADMIN' || role === 'USER') {
    return role
  }
  return ''
}

export function canAccessAdminShell(user: UserLike): boolean {
  const role = getEffectiveRole(user)
  return role === 'ADMIN' || role === 'TENANT_ADMIN'
}

export function isSuperAdminUser(user: UserLike): boolean {
  return getEffectiveRole(user) === 'ADMIN'
}

export function isTenantAdminUser(user: UserLike): boolean {
  return getEffectiveRole(user) === 'TENANT_ADMIN'
}

export function getAllowedAdminSections(user: UserLike): readonly AdminSection[] {
  const role = getEffectiveRole(user)
  if (role === 'ADMIN') return SUPER_ADMIN_SECTIONS
  if (role === 'TENANT_ADMIN') return TENANT_ADMIN_SECTIONS
  return []
}
