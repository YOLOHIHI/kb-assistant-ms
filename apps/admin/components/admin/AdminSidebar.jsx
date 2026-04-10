"use client"

import React, { useRef, useEffect } from "react"
import { motion } from "framer-motion"
import {
  Users,
  Monitor,
  Book,
  Building2,
  MessageSquare,
  PanelLeftClose,
  PanelLeftOpen,
  Shield,
} from "lucide-react"

const NAV_ITEMS = [
  { id: "users", label: "用户管理", icon: Users },
  { id: "models", label: "模型管理", icon: Monitor },
  { id: "kb", label: "知识库", icon: Book },
  { id: "tenants", label: "租户管理", icon: Building2 },
  { id: "messages", label: "用户留言", icon: MessageSquare },
]

export default function AdminSidebar({
  collapsed,
  setCollapsed,
  width,
  setWidth,
  activeSection,
  setActiveSection,
  allowedSections = [],
  pendingUsersCount = 0,
}) {
  const resizerRef = useRef(null)
  const isDragging = useRef(false)
  const navItems = NAV_ITEMS.filter((item) => allowedSections.includes(item.id))

  useEffect(() => {
    const handleMouseMove = (e) => {
      if (!isDragging.current) return
      const newWidth = Math.min(360, Math.max(180, e.clientX))
      setWidth(newWidth)
    }

    const handleMouseUp = () => {
      isDragging.current = false
      document.body.style.cursor = ""
      document.body.style.userSelect = ""
    }

    window.addEventListener("mousemove", handleMouseMove)
    window.addEventListener("mouseup", handleMouseUp)

    return () => {
      window.removeEventListener("mousemove", handleMouseMove)
      window.removeEventListener("mouseup", handleMouseUp)
    }
  }, [setWidth])

  const handleResizerMouseDown = () => {
    isDragging.current = true
    document.body.style.cursor = "col-resize"
    document.body.style.userSelect = "none"
  }

  if (collapsed) {
    return (
      <motion.aside
        initial={{ width: width }}
        animate={{ width: 64 }}
        transition={{ type: "spring", stiffness: 300, damping: 30 }}
        className="relative z-20 flex h-full flex-col border-r border-zinc-200/60 bg-white/80 backdrop-blur-sm dark:border-zinc-800 dark:bg-zinc-900/80"
      >
        <div className="flex items-center justify-center border-b border-zinc-200/60 p-3 dark:border-zinc-800">
          <motion.button
            onClick={() => setCollapsed(false)}
            className="rounded-xl p-2 transition-colors hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            <PanelLeftOpen className="h-5 w-5" />
          </motion.button>
        </div>

        <nav className="flex flex-1 flex-col items-center gap-2 py-4">
          {navItems.map((item) => {
            const Icon = item.icon
            const isActive = activeSection === item.id
            return (
              <motion.button
                key={item.id}
                onClick={() => setActiveSection(item.id)}
                className={`relative rounded-xl p-2.5 transition-all ${
                  isActive
                    ? "bg-zinc-900 text-white dark:bg-white dark:text-zinc-900"
                    : "text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                }`}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                title={item.label}
              >
                <Icon className="h-5 w-5" />
                {item.id === "users" && pendingUsersCount > 0 && (
                  <span className="absolute -right-1 -top-1 flex h-4 w-4 items-center justify-center rounded-full bg-orange-500 text-[10px] font-bold text-white">
                    {pendingUsersCount}
                  </span>
                )}
              </motion.button>
            )
          })}
        </nav>
      </motion.aside>
    )
  }

  return (
    <motion.aside
      initial={{ width: 64 }}
      animate={{ width }}
      transition={{ type: "spring", stiffness: 300, damping: 30 }}
      className="relative z-20 flex h-full flex-col border-r border-zinc-200/60 bg-white/80 backdrop-blur-sm dark:border-zinc-800 dark:bg-zinc-900/80"
    >
      {/* Header */}
      <div className="flex items-center gap-3 border-b border-zinc-200/60 px-4 py-3 dark:border-zinc-800">
        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-600 text-white shadow-lg shadow-blue-500/20">
          <Shield className="h-5 w-5" />
        </div>
        <div className="min-w-0 flex-1">
          <h1 className="truncate text-sm font-semibold">管理后台</h1>
          <p className="truncate text-xs text-zinc-500 dark:text-zinc-400">系统运营中心</p>
        </div>
        <motion.button
          onClick={() => setCollapsed(true)}
          className="rounded-xl p-2 transition-colors hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800"
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
        >
          <PanelLeftClose className="h-5 w-5" />
        </motion.button>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto p-3">
        <div className="space-y-1">
          {navItems.map((item, index) => {
            const Icon = item.icon
            const isActive = activeSection === item.id
            return (
              <motion.button
                key={item.id}
                onClick={() => setActiveSection(item.id)}
                initial={{ opacity: 0, x: -20 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ delay: index * 0.05 }}
                className={`group relative flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm font-medium transition-all ${
                  isActive
                    ? "bg-zinc-900 text-white shadow-lg shadow-zinc-900/10 dark:bg-white dark:text-zinc-900 dark:shadow-white/10"
                    : "text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                }`}
              >
                <Icon className="h-5 w-5 shrink-0" />
                <span className="truncate">{item.label}</span>
                {item.id === "users" && pendingUsersCount > 0 && (
                  <motion.span
                    initial={{ scale: 0 }}
                    animate={{ scale: 1 }}
                    className={`ml-auto flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 text-xs font-bold ${
                      isActive
                        ? "bg-orange-500 text-white"
                        : "bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400"
                    }`}
                  >
                    {pendingUsersCount}
                  </motion.span>
                )}
                {isActive && (
                  <motion.div
                    layoutId="activeIndicator"
                    className="absolute inset-0 -z-10 rounded-xl bg-zinc-900 dark:bg-white"
                    transition={{ type: "spring", stiffness: 300, damping: 30 }}
                  />
                )}
              </motion.button>
            )
          })}
        </div>
      </nav>

      {/* Resizer */}
      <div
        ref={resizerRef}
        onMouseDown={handleResizerMouseDown}
        className="absolute right-0 top-0 z-30 h-full w-1 cursor-col-resize opacity-0 transition-opacity hover:opacity-100"
      >
        <div className="h-full w-full bg-blue-500" />
      </div>
    </motion.aside>
  )
}
