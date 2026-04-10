"use client"

import React, { useCallback, useEffect, useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { Plus, Copy, RefreshCw, ToggleLeft, ToggleRight, X } from "lucide-react"
import {
  copyToClipboard,
  createTenant,
  formatDateTime,
  isUnauthorizedError,
  listTenants,
  rotateTenantCode,
  updateTenant,
} from "@/lib/admin-api"

export default function TenantsSection({ enabled, onUnauthorized }) {
  const [tenants, setTenants] = useState([])
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [newTenant, setNewTenant] = useState({ name: "", slug: "" })
  const [error, setError] = useState("")
  const [statusMessage, setStatusMessage] = useState("")
  const [loading, setLoading] = useState(false)

  const loadTenantList = useCallback(async () => {
    if (!enabled) return
    setLoading(true)
    setError("")

    try {
      const data = await listTenants()
      setTenants(Array.isArray(data?.tenants) ? data.tenants : [])
    } catch (loadError) {
      if (isUnauthorizedError(loadError)) {
        onUnauthorized?.(loadError.message)
        return
      }
      setError(loadError?.message || "租户列表加载失败")
    } finally {
      setLoading(false)
    }
  }, [enabled, onUnauthorized])

  useEffect(() => {
    if (!enabled) {
      setTenants([])
      setError("")
      setStatusMessage("")
      return
    }
    loadTenantList()
  }, [enabled, loadTenantList])

  const handleCreate = async (event) => {
    event.preventDefault()
    setError("")

    if (!newTenant.name || !newTenant.slug) {
      setError("请填写完整信息")
      return
    }

    if (!/^[a-z0-9-]+$/.test(newTenant.slug)) {
      setError("Slug 只能包含小写字母、数字和连字符")
      return
    }

    try {
      await createTenant({
        name: newTenant.name.trim(),
        slug: newTenant.slug.trim().toLowerCase(),
      })
      setNewTenant({ name: "", slug: "" })
      setShowCreateForm(false)
      setStatusMessage("租户已创建")
      await loadTenantList()
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "创建租户失败")
    }
  }

  const handleCopyCode = async (code) => {
    const copied = await copyToClipboard(code)
    if (copied) {
      setStatusMessage("邀请码已复制到剪贴板")
      return
    }
    setError("复制邀请码失败，请手动复制")
  }

  const handleRotateCode = async (id) => {
    if (!confirm("确定要刷新邀请码吗？旧邀请码将失效。")) return

    try {
      const result = await rotateTenantCode(id)
      setStatusMessage(`新邀请码：${result?.inviteCode || ""}`)
      await loadTenantList()
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "刷新邀请码失败")
    }
  }

  const handleToggle = async (tenant) => {
    try {
      await updateTenant(tenant.id, { enabled: !tenant.enabled })
      setStatusMessage(`租户已${tenant.enabled ? "停用" : "启用"}`)
      await loadTenantList()
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "更新租户状态失败")
    }
  }

  return (
    <div className="mx-auto max-w-5xl">
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-6 flex items-center justify-between"
      >
        <div>
          <h1 className="text-2xl font-bold tracking-tight">租户管理</h1>
          <p className="mt-1 text-zinc-500 dark:text-zinc-400">管理多租户配置和邀请码</p>
          {statusMessage && <p className="mt-2 text-sm text-emerald-500">{statusMessage}</p>}
          {error && <p className="mt-2 text-sm text-red-500">{error}</p>}
        </div>
        <motion.button
          onClick={() => setShowCreateForm((prev) => !prev)}
          className="inline-flex items-center gap-2 rounded-xl bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-100"
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
        >
          <Plus className="h-4 w-4" />
          创建租户
        </motion.button>
      </motion.div>

      <AnimatePresence>
        {showCreateForm && (
          <motion.div
            initial={{ opacity: 0, height: 0, marginBottom: 0 }}
            animate={{ opacity: 1, height: "auto", marginBottom: 24 }}
            exit={{ opacity: 0, height: 0, marginBottom: 0 }}
            className="overflow-hidden rounded-2xl border border-zinc-200/60 bg-white dark:border-zinc-800 dark:bg-zinc-900"
          >
            <div className="flex items-center justify-between border-b border-zinc-200/60 px-6 py-4 dark:border-zinc-800">
              <h2 className="font-semibold">创建新租户</h2>
              <button
                onClick={() => setShowCreateForm(false)}
                className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <form onSubmit={handleCreate} className="p-6">
              <div className="grid gap-4 md:grid-cols-2">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">租户名称</label>
                  <input
                    type="text"
                    value={newTenant.name}
                    onChange={(event) =>
                      setNewTenant((prev) => ({ ...prev, name: event.target.value }))
                    }
                    placeholder="如 Acme Corp"
                    className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-2.5 text-sm outline-none transition-all focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">Slug</label>
                  <input
                    type="text"
                    value={newTenant.slug}
                    onChange={(event) =>
                      setNewTenant((prev) => ({
                        ...prev,
                        slug: event.target.value.toLowerCase(),
                      }))
                    }
                    placeholder="如 acme (小写字母/数字/连字符)"
                    className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-2.5 text-sm outline-none transition-all focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
                  />
                </div>
              </div>

              <div className="mt-6 flex justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowCreateForm(false)}
                  className="rounded-xl px-4 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600"
                >
                  创建
                </button>
              </div>
            </form>
          </motion.div>
        )}
      </AnimatePresence>

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="overflow-hidden rounded-2xl border border-zinc-200/60 bg-white dark:border-zinc-800 dark:bg-zinc-900"
      >
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-zinc-200/60 bg-zinc-50/50 dark:border-zinc-800 dark:bg-zinc-800/50">
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">名称</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">Slug</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">邀请码</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">状态</th>
                <th className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">创建时间</th>
                <th className="px-6 py-3 text-right text-xs font-semibold uppercase tracking-wider text-zinc-500">操作</th>
              </tr>
            </thead>
            <tbody>
              <AnimatePresence>
                {tenants.map((tenant, index) => (
                  <motion.tr
                    key={tenant.id}
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    exit={{ opacity: 0, x: 20 }}
                    transition={{ delay: index * 0.03 }}
                    className="border-b border-zinc-100 transition-colors last:border-0 hover:bg-zinc-50/50 dark:border-zinc-800/50 dark:hover:bg-zinc-800/30"
                  >
                    <td className="px-6 py-4 font-medium">{tenant.name}</td>
                    <td className="px-6 py-4">
                      <code className="rounded bg-zinc-100 px-2 py-1 font-mono text-sm dark:bg-zinc-800">
                        {tenant.slug}
                      </code>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2">
                        <code className="rounded bg-zinc-100 px-2 py-1 font-mono text-sm dark:bg-zinc-800">
                          {tenant.inviteCode}
                        </code>
                        <motion.button
                          onClick={() => handleCopyCode(tenant.inviteCode)}
                          className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
                          whileHover={{ scale: 1.1 }}
                          whileTap={{ scale: 0.9 }}
                          title="复制邀请码"
                        >
                          <Copy className="h-4 w-4" />
                        </motion.button>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <span
                        className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${
                          tenant.enabled
                            ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
                            : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800"
                        }`}
                      >
                        {tenant.enabled ? "启用" : "停用"}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-zinc-500">
                      {formatDateTime(tenant.createdAt)}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center justify-end gap-2">
                        <motion.button
                          onClick={() => handleRotateCode(tenant.id)}
                          className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
                          whileHover={{ scale: 1.1 }}
                          whileTap={{ scale: 0.9 }}
                          title="刷新邀请码"
                        >
                          <RefreshCw className="h-4 w-4" />
                        </motion.button>
                        <motion.button
                          onClick={() => handleToggle(tenant)}
                          className={`rounded-lg p-1.5 ${
                            tenant.enabled
                              ? "text-green-500 hover:bg-green-50 dark:hover:bg-green-900/20"
                              : "text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                          }`}
                          whileHover={{ scale: 1.1 }}
                          whileTap={{ scale: 0.9 }}
                          title={tenant.enabled ? "停用" : "启用"}
                        >
                          {tenant.enabled ? (
                            <ToggleRight className="h-5 w-5" />
                          ) : (
                            <ToggleLeft className="h-5 w-5" />
                          )}
                        </motion.button>
                      </div>
                    </td>
                  </motion.tr>
                ))}
              </AnimatePresence>
            </tbody>
          </table>
        </div>

        {!loading && tenants.length === 0 && (
          <div className="py-12 text-center text-zinc-500">暂无租户</div>
        )}
      </motion.div>
    </div>
  )
}
