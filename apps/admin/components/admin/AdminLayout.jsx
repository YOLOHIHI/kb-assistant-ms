"use client"

import React, { useEffect, useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import AdminSidebar from "./AdminSidebar"
import AdminTopbar from "./AdminTopbar"
import LoginModal from "./LoginModal"
import ProfileModal from "./ProfileModal"

export default function AdminLayout({
  children,
  activeSection,
  setActiveSection,
  allowedSections,
  authReady,
  currentUser,
  pendingUsersCount,
  showLoginModal,
  loginHint,
  onLogin,
  onCloseLogin,
  showProfileModal,
  onOpenProfile,
  onCloseProfile,
  onSaveProfile,
  onLogout,
}) {
  const [theme, setTheme] = useState(() => {
    if (typeof window !== "undefined") {
      const saved = localStorage.getItem("kb_theme")
      if (saved) return saved
      if (window.matchMedia?.("(prefers-color-scheme: dark)").matches) return "dark"
    }
    return "light"
  })

  const [sidebarCollapsed, setSidebarCollapsed] = useState(() => {
    if (typeof window !== "undefined") {
      return localStorage.getItem("kb_admin_sidebar_collapsed") === "1"
    }
    return false
  })

  const [sidebarWidth, setSidebarWidth] = useState(() => {
    if (typeof window !== "undefined") {
      const saved = parseInt(localStorage.getItem("kb_admin_sidebar_width") || "240")
      return Math.min(360, Math.max(180, saved))
    }
    return 240
  })

  useEffect(() => {
    if (theme === "dark") {
      document.documentElement.classList.add("dark")
    } else {
      document.documentElement.classList.remove("dark")
    }
    document.documentElement.setAttribute("data-theme", theme)
    document.documentElement.style.colorScheme = theme
    localStorage.setItem("kb_theme", theme)
  }, [theme])

  // Sidebar persistence
  useEffect(() => {
    localStorage.setItem("kb_admin_sidebar_collapsed", sidebarCollapsed ? "1" : "0")
  }, [sidebarCollapsed])

  useEffect(() => {
    localStorage.setItem("kb_admin_sidebar_width", String(sidebarWidth))
  }, [sidebarWidth])

  return (
    <div className="h-screen w-full overflow-hidden bg-zinc-50 text-zinc-900 dark:bg-zinc-950 dark:text-zinc-100">
      {/* Background decoration */}
      <div className="pointer-events-none fixed inset-0 overflow-hidden">
        <div className="absolute -left-40 -top-40 h-80 w-80 rounded-full bg-gradient-to-br from-blue-500/10 to-indigo-500/10 blur-3xl dark:from-blue-500/5 dark:to-indigo-500/5" />
        <div className="absolute -bottom-40 -right-40 h-80 w-80 rounded-full bg-gradient-to-br from-purple-500/10 to-pink-500/10 blur-3xl dark:from-purple-500/5 dark:to-pink-500/5" />
      </div>

      <div className="relative flex h-full">
        <AdminSidebar
          collapsed={sidebarCollapsed}
            setCollapsed={setSidebarCollapsed}
            width={sidebarWidth}
            setWidth={setSidebarWidth}
            activeSection={activeSection}
            setActiveSection={setActiveSection}
            allowedSections={allowedSections}
            pendingUsersCount={pendingUsersCount}
        />

        <div className="flex min-w-0 flex-1 flex-col">
          <AdminTopbar
            collapsed={sidebarCollapsed}
            setCollapsed={setSidebarCollapsed}
            theme={theme}
            setTheme={setTheme}
            currentUser={currentUser}
            onProfileClick={onOpenProfile}
            onLogout={onLogout}
          />

          <motion.main
            className="flex-1 overflow-y-auto p-6"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
          >
            <AnimatePresence mode="wait">
              <motion.div
                key={activeSection}
                initial={{ opacity: 0, x: 20 }}
                animate={{ opacity: 1, x: 0 }}
                exit={{ opacity: 0, x: -20 }}
                transition={{ duration: 0.2 }}
              >
                {authReady ? (
                  children
                ) : (
                  <div className="flex min-h-[50vh] items-center justify-center">
                    <div className="h-10 w-10 animate-spin rounded-full border-4 border-zinc-300 border-t-zinc-900 dark:border-zinc-700 dark:border-t-white" />
                  </div>
                )}
              </motion.div>
            </AnimatePresence>
          </motion.main>
        </div>
      </div>

      <LoginModal
        isOpen={showLoginModal}
        onClose={onCloseLogin}
        onLogin={onLogin}
        initialError={loginHint}
      />

      <ProfileModal
        isOpen={showProfileModal}
        onClose={onCloseProfile}
        user={currentUser}
        onSave={onSaveProfile}
      />
    </div>
  )
}
