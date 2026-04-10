"use client"
import { useState } from "react"
import { UserCircle2, HelpCircle, LogOut, ChevronRight, Database } from "lucide-react"
import { Popover, PopoverContent, PopoverTrigger } from "./ui/popover"
import { displayNameForUser, initials } from "../lib/kb-api"
import { appCopy, formatUserRole } from "../lib/copy"

export default function SettingsPopover({ children, user, onOpenProfile, onOpenKnowledgeBase, onOpenSupport, onLogout }) {
  const [open, setOpen] = useState(false)
  const name = displayNameForUser(user)
  const username = user?.username || appCopy.guestLabel

  function handle(action) {
    setOpen(false)
    action?.()
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent className="w-72 p-0" align="start" side="top">
        <div className="p-3">
          <div className="mb-3 text-sm text-zinc-500 dark:text-zinc-400">{username}</div>

          <div className="mb-3 flex items-center gap-3 rounded-lg bg-zinc-50 p-3 dark:bg-zinc-800/50">
            {user?.avatarDataUrl ? (
              <img src={user.avatarDataUrl} alt={name} className="h-8 w-8 rounded-md object-cover" />
            ) : (
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-zinc-200 text-xs font-bold dark:bg-zinc-700">
                {initials(name)}
              </div>
            )}
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-medium">{name}</div>
              <div className="truncate text-xs text-zinc-500 dark:text-zinc-400">{formatUserRole(user?.role)}</div>
            </div>
          </div>

          <div className="space-y-0.5">
            <button onClick={() => handle(onOpenProfile)} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-800">
              <UserCircle2 className="h-4 w-4 text-zinc-500" />
              <span>个人资料</span>
            </button>

            <button onClick={() => handle(onOpenKnowledgeBase)} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-800">
              <Database className="h-4 w-4 text-zinc-500" />
              <span>知识库管理</span>
            </button>

            <button onClick={() => handle(onOpenSupport)} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-800">
              <HelpCircle className="h-4 w-4 text-zinc-500" />
              <span>联系管理员</span>
              <ChevronRight className="ml-auto h-4 w-4 text-zinc-400" />
            </button>
          </div>

          <div className="my-2 border-t border-zinc-200 dark:border-zinc-700" />

          <button onClick={() => handle(onLogout)} className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-red-600 transition-colors hover:bg-zinc-100 dark:text-red-400 dark:hover:bg-zinc-800">
            <LogOut className="h-4 w-4" />
            <span>退出登录</span>
          </button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
