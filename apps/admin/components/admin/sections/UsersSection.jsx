"use client"

import React, { useCallback, useEffect, useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { RefreshCw, Check, X, User } from "lucide-react"
import {
  approveScopedUser,
  disableAdminUser,
  formatDateTime,
  getUserRoleLabel,
  isUnauthorizedError,
  isSuperAdminUser,
  listScopedUsers,
  grantTenantAdminRole,
  rejectScopedUser,
  restoreAdminUser,
  revokeTenantAdminRole,
} from "@/lib/admin-api"
import { mergeUsersByPreviousOrder, upsertUserRow } from "@/lib/user-list-state"

const STATUS_CONFIG = {
  PENDING: { label: "待审批", color: "bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400" },
  ACTIVE: { label: "已启用", color: "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400" },
  REJECTED: { label: "已驳回", color: "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400" },
  DISABLED: { label: "已禁用", color: "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400" },
}

const FILTER_BUTTONS = [
  { value: "", label: "全部" },
  { value: "PENDING", label: "待审批" },
  { value: "ACTIVE", label: "已启用" },
  { value: "DISABLED", label: "已禁用" },
]

export default function UsersSection({ enabled, currentUser, onUnauthorized, onPendingUsersCountChange }) {
  const [users, setUsers] = useState([])
  const [filter, setFilter] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    if (typeof window === "undefined") return
    setFilter(localStorage.getItem("kb_admin_user_filter") || "")
  }, [])

  useEffect(() => {
    if (typeof window === "undefined") return
    localStorage.setItem("kb_admin_user_filter", filter)
  }, [filter])

  const loadUsers = useCallback(
    async (nextFilter = filter) => {
      if (!enabled) return
      setLoading(true)
      setError("")

      try {
        const shouldLoadPendingSeparately = nextFilter && nextFilter !== "PENDING"
        const [usersData, pendingData] = await Promise.all([
          listScopedUsers(currentUser, nextFilter || undefined),
          shouldLoadPendingSeparately ? listScopedUsers(currentUser, "PENDING") : Promise.resolve(null),
        ])

        const nextUsers = Array.isArray(usersData?.users) ? usersData.users : []
        setUsers((previousUsers) => mergeUsersByPreviousOrder(previousUsers, nextUsers))

        const pendingCount =
          nextFilter === "PENDING"
            ? nextUsers.length
            : shouldLoadPendingSeparately
              ? Array.isArray(pendingData?.users)
                ? pendingData.users.length
                : 0
              : nextUsers.filter((user) => user?.status === "PENDING").length
        onPendingUsersCountChange?.(pendingCount)
      } catch (loadError) {
        if (isUnauthorizedError(loadError)) {
          onUnauthorized?.(loadError.message)
          return
        }
        setError(loadError?.message || "用户列表加载失败")
      } finally {
        setLoading(false)
      }
    },
    [currentUser, enabled, filter, onPendingUsersCountChange, onUnauthorized]
  )

  useEffect(() => {
    if (!enabled) {
      setUsers([])
      setError("")
      return
    }
    loadUsers(filter)
  }, [enabled, filter, loadUsers])

  const handleRefresh = () => {
    loadUsers(filter)
  }

  const handleApprove = async (id) => {
    try {
      await approveScopedUser(currentUser, id)
      await loadUsers(filter)
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "审批失败")
    }
  }

  const handleReject = async (id) => {
    if (!confirm("确定要驳回此用户吗？")) return
    try {
      await rejectScopedUser(currentUser, id)
      await loadUsers(filter)
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "驳回失败")
    }
  }

  const handleToggleTenantAdmin = async (user) => {
    try {
      const effectiveRole = String(user?.effectiveRole || user?.role || "").toUpperCase()
      const username = String(user?.username || "").trim() || "该账号"
      const isTenantAdmin = effectiveRole === "TENANT_ADMIN" || user?.isTenantAdmin
      const confirmed = isTenantAdmin
        ? confirm(`确定要撤销账号“${username}”的租户管理员权限吗？`)
        : confirm(`确定要将账号“${username}”设为租户管理员吗？`)
      if (!confirmed) return

      const response = isTenantAdmin
        ? await revokeTenantAdminRole(user.id)
        : await grantTenantAdminRole(user.id)
      if (response?.user?.id) {
        setUsers((previousUsers) => upsertUserRow(previousUsers, response.user))
        return
      }

      await loadUsers(filter)
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "更新租户管理员状态失败")
    }
  }

  const handleDisable = async (id) => {
    if (!confirm("确定要禁用此用户吗？被禁用用户的数据会保留，但无法登录。")) return
    try {
      await disableAdminUser(id)
      await loadUsers(filter)
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "禁用失败")
    }
  }

  const handleRestore = async (id) => {
    try {
      await restoreAdminUser(id)
      await loadUsers(filter)
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "恢复失败")
    }
  }

  return (
    <div className="mx-auto max-w-5xl">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between"
      >
        <div>
          <h1 className="text-2xl font-bold tracking-tight">用户管理</h1>
          <p className="mt-1 text-zinc-500 dark:text-zinc-400">管理系统用户、审批注册和权限控制</p>
          {error && <p className="mt-2 text-sm text-red-500">{error}</p>}
        </div>
        <motion.button
          onClick={handleRefresh}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-xl bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 disabled:opacity-50 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-100"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          刷新
        </motion.button>
      </motion.div>

      {/* Filters */}
      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="mb-6 flex flex-wrap gap-2"
      >
        {FILTER_BUTTONS.map((btn) => (
          <motion.button
            key={btn.value}
            onClick={() => setFilter(btn.value)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-all ${
              filter === btn.value
                ? "bg-zinc-900 text-white dark:bg-white dark:text-zinc-900"
                : "bg-zinc-100 text-zinc-600 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-400 dark:hover:bg-zinc-700"
            }`}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            {btn.label}
          </motion.button>
        ))}
      </motion.div>

      {/* Table */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
        className="overflow-hidden rounded-2xl border border-zinc-200/60 bg-white dark:border-zinc-800 dark:bg-zinc-900"
      >
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-zinc-200/60 bg-zinc-50/50 dark:border-zinc-800 dark:bg-zinc-800/50">
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                  账号
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                  昵称
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                  角色
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                  状态
                </th>
                <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                  创建时间
                </th>
                <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-zinc-500 dark:text-zinc-400">
                  操作
                </th>
              </tr>
            </thead>
            <tbody>
              <AnimatePresence>
                {users.map((user, index) => {
                  const effectiveRole = String(user?.effectiveRole || user?.role || "").toUpperCase()
                  const baseRole = String(user?.role || "").toUpperCase()
                  const isCurrentUser = String(user?.id || "") === String(currentUser?.id || "")
                  const canToggleTenantAdmin =
                    isSuperAdminUser(currentUser) &&
                    baseRole !== "ADMIN" &&
                    Boolean(user?.tenantId)
                  const canChangeUserStatus =
                    isSuperAdminUser(currentUser) &&
                    baseRole !== "ADMIN" &&
                    !isCurrentUser
                  const canDisable = canChangeUserStatus && user?.status === "ACTIVE"
                  const canRestore = canChangeUserStatus && user?.status === "DISABLED"
                  const showActions = user?.status === "PENDING" || canToggleTenantAdmin || canDisable || canRestore

                  return (
                  <motion.tr
                    key={user.id}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 20 }}
                    transition={{ delay: index * 0.03 }}
                    className="border-b border-zinc-100 transition-colors last:border-0 hover:bg-zinc-50/50 dark:border-zinc-800/50 dark:hover:bg-zinc-800/30"
                  >
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-3">
                        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 text-xs font-bold text-white">
                          {user.displayName?.slice(0, 1) || user.username.slice(0, 1).toUpperCase()}
                        </div>
                        <span className="font-medium">{user.username}</span>
                      </div>
                    </td>
                    <td className="px-4 py-3 text-zinc-600 dark:text-zinc-400">{user.displayName || "-"}</td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1 ${effectiveRole === "ADMIN" ? "text-purple-600 dark:text-purple-400" : ""}`}>
                        {effectiveRole === "ADMIN" && <User className="h-3.5 w-3.5" />}
                        {getUserRoleLabel(effectiveRole)}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CONFIG[user.status]?.color}`}>
                        {STATUS_CONFIG[user.status]?.label}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-zinc-500 dark:text-zinc-400">
                      {formatDateTime(user.createdAt)}
                    </td>
                    <td className="px-4 py-3">
                      {showActions && (
                        <div className="flex items-center justify-end gap-2">
                          {canToggleTenantAdmin && (
                            <motion.button
                              onClick={() => handleToggleTenantAdmin(user)}
                              className={`rounded-lg px-2.5 py-1.5 text-xs font-medium transition-colors ${
                                effectiveRole === "TENANT_ADMIN"
                                  ? "bg-zinc-100 text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-200 dark:hover:bg-zinc-700"
                                  : "bg-blue-100 text-blue-700 hover:bg-blue-200 dark:bg-blue-900/30 dark:text-blue-300 dark:hover:bg-blue-900/50"
                              }`}
                              whileHover={{ scale: 1.03 }}
                              whileTap={{ scale: 0.97 }}
                              title={effectiveRole === "TENANT_ADMIN" ? "撤销租户管理员" : "设为租户管理员"}
                            >
                              {effectiveRole === "TENANT_ADMIN" ? "撤销租户管理员" : "设为租户管理员"}
                            </motion.button>
                          )}
                          {canDisable && (
                            <motion.button
                              onClick={() => handleDisable(user.id)}
                              className="rounded-lg bg-red-100 px-2.5 py-1.5 text-xs font-medium text-red-700 transition-colors hover:bg-red-200 dark:bg-red-900/30 dark:text-red-300 dark:hover:bg-red-900/50"
                              whileHover={{ scale: 1.03 }}
                              whileTap={{ scale: 0.97 }}
                              title="禁用用户"
                            >
                              禁用
                            </motion.button>
                          )}
                          {canRestore && (
                            <motion.button
                              onClick={() => handleRestore(user.id)}
                              className="rounded-lg bg-green-100 px-2.5 py-1.5 text-xs font-medium text-green-700 transition-colors hover:bg-green-200 dark:bg-green-900/30 dark:text-green-300 dark:hover:bg-green-900/50"
                              whileHover={{ scale: 1.03 }}
                              whileTap={{ scale: 0.97 }}
                              title="恢复用户"
                            >
                              恢复
                            </motion.button>
                          )}
                          {user.status === "PENDING" && (
                            <>
                              <motion.button
                                onClick={() => handleApprove(user.id)}
                                className="rounded-lg bg-green-100 p-1.5 text-green-600 transition-colors hover:bg-green-200 dark:bg-green-900/30 dark:text-green-400 dark:hover:bg-green-900/50"
                                whileHover={{ scale: 1.1 }}
                                whileTap={{ scale: 0.9 }}
                                title="通过"
                              >
                                <Check className="h-4 w-4" />
                              </motion.button>
                              <motion.button
                                onClick={() => handleReject(user.id)}
                                className="rounded-lg bg-red-100 p-1.5 text-red-600 transition-colors hover:bg-red-200 dark:bg-red-900/30 dark:text-red-400 dark:hover:bg-red-900/50"
                                whileHover={{ scale: 1.1 }}
                                whileTap={{ scale: 0.9 }}
                                title="驳回"
                              >
                                <X className="h-4 w-4" />
                              </motion.button>
                            </>
                          )}
                        </div>
                      )}
                    </td>
                  </motion.tr>
                  )
                })}
              </AnimatePresence>
            </tbody>
          </table>
        </div>

        {!loading && users.length === 0 && (
          <div className="py-12 text-center text-zinc-500 dark:text-zinc-400">
            暂无数据
          </div>
        )}
      </motion.div>
    </div>
  )
}
