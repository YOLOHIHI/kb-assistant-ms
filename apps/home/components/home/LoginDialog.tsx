'use client'

import { useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Field, FieldGroup, FieldLabel, FieldError } from '@/components/ui/field'
import { Spinner } from '@/components/ui/spinner'
import { Sparkles, ArrowRight } from 'lucide-react'
import { login, type User } from '@/lib/auth-api'

interface LoginDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess: (user: User) => void
  onSwitchToRegister: () => void
}

export default function LoginDialog({
  open,
  onOpenChange,
  onSuccess,
  onSwitchToRegister,
}: LoginDialogProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!username.trim() || !password.trim()) {
      setError('请输入账号和密码')
      return
    }

    setLoading(true)
    const result = await login({ username: username.trim(), password })
    setLoading(false)

    if (result.error) {
      setError(result.error)
      return
    }

    if (result.user) {
      setUsername('')
      setPassword('')
      onSuccess(result.user)
    }
  }

  const handleSwitchToRegister = () => {
    setUsername('')
    setPassword('')
    setError('')
    onSwitchToRegister()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md overflow-hidden p-0">
        {/* Header with gradient */}
        <div className="relative bg-gradient-to-br from-foreground to-foreground/90 px-6 py-8 text-center">
          <div className="absolute inset-0 opacity-30">
            <div className="absolute left-1/4 top-0 h-32 w-32 rounded-full bg-accent/30 blur-3xl" />
            <div className="absolute right-1/4 bottom-0 h-24 w-24 rounded-full bg-accent/20 blur-2xl" />
          </div>
          <div className="relative">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-accent shadow-lg shadow-accent/30">
              <Sparkles className="h-7 w-7 text-accent-foreground" />
            </div>
            <DialogHeader className="space-y-2">
              <DialogTitle className="text-2xl font-bold text-background">欢迎回来</DialogTitle>
              <DialogDescription className="text-background/70">
                输入您的账号和密码登录系统
              </DialogDescription>
            </DialogHeader>
          </div>
        </div>

        {/* Form */}
        <div className="p-6">
          <form onSubmit={handleSubmit}>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="login-username">用户名</FieldLabel>
                <Input
                  id="login-username"
                  type="text"
                  placeholder="请输入用户名"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  className="h-12 rounded-xl"
                />
              </Field>

              <Field>
                <FieldLabel htmlFor="login-password">密码</FieldLabel>
                <Input
                  id="login-password"
                  type="password"
                  placeholder="请输入密码"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  className="h-12 rounded-xl"
                />
              </Field>

              {error && (
                <FieldError className="animate-fade-in rounded-xl bg-destructive/10 p-3">
                  {error}
                </FieldError>
              )}

              <Button 
                type="submit" 
                className="w-full h-12 rounded-xl bg-accent text-accent-foreground hover:bg-accent/90 shadow-lg shadow-accent/20 gap-2 text-base font-medium mt-2" 
                disabled={loading}
              >
                {loading ? (
                  <Spinner className="mr-2" />
                ) : (
                  <>
                    登录
                    <ArrowRight className="h-4 w-4" />
                  </>
                )}
              </Button>
            </FieldGroup>
          </form>

          <div className="mt-6 text-center text-sm text-muted-foreground">
            还没有账号？{' '}
            <button
              type="button"
              onClick={handleSwitchToRegister}
              className="font-medium text-accent hover:text-accent/80 transition-colors"
            >
              立即注册
            </button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
