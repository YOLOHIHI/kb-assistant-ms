'use client'

import { Button } from '@/components/ui/button'
import type { UserRole } from '@kb/shared/auth'
import { ArrowRight, Mail, Sparkles } from 'lucide-react'

interface CtaSectionProps {
  isLoggedIn: boolean
  userRole?: UserRole | ''
  onEnterSystem: () => void
  onLogin: () => void
  onContactAdmin: () => void
}

export default function CtaSection({
  isLoggedIn,
  userRole,
  onEnterSystem,
  onLogin,
  onContactAdmin,
}: CtaSectionProps) {
  const handleMainAction = () => {
    if (isLoggedIn) {
      onEnterSystem()
    } else {
      onLogin()
    }
  }

  return (
    <section id="cta" className="relative py-24 sm:py-36">
      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="relative overflow-hidden rounded-[2.5rem] bg-gradient-to-br from-foreground via-foreground/95 to-foreground/90 px-8 py-20 text-center sm:px-16 sm:py-28">
          {/* Animated background elements */}
          <div className="absolute inset-0 -z-10 overflow-hidden">
            <div className="absolute -left-32 -top-32 h-96 w-96 rounded-full bg-accent/20 blur-[100px] animate-float" />
            <div className="absolute -right-32 -bottom-32 h-96 w-96 rounded-full bg-accent/15 blur-[100px] animate-float animation-delay-300" />
            <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 h-[600px] w-[600px] rounded-full bg-accent/10 blur-[150px]" />
          </div>

          {/* Grid pattern overlay */}
          <div className="absolute inset-0 -z-10 opacity-[0.03]" style={{
            backgroundImage: `linear-gradient(hsl(var(--background)) 1px, transparent 1px), linear-gradient(90deg, hsl(var(--background)) 1px, transparent 1px)`,
            backgroundSize: '60px 60px'
          }} />

          {/* Floating icon */}
          <div className="mb-8 inline-flex items-center justify-center">
            <div className="relative">
              <div className="absolute inset-0 rounded-2xl bg-accent blur-xl opacity-50 animate-pulse" />
              <div className="relative flex h-16 w-16 items-center justify-center rounded-2xl bg-accent">
                <Sparkles className="h-8 w-8 text-accent-foreground" />
              </div>
            </div>
          </div>

          <h2 className="mx-auto max-w-2xl text-balance text-3xl font-bold tracking-tight text-background sm:text-5xl">
            准备好开始了吗？
          </h2>
          <p className="mx-auto mt-6 max-w-xl text-pretty text-lg text-background/70 leading-relaxed">
            立即注册体验智能知识助手，让 AI 为您的工作效率带来质的飞跃
          </p>

          <div className="mt-12 flex flex-col items-center justify-center gap-4 sm:flex-row">
            <Button
              size="lg"
              className="gap-2 h-14 px-10 rounded-full bg-accent text-accent-foreground hover:bg-accent/90 shadow-2xl shadow-accent/30 transition-all hover:scale-105 text-base font-medium"
              onClick={handleMainAction}
            >
              {isLoggedIn ? '进入系统' : '立即体验'}
              <ArrowRight className="h-5 w-5" />
            </Button>
            <Button
              size="lg"
              variant="outline"
              className="gap-2 h-14 px-10 rounded-full border-background/20 bg-background/5 text-background hover:bg-background/10 hover:text-background backdrop-blur-sm transition-all text-base font-medium"
              onClick={onContactAdmin}
            >
              <Mail className="h-5 w-5" />
              {userRole === 'ADMIN' || userRole === 'TENANT_ADMIN' ? '管理后台' : '联系管理员'}
            </Button>
          </div>

          {/* Trust badges */}
          <div className="mt-16 flex flex-wrap items-center justify-center gap-8">
            {['企业级安全', '24/7 支持', '99.9% 可用性'].map((badge) => (
              <div key={badge} className="flex items-center gap-2 text-sm text-background/50">
                <div className="h-1.5 w-1.5 rounded-full bg-accent" />
                <span>{badge}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
