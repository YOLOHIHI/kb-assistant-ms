'use client'

import { useState, useRef } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Field, FieldGroup, FieldLabel, FieldError, FieldDescription } from '@/components/ui/field'
import { Avatar, AvatarImage, AvatarFallback } from '@/components/ui/avatar'
import { Spinner } from '@/components/ui/spinner'
import { Upload, User, UserPlus, ArrowRight, Camera } from 'lucide-react'
import { register } from '@/lib/auth-api'
import { validateAndConvertAvatar } from '@/lib/file-utils'

interface RegisterDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess: () => void
  onSwitchToLogin: () => void
}

// 验证规则
const USERNAME_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._-]{2,31}$/
const PASSWORD_MIN_LENGTH = 6
const PASSWORD_MAX_LENGTH = 72
const DISPLAY_NAME_MAX_LENGTH = 64

export default function RegisterDialog({
  open,
  onOpenChange,
  onSuccess,
  onSwitchToLogin,
}: RegisterDialogProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [avatarDataUrl, setAvatarDataUrl] = useState('')
  const [inviteCode, setInviteCode] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const validateForm = (): string | null => {
    if (!username.trim()) {
      return '请输入用户名'
    }
    if (!USERNAME_PATTERN.test(username.trim())) {
      return '用户名必须以字母或数字开头，长度为 3-32 位，只能包含字母、数字、下划线、点和连字符'
    }
    if (!password) {
      return '请输入密码'
    }
    if (password.length < PASSWORD_MIN_LENGTH || password.length > PASSWORD_MAX_LENGTH) {
      return `密码长度必须在 ${PASSWORD_MIN_LENGTH} 到 ${PASSWORD_MAX_LENGTH} 位之间`
    }
    if (!displayName.trim()) {
      return '请输入显示名称'
    }
    if (displayName.trim().length > DISPLAY_NAME_MAX_LENGTH) {
      return `显示名称最长 ${DISPLAY_NAME_MAX_LENGTH} 个字符`
    }
    return null
  }

  const handleAvatarClick = () => {
    fileInputRef.current?.click()
  }

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return

    const result = await validateAndConvertAvatar(file)
    if (!result.valid) {
      setError(result.error || '头像上传失败')
      return
    }

    setAvatarDataUrl(result.dataUrl || '')
    setError('')
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    const validationError = validateForm()
    if (validationError) {
      setError(validationError)
      return
    }

    setLoading(true)
    const result = await register({
      username: username.trim(),
      password,
      displayName: displayName.trim(),
      avatarDataUrl: avatarDataUrl || undefined,
      inviteCode: inviteCode.trim() || undefined,
    })
    setLoading(false)

    if (result.error) {
      setError(result.error)
      return
    }

    if (result.success) {
      resetForm()
      onSuccess()
    }
  }

  const resetForm = () => {
    setUsername('')
    setPassword('')
    setDisplayName('')
    setAvatarDataUrl('')
    setInviteCode('')
    setError('')
  }

  const handleSwitchToLogin = () => {
    resetForm()
    onSwitchToLogin()
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md overflow-hidden p-0 max-h-[90vh] overflow-y-auto">
        {/* Header with gradient */}
        <div className="relative bg-gradient-to-br from-foreground to-foreground/90 px-6 py-8 text-center">
          <div className="absolute inset-0 opacity-30">
            <div className="absolute left-1/4 top-0 h-32 w-32 rounded-full bg-accent/30 blur-3xl" />
            <div className="absolute right-1/4 bottom-0 h-24 w-24 rounded-full bg-accent/20 blur-2xl" />
          </div>
          <div className="relative">
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-accent shadow-lg shadow-accent/30">
              <UserPlus className="h-7 w-7 text-accent-foreground" />
            </div>
            <DialogHeader className="space-y-2">
              <DialogTitle className="text-2xl font-bold text-background">创建账号</DialogTitle>
              <DialogDescription className="text-background/70">
                提交后需等待管理员审批
              </DialogDescription>
            </DialogHeader>
          </div>
        </div>

        {/* Form */}
        <div className="p-6">
          <form onSubmit={handleSubmit}>
            <FieldGroup>
              {/* 头像上传 */}
              <Field className="items-center">
                <FieldLabel>头像（可选）</FieldLabel>
                <div className="flex items-center gap-4">
                  <div className="relative group">
                    <Avatar 
                      className="h-20 w-20 cursor-pointer ring-4 ring-muted transition-all group-hover:ring-accent/30" 
                      onClick={handleAvatarClick}
                    >
                      {avatarDataUrl ? (
                        <AvatarImage src={avatarDataUrl} alt="头像预览" />
                      ) : (
                        <AvatarFallback className="bg-muted">
                          <User className="h-10 w-10 text-muted-foreground" />
                        </AvatarFallback>
                      )}
                    </Avatar>
                    <div 
                      className="absolute inset-0 flex items-center justify-center rounded-full bg-foreground/60 opacity-0 transition-opacity group-hover:opacity-100 cursor-pointer"
                      onClick={handleAvatarClick}
                    >
                      <Camera className="h-6 w-6 text-background" />
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleAvatarClick}
                    className="gap-2 rounded-xl"
                  >
                    <Upload className="h-4 w-4" />
                    上传头像
                  </Button>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/png,image/jpeg,image/jpg,image/webp,image/gif"
                    onChange={handleAvatarChange}
                    className="hidden"
                  />
                </div>
              </Field>

              <Field>
                <FieldLabel htmlFor="register-username">用户名</FieldLabel>
                <Input
                  id="register-username"
                  type="text"
                  placeholder="请输入用户名"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  autoComplete="username"
                  className="h-12 rounded-xl"
                />
                <FieldDescription>
                  以字母或数字开头，3-32 位
                </FieldDescription>
              </Field>

              <Field>
                <FieldLabel htmlFor="register-password">密码</FieldLabel>
                <Input
                  id="register-password"
                  type="password"
                  placeholder="请输入密码"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="new-password"
                  className="h-12 rounded-xl"
                />
                <FieldDescription>密码长度 6-72 位</FieldDescription>
              </Field>

              <Field>
                <FieldLabel htmlFor="register-displayName">显示名称</FieldLabel>
                <Input
                  id="register-displayName"
                  type="text"
                  placeholder="请输入显示名称"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="h-12 rounded-xl"
                />
              </Field>

              <Field>
                <FieldLabel htmlFor="register-inviteCode">邀请码（可选）</FieldLabel>
                <Input
                  id="register-inviteCode"
                  type="text"
                  placeholder="如有邀请码请输入"
                  value={inviteCode}
                  onChange={(e) => setInviteCode(e.target.value)}
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
                    提交注册
                    <ArrowRight className="h-4 w-4" />
                  </>
                )}
              </Button>
            </FieldGroup>
          </form>

          <div className="mt-6 text-center text-sm text-muted-foreground">
            已有账号？{' '}
            <button
              type="button"
              onClick={handleSwitchToLogin}
              className="font-medium text-accent hover:text-accent/80 transition-colors"
            >
              立即登录
            </button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
