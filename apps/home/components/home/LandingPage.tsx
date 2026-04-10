'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { canAccessAdminShell, getEffectiveRole } from '@kb/shared/auth'
import LandingHeader from './LandingHeader'
import HeroSection from './HeroSection'
import FeatureSection from './FeatureSection'
import WorkflowSection from './WorkflowSection'
import CtaSection from './CtaSection'
import LandingFooter from './LandingFooter'
import LoginDialog from './LoginDialog'
import RegisterDialog from './RegisterDialog'
import { getCurrentUser, logout, type User } from '@/lib/auth-api'
import { getStoredTheme, setStoredTheme, type Theme } from '@/lib/theme-storage'
import { toast } from 'sonner'

export default function LandingPage() {
  const router = useRouter()
  const [user, setUser] = useState<User | null>(null)
  const [theme, setTheme] = useState<Theme>('dark')
  const [loginOpen, setLoginOpen] = useState(false)
  const [registerOpen, setRegisterOpen] = useState(false)
  const [initialized, setInitialized] = useState(false)

  // 初始化：读取主题和用户状态
  useEffect(() => {
    const initTheme = getStoredTheme()
    setTheme(initTheme)
    applyTheme(initTheme)

    getCurrentUser().then((u) => {
      setUser(u)
      setInitialized(true)
    })
  }, [])

  // 应用主题
  const applyTheme = (t: Theme) => {
    if (t === 'dark') {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }

  // 切换主题
  const handleToggleTheme = () => {
    const newTheme: Theme = theme === 'dark' ? 'light' : 'dark'
    setTheme(newTheme)
    setStoredTheme(newTheme)
    applyTheme(newTheme)
  }

  // 登录成功
  const handleLoginSuccess = (loggedInUser: User) => {
    setUser(loggedInUser)
    setLoginOpen(false)
    navigateByUser(loggedInUser)
  }

  // 注册成功
  const handleRegisterSuccess = () => {
    setRegisterOpen(false)
    toast.success('注册成功，请等待管理员审批后登录')
  }

  // 根据角色跳转
  const navigateByUser = useCallback((nextUser: Pick<User, 'role' | 'effectiveRole'> | null | undefined) => {
    if (canAccessAdminShell(nextUser)) {
      router.push('/admin')
    } else {
      router.push('/chat')
    }
  }, [router])

  // 进入系统
  const handleEnterSystem = () => {
    if (user) {
      navigateByUser(user)
    } else {
      setLoginOpen(true)
    }
  }

  // 打开登录弹窗
  const handleOpenLogin = () => {
    setRegisterOpen(false)
    setLoginOpen(true)
  }

  // 打开注册弹窗
  const handleOpenRegister = () => {
    setLoginOpen(false)
    setRegisterOpen(true)
  }

  // 退出登录
  const handleLogout = async () => {
    await logout()
    setUser(null)
    toast.success('已退出登录')
  }

  // 联系管理员
  const handleContactAdmin = () => {
    if (!user) {
      setRegisterOpen(true)
      return
    }
    if (canAccessAdminShell(user)) {
      router.push('/admin')
      return
    }
    toast.info('如需帮助，请联系管理员')
  }

  // 等待初始化完成
  if (!initialized) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <LandingHeader
        isDark={theme === 'dark'}
        isLoggedIn={!!user}
        onToggleTheme={handleToggleTheme}
        onLogin={handleOpenLogin}
        onRegister={handleOpenRegister}
        onLogout={handleLogout}
        onEnterSystem={handleEnterSystem}
      />

      <main>
        <HeroSection
          isLoggedIn={!!user}
          onEnterSystem={handleEnterSystem}
          onLogin={handleOpenLogin}
        />
        <FeatureSection />
        <WorkflowSection />
        <CtaSection
          isLoggedIn={!!user}
          userRole={getEffectiveRole(user)}
          onEnterSystem={handleEnterSystem}
          onLogin={handleOpenLogin}
          onContactAdmin={handleContactAdmin}
        />
      </main>

      <LandingFooter />

      <LoginDialog
        open={loginOpen}
        onOpenChange={setLoginOpen}
        onSuccess={handleLoginSuccess}
        onSwitchToRegister={handleOpenRegister}
      />

      <RegisterDialog
        open={registerOpen}
        onOpenChange={setRegisterOpen}
        onSuccess={handleRegisterSuccess}
        onSwitchToLogin={handleOpenLogin}
      />
    </div>
  )
}
