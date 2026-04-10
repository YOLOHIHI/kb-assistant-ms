"use client"

import React from "react"
import { motion } from "framer-motion"
import { Sun, Moon, User, LogOut, Menu } from "lucide-react"
import { getUserRoleLabel } from "@/lib/admin-api"

function getAvatarText(name) {
  if (!name) return "AD"
  const chars = name.slice(0, 2)
  return chars.toUpperCase()
}

export default function AdminTopbar({
  collapsed,
  setCollapsed,
  theme,
  setTheme,
  currentUser,
  onProfileClick,
  onLogout,
}) {
  return (
    <motion.header
      initial={{ opacity: 0, y: -10 }}
      animate={{ opacity: 1, y: 0 }}
      className="sticky top-0 z-30 flex items-center gap-4 border-b border-zinc-200/60 bg-white/80 px-6 py-3 backdrop-blur-sm dark:border-zinc-800 dark:bg-zinc-900/80"
    >
      {/* Mobile menu button */}
      {collapsed && (
        <motion.button
          onClick={() => setCollapsed(false)}
          className="rounded-xl p-2 transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-800 md:hidden"
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
        >
          <Menu className="h-5 w-5" />
        </motion.button>
      )}

      {/* Title area */}
      <div className="flex items-center gap-3">
        <h2 className="text-lg font-semibold tracking-tight">管理后台</h2>
        <div className="hidden items-center gap-2 md:flex">
          {["用户审批", "模型治理", "知识库维护"].map((tag, i) => (
            <motion.span
              key={tag}
              initial={{ opacity: 0, scale: 0.8 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: i * 0.1 }}
              className="rounded-full bg-zinc-100 px-2.5 py-1 text-xs font-medium text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400"
            >
              {tag}
            </motion.span>
          ))}
        </div>
      </div>

      {/* Right actions */}
      <div className="ml-auto flex items-center gap-3">
        {/* Theme toggle */}
        <motion.button
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          className="flex items-center gap-2 rounded-full border border-zinc-200 bg-white px-3 py-1.5 text-sm transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:bg-zinc-800 dark:hover:bg-zinc-700"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          {theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          <span className="hidden sm:inline">{theme === "dark" ? "切换日间" : "切换夜间"}</span>
        </motion.button>

        {/* User area */}
        {currentUser && (
          <div className="flex items-center gap-3">
            <div className="hidden text-right md:block">
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                {getUserRoleLabel(currentUser.effectiveRole || currentUser.role)}
              </p>
              <p className="text-sm font-medium">{currentUser.displayName || currentUser.username}</p>
            </div>

            {/* Avatar */}
            <motion.button
              onClick={onProfileClick}
              className="relative overflow-hidden rounded-full"
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
            >
              {currentUser.avatarDataUrl ? (
                <img
                  src={currentUser.avatarDataUrl}
                  alt="Avatar"
                  className="h-9 w-9 rounded-full object-cover ring-2 ring-white dark:ring-zinc-800"
                />
              ) : (
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 text-xs font-bold text-white ring-2 ring-white dark:ring-zinc-800">
                  {getAvatarText(currentUser.displayName || currentUser.username)}
                </div>
              )}
            </motion.button>

            {/* Profile button */}
            <motion.button
              onClick={onProfileClick}
              className="hidden rounded-xl p-2 transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-800 md:block"
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              title="个人资料"
            >
              <User className="h-5 w-5" />
            </motion.button>

            {/* Logout button */}
            <motion.button
              onClick={onLogout}
              className="rounded-xl p-2 text-zinc-500 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20"
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              title="退出登录"
            >
              <LogOut className="h-5 w-5" />
            </motion.button>
          </div>
        )}
      </div>
    </motion.header>
  )
}
