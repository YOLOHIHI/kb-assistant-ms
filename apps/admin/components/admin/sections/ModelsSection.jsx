"use client"

import React, { useCallback, useEffect, useMemo, useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import {
  Plus,
  RefreshCw,
  Trash2,
  Eye,
  Search,
  ToggleLeft,
  ToggleRight,
  Server,
} from "lucide-react"
import {
  createProvider,
  deleteProvider,
  getModelTags,
  isUnauthorizedError,
  listAdminModels,
  listProviders,
  syncProviderModels,
  updateAdminModel,
} from "@/lib/admin-api"

function getProviderColor(name) {
  const colors = {
    OpenRouter: "from-purple-500 to-pink-500",
    OpenAI: "from-emerald-500 to-teal-500",
    Anthropic: "from-orange-500 to-amber-500",
  }
  return colors[name] || "from-blue-500 to-indigo-500"
}

function getTagLabel(tag) {
  const labels = {
    reasoning: "推理",
    vision: "视觉",
    online: "联网",
    free: "免费",
    embed: "嵌入",
    rerank: "重排",
    tool: "工具",
  }
  return labels[tag] || tag
}

export default function ModelsSection({ enabled, onUnauthorized }) {
  const [providers, setProviders] = useState([])
  const [models, setModels] = useState([])
  const [selectedProvider, setSelectedProvider] = useState("")
  const [keyword, setKeyword] = useState("")
  const [onlyEnabled, setOnlyEnabled] = useState(true)
  const [showAddForm, setShowAddForm] = useState(false)
  const [newProvider, setNewProvider] = useState({ name: "", baseUrl: "", apiKey: "" })
  const [loading, setLoading] = useState(false)
  const [statusMessage, setStatusMessage] = useState("")
  const [error, setError] = useState("")
  const [syncingProviderId, setSyncingProviderId] = useState("")

  useEffect(() => {
    if (typeof window === "undefined") return
    setSelectedProvider(localStorage.getItem("kb_admin_provider") || "")
  }, [])

  useEffect(() => {
    if (typeof window === "undefined") return
    localStorage.setItem("kb_admin_provider", selectedProvider)
  }, [selectedProvider])

  const loadProviders = useCallback(async () => {
    if (!enabled) return
    try {
      const data = await listProviders()
      const nextProviders = Array.isArray(data?.providers) ? data.providers : []
      setProviders(nextProviders)

      if (selectedProvider && !nextProviders.some((provider) => provider.id === selectedProvider)) {
        setSelectedProvider("")
      }
    } catch (loadError) {
      if (isUnauthorizedError(loadError)) {
        onUnauthorized?.(loadError.message)
        return
      }
      setError(loadError?.message || "供应商加载失败")
    }
  }, [enabled, onUnauthorized, selectedProvider])

  const loadModels = useCallback(
    async (providerId = selectedProvider) => {
      if (!enabled) return
      setLoading(true)
      setError("")
      try {
        const data = await listAdminModels(providerId || undefined)
        setModels(Array.isArray(data?.models) ? data.models : [])
      } catch (loadError) {
        if (isUnauthorizedError(loadError)) {
          onUnauthorized?.(loadError.message)
          return
        }
        setError(loadError?.message || "模型列表加载失败")
      } finally {
        setLoading(false)
      }
    },
    [enabled, onUnauthorized, selectedProvider]
  )

  useEffect(() => {
    if (!enabled) {
      setProviders([])
      setModels([])
      setError("")
      setStatusMessage("")
      return
    }

    loadProviders()
  }, [enabled, loadProviders])

  useEffect(() => {
    if (!enabled) return
    loadModels(selectedProvider)
  }, [enabled, loadModels, selectedProvider])

  const filteredModels = useMemo(() => {
    let result = models
    if (selectedProvider) {
      result = result.filter((model) => model.providerId === selectedProvider)
    }
    if (keyword.trim()) {
      const query = keyword.toLowerCase()
      result = result.filter(
        (model) =>
          String(model.modelId || "").toLowerCase().includes(query) ||
          String(model.displayName || "").toLowerCase().includes(query)
      )
    }
    if (onlyEnabled) {
      result = result.filter((model) => model.enabled)
    }
    return result
  }, [keyword, models, onlyEnabled, selectedProvider])

  const handleRefresh = async () => {
    setStatusMessage("")
    await Promise.all([loadProviders(), loadModels(selectedProvider)])
  }

  const handleAddProvider = async (event) => {
    event.preventDefault()
    if (!newProvider.name || !newProvider.baseUrl || !newProvider.apiKey) {
      setError("请填写供应商名称、API 地址和 API 密钥")
      return
    }

    try {
      await createProvider({
        name: newProvider.name.trim(),
        baseUrl: newProvider.baseUrl.trim(),
        apiKey: newProvider.apiKey.trim(),
        enabled: true,
      })
      setNewProvider({ name: "", baseUrl: "", apiKey: "" })
      setShowAddForm(false)
      setStatusMessage("供应商已添加")
      await loadProviders()
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "添加供应商失败")
    }
  }

  const handleDeleteProvider = async (id) => {
    if (!confirm("确定要删除此供应商吗？相关模型也将被移除。")) return

    try {
      await deleteProvider(id)
      if (selectedProvider === id) {
        setSelectedProvider("")
      }
      setStatusMessage("供应商已删除")
      await Promise.all([loadProviders(), loadModels(selectedProvider === id ? "" : selectedProvider)])
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "删除供应商失败")
    }
  }

  const handleToggleModel = async (id, nextEnabled) => {
    try {
      await updateAdminModel(id, { enabled: nextEnabled })
      setModels((prev) =>
        prev.map((model) => (model.id === id ? { ...model, enabled: nextEnabled } : model))
      )
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "更新模型状态失败")
    }
  }

  const handleViewProviderModels = async (provider) => {
    setSyncingProviderId(provider.id)
    setStatusMessage("")
    setError("")

    try {
      const result = await syncProviderModels(provider.id)
      setSelectedProvider(provider.id)
      setStatusMessage(
        result?.fallback
          ? result.notice || "远端模型加载失败，已回退到本地已同步模型"
          : `模型同步完成，新增 ${Number(result?.created || 0)} 个，更新 ${Number(result?.updated || 0)} 个`
      )
      await loadModels(provider.id)
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "同步模型失败")
    } finally {
      setSyncingProviderId("")
    }
  }

  return (
    <div className="mx-auto max-w-6xl">
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-6"
      >
        <h1 className="text-2xl font-bold tracking-tight">模型管理</h1>
        <p className="mt-1 text-zinc-500 dark:text-zinc-400">管理 AI 供应商和模型配置</p>
        {statusMessage && <p className="mt-2 text-sm text-emerald-500">{statusMessage}</p>}
        {error && <p className="mt-2 text-sm text-red-500">{error}</p>}
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="mb-8"
      >
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">供应商</h2>
          <motion.button
            onClick={() => setShowAddForm((prev) => !prev)}
            className="inline-flex items-center gap-2 rounded-xl bg-zinc-900 px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-100"
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
          >
            <Plus className="h-4 w-4" />
            添加供应商
          </motion.button>
        </div>

        <AnimatePresence>
          {showAddForm && (
            <motion.form
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              exit={{ opacity: 0, height: 0 }}
              onSubmit={handleAddProvider}
              className="mb-4 overflow-hidden rounded-2xl border border-zinc-200/60 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
            >
              <div className="grid gap-4 md:grid-cols-3">
                <div>
                  <label className="mb-1.5 block text-sm font-medium">供应商名称</label>
                  <input
                    type="text"
                    value={newProvider.name}
                    onChange={(event) =>
                      setNewProvider((prev) => ({ ...prev, name: event.target.value }))
                    }
                    placeholder="如 OpenRouter"
                    className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-2 text-sm outline-none transition-all focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">API 地址</label>
                  <input
                    type="url"
                    value={newProvider.baseUrl}
                    onChange={(event) =>
                      setNewProvider((prev) => ({ ...prev, baseUrl: event.target.value }))
                    }
                    placeholder="如 https://openrouter.ai/api/v1"
                    className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-2 text-sm outline-none transition-all focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
                  />
                </div>
                <div>
                  <label className="mb-1.5 block text-sm font-medium">API 密钥</label>
                  <input
                    type="password"
                    value={newProvider.apiKey}
                    onChange={(event) =>
                      setNewProvider((prev) => ({ ...prev, apiKey: event.target.value }))
                    }
                    placeholder="sk-..."
                    className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-2 text-sm outline-none transition-all focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
                  />
                </div>
              </div>
              <div className="mt-4 flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowAddForm(false)}
                  className="rounded-xl px-4 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="rounded-xl bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600"
                >
                  添加
                </button>
              </div>
            </motion.form>
          )}
        </AnimatePresence>

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {providers.map((provider, index) => (
            <motion.div
              key={provider.id}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.05 }}
              className="group relative overflow-hidden rounded-2xl border border-zinc-200/60 bg-white p-4 transition-shadow hover:shadow-lg dark:border-zinc-800 dark:bg-zinc-900"
            >
              <div className="flex items-start gap-3">
                <div
                  className={`flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br ${getProviderColor(provider.name)} text-white shadow-lg`}
                >
                  <Server className="h-5 w-5" />
                </div>
                <div className="min-w-0 flex-1">
                  <h3 className="font-semibold">{provider.name}</h3>
                  <p className="truncate text-xs text-zinc-500 dark:text-zinc-400">
                    {provider.baseUrl}
                  </p>
                  <p className="mt-1 font-mono text-xs text-zinc-400">{provider.apiKeyMasked}</p>
                </div>
                <span
                  className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${
                    provider.enabled
                      ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
                      : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400"
                  }`}
                >
                  {provider.enabled ? "已启用" : "停用"}
                </span>
              </div>
              <div className="mt-4 flex gap-2">
                <button
                  onClick={() => handleViewProviderModels(provider)}
                  className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                >
                  <Eye className={`h-3.5 w-3.5 ${syncingProviderId === provider.id ? "animate-pulse" : ""}`} />
                  {syncingProviderId === provider.id ? "同步中..." : "查看模型"}
                </button>
                <button
                  onClick={() => handleDeleteProvider(provider.id)}
                  className="flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                  删除
                </button>
              </div>
            </motion.div>
          ))}
        </div>
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.2 }}
      >
        <h2 className="mb-4 text-lg font-semibold">已启用模型</h2>

        <div className="mb-4 flex flex-wrap items-center gap-4">
          <select
            value={selectedProvider}
            onChange={(event) => setSelectedProvider(event.target.value)}
            className="rounded-xl border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
          >
            <option value="">全部供应商</option>
            {providers.map((provider) => (
              <option key={provider.id} value={provider.id}>
                {provider.name}
              </option>
            ))}
          </select>

          <div className="relative flex-1 md:max-w-xs">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
            <input
              type="text"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索模型..."
              className="w-full rounded-xl border border-zinc-200 bg-white py-2 pl-9 pr-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
            />
          </div>

          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={onlyEnabled}
              onChange={(event) => setOnlyEnabled(event.target.checked)}
              className="rounded border-zinc-300 text-blue-500 focus:ring-blue-500"
            />
            仅已启用
          </label>

          <button
            onClick={handleRefresh}
            className="ml-auto inline-flex items-center gap-2 rounded-xl bg-zinc-100 px-3 py-2 text-sm font-medium text-zinc-700 hover:bg-zinc-200 dark:bg-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-700"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
            刷新
          </button>

          <span className="text-sm text-zinc-500">
            显示 {filteredModels.length} / {models.length}
          </span>
        </div>

        <div className="overflow-hidden rounded-2xl border border-zinc-200/60 bg-white dark:border-zinc-800 dark:bg-zinc-900">
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead>
                <tr className="border-b border-zinc-200/60 bg-zinc-50/50 dark:border-zinc-800 dark:bg-zinc-800/50">
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                    显示名称
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                    模型 ID
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                    供应商
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                    标签
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                    状态
                  </th>
                  <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wider text-zinc-500">
                    操作
                  </th>
                </tr>
              </thead>
              <tbody>
                <AnimatePresence>
                  {filteredModels.map((model, index) => {
                    const tags = getModelTags(model)
                    return (
                      <motion.tr
                        key={model.id}
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ delay: index * 0.02 }}
                        className="border-b border-zinc-100 transition-colors last:border-0 hover:bg-zinc-50/50 dark:border-zinc-800/50 dark:hover:bg-zinc-800/30"
                      >
                        <td className="px-4 py-3 font-medium">{model.displayName}</td>
                        <td className="px-4 py-3 font-mono text-sm text-zinc-500">{model.modelId}</td>
                        <td className="px-4 py-3">{model.providerName}</td>
                        <td className="px-4 py-3">
                          <div className="flex flex-wrap gap-1">
                            {tags.map((tag) => (
                              <span
                                key={`${model.id}_${tag}`}
                                className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs dark:bg-zinc-800"
                              >
                                {getTagLabel(tag)}
                              </span>
                            ))}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${
                              model.enabled
                                ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
                                : "bg-zinc-100 text-zinc-500 dark:bg-zinc-800"
                            }`}
                          >
                            {model.enabled ? "已启用" : "未启用"}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end">
                            <motion.button
                              onClick={() => handleToggleModel(model.id, !model.enabled)}
                              className={`rounded-lg p-1.5 ${
                                model.enabled
                                  ? "text-green-500 hover:bg-green-50 dark:hover:bg-green-900/20"
                                  : "text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                              }`}
                              whileHover={{ scale: 1.1 }}
                              whileTap={{ scale: 0.9 }}
                            >
                              {model.enabled ? (
                                <ToggleRight className="h-5 w-5" />
                              ) : (
                                <ToggleLeft className="h-5 w-5" />
                              )}
                            </motion.button>
                          </div>
                        </td>
                      </motion.tr>
                    )
                  })}
                </AnimatePresence>
              </tbody>
            </table>
          </div>

          {!loading && filteredModels.length === 0 && (
            <div className="py-12 text-center text-zinc-500">暂无数据</div>
          )}
        </div>
      </motion.div>
    </div>
  )
}
