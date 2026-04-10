"use client"

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import {
  Plus,
  Trash2,
  Upload,
  RefreshCw,
  FileText,
  Database,
  Layers,
} from "lucide-react"
import {
  createScopedKb,
  deleteScopedKb,
  deleteScopedKbDocument,
  filterEmbeddingModels,
  formatDateTime,
  getScopedKbStats,
  isUnauthorizedError,
  listScopedKbDocuments,
  listScopedKbs,
  listEnabledModels,
  normalizeDocumentsResponse,
  normalizeKbStats,
  normalizeKnowledgeBases,
  reindexScopedKb,
  uploadScopedKbDocument,
} from "@/lib/admin-api"

const EMPTY_STATS = {
  documents: 0,
  chunks: 0,
  sessions: 0,
}

function resolveKbId(list, currentId, fallbackId) {
  if (fallbackId && list.some((kb) => kb.id === fallbackId)) return fallbackId
  if (currentId && list.some((kb) => kb.id === currentId)) return currentId
  return list[0]?.id || ""
}

export default function KnowledgeBaseSection({ enabled, currentUser, onUnauthorized }) {
  const [kbs, setKbs] = useState([])
  const [selectedKbId, setSelectedKbId] = useState("default")
  const [documents, setDocuments] = useState([])
  const [stats, setStats] = useState(EMPTY_STATS)
  const [embedModels, setEmbedModels] = useState([])
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [newKb, setNewKb] = useState({ name: "", mode: "local", model: "" })
  const [uploading, setUploading] = useState(false)
  const [reindexing, setReindexing] = useState(false)
  const [loadingDetails, setLoadingDetails] = useState(false)
  const [error, setError] = useState("")
  const [statusMessage, setStatusMessage] = useState("")
  const fileInputRef = useRef(null)

  useEffect(() => {
    if (typeof window === "undefined") return
    setSelectedKbId(localStorage.getItem("kb_admin_kb") || "default")
  }, [])

  useEffect(() => {
    if (typeof window === "undefined") return
    localStorage.setItem("kb_admin_kb", selectedKbId)
  }, [selectedKbId])

  const loadBaseData = useCallback(
    async (preferredKbId) => {
      if (!enabled) return
      setError("")

      try {
        const [modelsData, kbsData] = await Promise.all([
          listEnabledModels(),
          listScopedKbs(currentUser),
        ])
        const nextKbs = normalizeKnowledgeBases(kbsData)
        const nextEmbedModels = filterEmbeddingModels(modelsData?.models || [])

        setKbs(nextKbs)
        setEmbedModels(nextEmbedModels)

        const resolvedKbId = resolveKbId(nextKbs, selectedKbId, preferredKbId)
        if (resolvedKbId !== selectedKbId) {
          setSelectedKbId(resolvedKbId)
        }
        if (!resolvedKbId) {
          setDocuments([])
          setStats(EMPTY_STATS)
        }
      } catch (loadError) {
        if (isUnauthorizedError(loadError)) {
          onUnauthorized?.(loadError.message)
          return
        }
        setError(loadError?.message || "知识库列表加载失败")
      }
    },
    [currentUser, enabled, onUnauthorized, selectedKbId]
  )

  const loadKbDetails = useCallback(
    async (kbId) => {
      if (!enabled || !kbId) return
      setLoadingDetails(true)
      setError("")

      try {
        const [statsData, documentsData] = await Promise.all([
          getScopedKbStats(currentUser, kbId),
          listScopedKbDocuments(currentUser, kbId),
        ])
        setStats(normalizeKbStats(statsData))
        setDocuments(normalizeDocumentsResponse(documentsData))
      } catch (loadError) {
        if (isUnauthorizedError(loadError)) {
          onUnauthorized?.(loadError.message)
          return
        }
        setError(loadError?.message || "知识库详情加载失败")
      } finally {
        setLoadingDetails(false)
      }
    },
    [currentUser, enabled, onUnauthorized]
  )

  useEffect(() => {
    if (!enabled) {
      setKbs([])
      setDocuments([])
      setStats(EMPTY_STATS)
      setEmbedModels([])
      setError("")
      setStatusMessage("")
      return
    }

    loadBaseData()
  }, [enabled, loadBaseData])

  useEffect(() => {
    if (!enabled || !selectedKbId || kbs.length === 0) return
    loadKbDetails(selectedKbId)
  }, [enabled, kbs.length, loadKbDetails, selectedKbId])

  const selectedKb = useMemo(
    () => kbs.find((kb) => kb.id === selectedKbId),
    [kbs, selectedKbId]
  )

  const handleCreateKb = async (event) => {
    event.preventDefault()
    if (!newKb.name.trim()) {
      setError("请输入知识库名称")
      return
    }
    if (newKb.mode === "api" && !newKb.model) {
      setError("云端嵌入模式需要选择一个嵌入模型")
      return
    }

    try {
      const created = await createScopedKb(currentUser, {
        name: newKb.name.trim(),
        embeddingMode: newKb.mode,
        embeddingModel: newKb.mode === "api" ? newKb.model : null,
        embeddingBaseUrl: null,
      })

      setNewKb({ name: "", mode: "local", model: "" })
      setShowCreateForm(false)
      setStatusMessage("知识库已创建")
      await loadBaseData(created?.id || "")
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "创建知识库失败")
    }
  }

  const handleDeleteKb = async () => {
    if (!selectedKb) return
    if (selectedKb.isSystem) {
      setError("系统默认知识库不可删除")
      return
    }
    if (!confirm(`确定要删除知识库“${selectedKb.name}”吗？`)) return

    try {
      await deleteScopedKb(currentUser, selectedKb.id)
      setStatusMessage("知识库已删除")
      await loadBaseData("")
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "删除知识库失败")
    }
  }

  const handleSelectUpload = () => {
    if (!selectedKbId) return
    fileInputRef.current?.click()
  }

  const handleUpload = async (event) => {
    const file = event.target.files?.[0]
    if (!file || !selectedKbId) return

    setUploading(true)
    setStatusMessage("")
    setError("")

    try {
      await uploadScopedKbDocument(currentUser, selectedKbId, file)
      setStatusMessage(`已上传 ${file.name}`)
      await loadKbDetails(selectedKbId)
      await loadBaseData(selectedKbId)
    } catch (uploadError) {
      if (isUnauthorizedError(uploadError)) {
        onUnauthorized?.(uploadError.message)
        return
      }
      setError(uploadError?.message || "上传文档失败")
    } finally {
      setUploading(false)
      event.target.value = ""
    }
  }

  const handleReindex = async () => {
    if (!selectedKbId) return
    setReindexing(true)
    setStatusMessage("")
    setError("")

    try {
      await reindexScopedKb(currentUser, selectedKbId)
      setStatusMessage("索引已重建")
      await loadKbDetails(selectedKbId)
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "重建索引失败")
    } finally {
      setReindexing(false)
    }
  }

  const handleDeleteDocument = async (id) => {
    if (!selectedKbId) return
    if (!confirm("确定要删除此文档吗？")) return

    try {
      await deleteScopedKbDocument(currentUser, selectedKbId, id)
      setStatusMessage("文档已删除")
      await loadKbDetails(selectedKbId)
      await loadBaseData(selectedKbId)
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "删除文档失败")
    }
  }

  return (
    <div className="mx-auto max-w-6xl">
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-6"
      >
        <h1 className="text-2xl font-bold tracking-tight">知识库管理</h1>
        <p className="mt-1 text-zinc-500 dark:text-zinc-400">管理文档索引和知识库配置</p>
        {statusMessage && <p className="mt-2 text-sm text-emerald-500">{statusMessage}</p>}
        {error && <p className="mt-2 text-sm text-red-500">{error}</p>}
      </motion.div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-1">
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="mb-6 grid grid-cols-3 gap-3"
          >
            {[
              { label: "文档", value: stats.documents, icon: FileText },
              { label: "片段", value: stats.chunks, icon: Layers },
              { label: "会话", value: stats.sessions, icon: Database },
            ].map((stat, index) => {
              const Icon = stat.icon
              return (
                <motion.div
                  key={stat.label}
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: index * 0.05 }}
                  className="rounded-xl border border-zinc-200/60 bg-white p-3 text-center dark:border-zinc-800 dark:bg-zinc-900"
                >
                  <Icon className="mx-auto mb-1 h-5 w-5 text-zinc-400" />
                  <p className="text-lg font-bold">{loadingDetails ? "..." : stat.value}</p>
                  <p className="text-xs text-zinc-500">{stat.label}</p>
                </motion.div>
              )
            })}
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="mb-6 rounded-2xl border border-zinc-200/60 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
          >
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-semibold">创建知识库</h2>
              <motion.button
                onClick={() => setShowCreateForm((prev) => !prev)}
                className="rounded-lg p-1.5 text-zinc-500 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                whileHover={{ scale: 1.1 }}
                whileTap={{ scale: 0.9 }}
              >
                <Plus className="h-5 w-5" />
              </motion.button>
            </div>

            <AnimatePresence>
              {showCreateForm && (
                <motion.form
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: "auto" }}
                  exit={{ opacity: 0, height: 0 }}
                  onSubmit={handleCreateKb}
                  className="space-y-3 overflow-hidden"
                >
                  <input
                    type="text"
                    value={newKb.name}
                    onChange={(event) =>
                      setNewKb((prev) => ({ ...prev, name: event.target.value }))
                    }
                    placeholder="知识库名称"
                    className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
                  />
                  <select
                    value={newKb.mode}
                    onChange={(event) =>
                      setNewKb((prev) => ({ ...prev, mode: event.target.value }))
                    }
                    className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 dark:border-zinc-700 dark:bg-zinc-800"
                  >
                    <option value="local">本地嵌入</option>
                    <option value="api">云端 API 嵌入</option>
                  </select>
                  {newKb.mode === "api" && (
                    <select
                      value={newKb.model}
                      onChange={(event) =>
                        setNewKb((prev) => ({ ...prev, model: event.target.value }))
                      }
                      className="w-full rounded-xl border border-zinc-200 bg-white px-3 py-2 text-sm outline-none focus:border-blue-500 dark:border-zinc-700 dark:bg-zinc-800"
                    >
                      <option value="">{embedModels.length ? "选择嵌入模型" : "暂无可用嵌入模型"}</option>
                      {embedModels.map((model) => (
                        <option key={model.id} value={model.id}>
                          {model.displayName} · {model.providerName}
                        </option>
                      ))}
                    </select>
                  )}
                  <button
                    type="submit"
                    className="w-full rounded-xl bg-blue-500 py-2 text-sm font-medium text-white hover:bg-blue-600"
                  >
                    创建
                  </button>
                </motion.form>
              )}
            </AnimatePresence>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.15 }}
            className="rounded-2xl border border-zinc-200/60 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
          >
            <h2 className="mb-3 font-semibold">选择知识库</h2>
            <div className="space-y-2">
              {kbs.map((kb) => (
                <motion.button
                  key={kb.id}
                  onClick={() => setSelectedKbId(kb.id)}
                  className={`w-full rounded-xl px-3 py-2.5 text-left text-sm transition-all ${
                    selectedKbId === kb.id
                      ? "bg-zinc-900 text-white dark:bg-white dark:text-zinc-900"
                      : "hover:bg-zinc-100 dark:hover:bg-zinc-800"
                  }`}
                  whileHover={{ scale: 1.01 }}
                  whileTap={{ scale: 0.99 }}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-medium">{kb.name}</span>
                    {kb.isSystem && (
                      <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs text-blue-600 dark:bg-blue-900/30 dark:text-blue-400">
                        系统
                      </span>
                    )}
                  </div>
                  <p className="mt-0.5 text-xs opacity-60">
                    {kb.embeddingMode === "local" ? "本地嵌入" : `云端 · ${kb.embeddingModel || "未指定"}`}
                  </p>
                </motion.button>
              ))}
            </div>

            {selectedKb && !selectedKb.isSystem && (
              <motion.button
                onClick={handleDeleteKb}
                className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl border border-red-200 py-2 text-sm text-red-500 hover:bg-red-50 dark:border-red-900/50 dark:hover:bg-red-900/20"
                whileHover={{ scale: 1.01 }}
                whileTap={{ scale: 0.99 }}
              >
                <Trash2 className="h-4 w-4" />
                删除当前知识库
              </motion.button>
            )}
          </motion.div>
        </div>

        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="lg:col-span-2"
        >
          <div className="rounded-2xl border border-zinc-200/60 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
            <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
              <h2 className="text-lg font-semibold">文档管理</h2>
              <div className="flex gap-2">
                <motion.button
                  onClick={handleReindex}
                  disabled={reindexing || !selectedKbId}
                  className="inline-flex items-center gap-2 rounded-xl border border-zinc-200 px-3 py-2 text-sm font-medium hover:bg-zinc-50 disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-800"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  <RefreshCw className={`h-4 w-4 ${reindexing ? "animate-spin" : ""}`} />
                  重建索引
                </motion.button>
                <motion.button
                  onClick={handleSelectUpload}
                  disabled={uploading || !selectedKbId}
                  className="inline-flex items-center gap-2 rounded-xl bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800 disabled:opacity-50 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-100"
                  whileHover={{ scale: 1.02 }}
                  whileTap={{ scale: 0.98 }}
                >
                  <Upload className={`h-4 w-4 ${uploading ? "animate-bounce" : ""}`} />
                  上传文档
                </motion.button>
                <input
                  ref={fileInputRef}
                  type="file"
                  className="hidden"
                  onChange={handleUpload}
                />
              </div>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-zinc-200/60 dark:border-zinc-800">
                    <th className="pb-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                      文件名
                    </th>
                    <th className="pb-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                      分类
                    </th>
                    <th className="pb-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                      标签
                    </th>
                    <th className="pb-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                      上传时间
                    </th>
                    <th className="pb-3 text-left text-xs font-semibold uppercase tracking-wider text-zinc-500">
                      大小
                    </th>
                    <th className="pb-3 text-right text-xs font-semibold uppercase tracking-wider text-zinc-500">
                      操作
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <AnimatePresence>
                    {documents.map((doc, index) => (
                      <motion.tr
                        key={doc.id}
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0, x: -20 }}
                        transition={{ delay: index * 0.03 }}
                        className="border-b border-zinc-100 last:border-0 dark:border-zinc-800/50"
                      >
                        <td className="py-3">
                          <div className="flex items-center gap-2">
                            <FileText className="h-4 w-4 text-zinc-400" />
                            <span className="font-medium">{doc.name}</span>
                          </div>
                        </td>
                        <td className="py-3 text-zinc-600 dark:text-zinc-400">
                          {doc.category || "-"}
                        </td>
                        <td className="py-3">
                          <div className="flex flex-wrap gap-1">
                            {(doc.tags || []).map((tag) => (
                              <span
                                key={`${doc.id}_${tag}`}
                                className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs dark:bg-zinc-800"
                              >
                                {tag}
                              </span>
                            ))}
                            {!doc.tags?.length && <span className="text-xs text-zinc-400">-</span>}
                          </div>
                        </td>
                        <td className="py-3 text-sm text-zinc-500">
                          {formatDateTime(doc.uploadedAt)}
                        </td>
                        <td className="py-3 text-sm text-zinc-500">{doc.sizeText}</td>
                        <td className="py-3">
                          <div className="flex justify-end">
                            <motion.button
                              onClick={() => handleDeleteDocument(doc.id)}
                              className="rounded-lg p-1.5 text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20"
                              whileHover={{ scale: 1.1 }}
                              whileTap={{ scale: 0.9 }}
                            >
                              <Trash2 className="h-4 w-4" />
                            </motion.button>
                          </div>
                        </td>
                      </motion.tr>
                    ))}
                  </AnimatePresence>
                </tbody>
              </table>
            </div>

            {!loadingDetails && documents.length === 0 && (
              <div className="py-12 text-center text-zinc-500">暂无文档</div>
            )}
          </div>
        </motion.div>
      </div>
    </div>
  )
}
