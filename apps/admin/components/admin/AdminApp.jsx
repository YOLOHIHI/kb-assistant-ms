"use client"

import React, { useCallback, useEffect, useState } from "react"
import AdminLayout from "./AdminLayout"
import OverviewSection from "./sections/OverviewSection"
import UsersSection from "./sections/UsersSection"
import ModelsSection from "./sections/ModelsSection"
import KnowledgeBaseSection from "./sections/KnowledgeBaseSection"
import TenantsSection from "./sections/TenantsSection"
import MessagesSection from "./sections/MessagesSection"
import {
  getAllowedAdminSections,
  getCurrentUser,
  isUnauthorizedError,
  listScopedUsers,
  loginAdmin,
  logoutAdmin,
  requireAdminUser,
  updateMyProfile,
} from "@/lib/admin-api"

export default function AdminApp() {
  const [activeSection, setActiveSection] = useState(() => {
    if (typeof window !== "undefined") {
      return localStorage.getItem("kb_admin_section") || "overview"
    }
    return "overview"
  })
  const [currentUser, setCurrentUser] = useState(null)
  const [authReady, setAuthReady] = useState(false)
  const [showLoginModal, setShowLoginModal] = useState(false)
  const [loginHint, setLoginHint] = useState("")
  const [showProfileModal, setShowProfileModal] = useState(false)
  const [pendingUsersCount, setPendingUsersCount] = useState(0)

  useEffect(() => {
    localStorage.setItem("kb_admin_section", activeSection)
  }, [activeSection])

  const handleUnauthorized = useCallback((message = "登录已失效，请重新登录") => {
    setCurrentUser(null)
    setPendingUsersCount(0)
    setShowProfileModal(false)
    setLoginHint(message)
    setShowLoginModal(true)
  }, [])

  const loadPendingUsersCount = useCallback(async (user) => {
    if (!user) {
      setPendingUsersCount(0)
      return
    }
    try {
      const data = await listScopedUsers(user, "PENDING")
      setPendingUsersCount(Array.isArray(data?.users) ? data.users.length : 0)
    } catch (error) {
      if (isUnauthorizedError(error)) {
        handleUnauthorized(error.message)
      }
    }
  }, [handleUnauthorized])

  const loadSession = useCallback(async () => {
    setAuthReady(false)
    try {
      const me = await getCurrentUser({ silent401: true })
      if (!me) {
        setCurrentUser(null)
        setPendingUsersCount(0)
        setLoginHint("")
        setShowLoginModal(true)
        return
      }

      requireAdminUser(me)
      setCurrentUser(me)
      setLoginHint("")
      setShowLoginModal(false)
      await loadPendingUsersCount(me)
    } catch (error) {
      setCurrentUser(null)
      setPendingUsersCount(0)
      setLoginHint(error?.message || "请使用管理员或租户管理员账号登录")
      setShowLoginModal(true)
    } finally {
      setAuthReady(true)
    }
  }, [loadPendingUsersCount])

  useEffect(() => {
    loadSession()
  }, [loadSession])

  const handleLogin = useCallback(
    async ({ username, password }) => {
      const user = await loginAdmin({ username, password })
      setCurrentUser(user)
      setLoginHint("")
      setShowLoginModal(false)
      setAuthReady(true)
      await loadPendingUsersCount(user)
      return user
    },
    [loadPendingUsersCount]
  )

  const handleProfileSave = useCallback(async (payload) => {
    const user = await updateMyProfile(payload)
    setCurrentUser(user)
    setShowProfileModal(false)
    return user
  }, [])

  const handleLogout = useCallback(async () => {
    if (!confirm("确定要退出登录吗？")) return
    try {
      await logoutAdmin()
    } finally {
      setCurrentUser(null)
      setPendingUsersCount(0)
      setShowProfileModal(false)
      window.location.href = "/"
    }
  }, [])

  const handleCloseLogin = useCallback(() => {
    if (currentUser) {
      setShowLoginModal(false)
      setLoginHint("")
      return
    }
    window.location.href = "/"
  }, [currentUser])

  useEffect(() => {
    if (!currentUser) return
    const allowedSections = getAllowedAdminSections(currentUser)
    const nextSection = allowedSections.includes(activeSection)
      ? activeSection
      : allowedSections[0] || "overview"
    if (nextSection !== activeSection) {
      setActiveSection(nextSection)
    }
  }, [activeSection, currentUser])

  const allowedSections = currentUser ? getAllowedAdminSections(currentUser) : []
  const renderedSection = allowedSections.includes(activeSection)
    ? activeSection
    : allowedSections[0] || "overview"

  const sectionProps = {
    enabled: Boolean(currentUser),
    currentUser,
    onUnauthorized: handleUnauthorized,
  }

  const renderSection = () => {
    switch (renderedSection) {
      case "users":
        return (
          <UsersSection
            {...sectionProps}
            onPendingUsersCountChange={setPendingUsersCount}
          />
        )
      case "models":
        return <ModelsSection {...sectionProps} />
      case "kb":
        return <KnowledgeBaseSection {...sectionProps} />
      case "tenants":
        return <TenantsSection {...sectionProps} />
      case "messages":
        return <MessagesSection {...sectionProps} />
      default:
        return (
          <OverviewSection
            {...sectionProps}
            pendingUsersCount={pendingUsersCount}
            onNavigate={setActiveSection}
          />
        )
    }
  }

  return (
    <AdminLayout
      activeSection={activeSection}
      setActiveSection={setActiveSection}
      allowedSections={allowedSections}
      authReady={authReady}
      currentUser={currentUser}
      pendingUsersCount={pendingUsersCount}
      showLoginModal={showLoginModal}
      loginHint={loginHint}
      onLogin={handleLogin}
      onCloseLogin={handleCloseLogin}
      showProfileModal={showProfileModal}
      onOpenProfile={() => setShowProfileModal(true)}
      onCloseProfile={() => setShowProfileModal(false)}
      onSaveProfile={handleProfileSave}
      onLogout={handleLogout}
    >
      {currentUser ? renderSection() : null}
    </AdminLayout>
  )
}
