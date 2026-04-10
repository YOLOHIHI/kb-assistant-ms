"use client"

import { useRef, useState } from "react"
import { X, Database, Plus, Trash2, Upload, FileText, ChevronRight, ChevronDown, Search, Loader2 } from "lucide-react"
import { bytesToSize, formatDateTime } from "../lib/kb-api"

export default function KnowledgeBaseModal({
  isOpen,
  onClose,
  kbs = [],
  onCreateKb,
  onDeleteKb,
  onLoadDocuments,
  onUploadDocument,
  onDeleteDocument,
}) {
  const [expandedId, setExpandedId] = useState(null)
  const [search, setSearch] = useState("")
  const [newKbName, setNewKbName] = useState("")
  const [showNewKb, setShowNewKb] = useState(false)
  const [creating, setCreating] = useState(false)
  const [uploadingId, setUploadingId] = useState("")
  const fileInputRef = useRef(null)

  if (!isOpen) return null

  const filtered = (kbs || []).filter((kb) => String(kb.name || "").toLowerCase().includes(search.toLowerCase()))

  async function handleCreateKb() {
    if (!newKbName.trim() || creating) return
    setCreating(true)
    try {
      await onCreateKb?.(newKbName.trim())
      setNewKbName("")
      setShowNewKb(false)
    } finally {
      setCreating(false)
    }
  }

  async function handleDeleteKb(id) {
    if (!confirm("确定要删除这个知识库吗？")) return
    await onDeleteKb?.(id)
    if (expandedId === id) setExpandedId(null)
  }

  async function handleToggleKb(kb) {
    const next = expandedId === kb.id ? null : kb.id
    setExpandedId(next)
    if (next && !kb.documentsLoaded) {
      await onLoadDocuments?.(kb.id)
    }
  }

  async function handleUpload(kbId, event) {
    const file = event.target.files?.[0]
    if (!file) return
    setUploadingId(kbId)
    try {
      await onUploadDocument?.(kbId, file)
      await onLoadDocuments?.(kbId)
    } finally {
      setUploadingId("")
      event.target.value = ""
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-sm" onClick={onClose} />

      <div className="relative flex max-h-[80vh] w-full max-w-2xl flex-col rounded-2xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-700 dark:bg-zinc-900">
        <div className="flex items-center gap-3 border-b border-zinc-200 px-5 py-4 dark:border-zinc-700">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-50 dark:bg-blue-900/30">
            <Database className="h-4 w-4 text-blue-600 dark:text-blue-400" />
          </div>
          <div>
            <h2 className="text-base font-semibold">知识库管理</h2>
            <p className="text-xs text-zinc-500 dark:text-zinc-400">{kbs.length} 个知识库</p>
          </div>
          <button onClick={onClose} className="ml-auto rounded-lg p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800">
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="flex items-center gap-2 border-b border-zinc-100 px-4 py-3 dark:border-zinc-800">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-zinc-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索知识库…"
              className="w-full rounded-full border border-zinc-200 bg-zinc-50 py-1.5 pl-8 pr-3 text-sm outline-none placeholder:text-zinc-400 focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-zinc-700 dark:bg-zinc-800/50 dark:text-zinc-100"
            />
          </div>
          <button
            onClick={() => setShowNewKb((value) => !value)}
            className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-zinc-900 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-zinc-700 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-200"
          >
            <Plus className="h-3.5 w-3.5" />
            新建知识库
          </button>
        </div>

        {showNewKb && (
          <div className="flex items-center gap-2 border-b border-zinc-100 bg-zinc-50/60 px-4 py-3 dark:border-zinc-800 dark:bg-zinc-800/30">
            <input
              type="text"
              value={newKbName}
              onChange={(e) => setNewKbName(e.target.value)}
              placeholder="例如：产品文档"
              className="flex-1 rounded-lg border border-zinc-300 px-3 py-2 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 dark:border-zinc-700 dark:bg-zinc-900"
            />
            <button onClick={handleCreateKb} disabled={!newKbName.trim() || creating} className="rounded-lg bg-blue-500 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-blue-600 disabled:opacity-40">
              {creating ? "创建中..." : "创建"}
            </button>
            <button onClick={() => { setShowNewKb(false); setNewKbName("") }} className="rounded-lg px-2 py-1.5 text-xs text-zinc-500 transition-colors hover:bg-zinc-200 dark:hover:bg-zinc-700">
              取消
            </button>
          </div>
        )}

        <div className="flex-1 divide-y divide-zinc-100 overflow-y-auto dark:divide-zinc-800">
          {filtered.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-center text-zinc-400">
              <Database className="mb-2 h-8 w-8 opacity-30" />
              <p className="text-sm">暂无知识库</p>
              <p className="mt-1 text-xs">点击「新建知识库」创建第一个</p>
            </div>
          ) : null}

          {filtered.map((kb) => {
            const expanded = expandedId === kb.id
            const docs = Array.isArray(kb.docs) ? kb.docs : []
            const canManage = !!kb.owned && !kb.isSystem
            const typeLabel = kb.isSystem ? "系统" : kb.isPublic ? "公开" : kb.owned ? "私有" : "共享"
            const docCount = kb.actualDocumentCount ?? kb.documentCount ?? kb.docCount ?? docs.length
            const sizeText = kb.sizeDisplay || bytesToSize(kb.sizeBytes ?? kb.size ?? 0)
            return (
              <div key={kb.id}>
                <div className="group flex items-center gap-3 px-4 py-3 transition-colors hover:bg-zinc-50 dark:hover:bg-zinc-800/50">
                  <button onClick={() => handleToggleKb(kb)} className="flex min-w-0 flex-1 items-center gap-3 text-left">
                    <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-50 dark:bg-blue-900/20">
                      <Database className="h-4 w-4 text-blue-500" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-sm font-medium">{kb.name}</div>
                      <div className="text-xs text-zinc-400">{docCount} 个文档 · {typeLabel} · {sizeText} · 更新于 {formatDateTime(kb.updatedAt || kb.createdAt || "")}</div>
                    </div>
                    {expanded ? <ChevronDown className="h-4 w-4 shrink-0 text-zinc-400" /> : <ChevronRight className="h-4 w-4 shrink-0 text-zinc-400" />}
                  </button>
                  {canManage ? (
                    <button onClick={() => handleDeleteKb(kb.id)} className="rounded-lg p-1.5 text-zinc-400 opacity-0 transition-all hover:bg-red-50 hover:text-red-500 group-hover:opacity-100 dark:hover:bg-red-900/20" title="删除知识库">
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  ) : null}
                </div>

                {expanded ? (
                  <div className="bg-zinc-50/60 px-4 pb-3 dark:bg-zinc-800/30">
                    {canManage ? (
                      <label className="mb-2 mt-1 inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-dashed border-zinc-300 px-3 py-1.5 text-xs text-zinc-500 transition-colors hover:border-blue-400 hover:text-blue-500 dark:border-zinc-600">
                        {uploadingId === kb.id ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Upload className="h-3.5 w-3.5" />}
                        上传文档
                        <input ref={fileInputRef} type="file" className="hidden" onChange={(event) => handleUpload(kb.id, event)} />
                      </label>
                    ) : null}
                    {docs.length === 0 ? <p className="py-1 text-xs text-zinc-400">暂无文档</p> : null}
                    <div className="space-y-1">
                      {docs.map((doc) => (
                        <div key={doc.id} className="group/doc flex items-center gap-2 rounded-lg px-2 py-1.5 transition-colors hover:bg-zinc-100 dark:hover:bg-zinc-700/50">
                          <FileText className="h-3.5 w-3.5 shrink-0 text-zinc-400" />
                          <span className="flex-1 truncate text-xs text-zinc-600 dark:text-zinc-300">{doc.name}</span>
                          <span className="shrink-0 text-[11px] text-zinc-400">{doc.size}</span>
                          {canManage ? (
                            <button onClick={() => onDeleteDocument?.(kb.id, doc.id)} className="rounded p-0.5 text-zinc-400 opacity-0 transition-all hover:text-red-500 group-hover/doc:opacity-100" title="删除文档">
                              <X className="h-3 w-3" />
                            </button>
                          ) : null}
                        </div>
                      ))}
                    </div>
                  </div>
                ) : null}
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
