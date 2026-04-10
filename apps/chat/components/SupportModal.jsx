"use client"

import { useEffect, useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { X, MessageSquare, Send } from "lucide-react"
import { formatSupportStatus } from "../lib/copy"

export default function SupportModal({ isOpen, onClose, onSend, onLoadHistory, onDeleteMessage }) {
  const [tab, setTab] = useState("send")
  const [subject, setSubject] = useState("")
  const [content, setContent] = useState("")
  const [error, setError] = useState("")
  const [history, setHistory] = useState([])
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [sending, setSending] = useState(false)

  useEffect(() => {
    if (!isOpen) return
    setError("")
    if (tab === "history") void loadHistory()
  }, [isOpen, tab])

  const loadHistory = async () => {
    setLoadingHistory(true)
    try {
      const list = await onLoadHistory?.()
      setHistory(Array.isArray(list) ? list : [])
    } catch (e) {
      setError(e?.message || "加载消息记录失败。")
    } finally {
      setLoadingHistory(false)
    }
  }

  const handleSend = async () => {
    if (!subject.trim() || !content.trim()) {
      setError("请完整填写主题和内容。")
      return
    }
    setSending(true)
    setError("")
    try {
      await onSend?.({ subject: subject.trim(), content: content.trim() })
      setSubject("")
      setContent("")
      setTab("history")
      await loadHistory()
    } catch (e) {
      setError(e?.message || "发送消息失败。")
    } finally {
      setSending(false)
    }
  }

  const handleDelete = async (id) => {
    await onDeleteMessage?.(id)
    await loadHistory()
  }

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-[80] bg-black/60" onClick={onClose} />
          <motion.div initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }} className="fixed left-1/2 top-1/2 z-[90] flex w-full max-w-2xl -translate-x-1/2 -translate-y-1/2 flex-col rounded-2xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex items-center justify-between border-b border-zinc-200 px-5 py-4 dark:border-zinc-800">
              <div>
                <div className="text-xs font-medium uppercase tracking-[0.24em] text-zinc-400">支持中心</div>
                <h2 className="mt-2 text-xl font-semibold">联系管理员</h2>
              </div>
              <button onClick={onClose} className="rounded-lg p-1.5 hover:bg-zinc-100 dark:hover:bg-zinc-800"><X className="h-5 w-5" /></button>
            </div>

            <div className="flex gap-2 border-b border-zinc-100 px-5 py-3 dark:border-zinc-800">
              <button onClick={() => setTab("send")} className={`rounded-full px-4 py-2 text-sm ${tab === "send" ? "bg-zinc-900 text-white dark:bg-white dark:text-zinc-900" : "hover:bg-zinc-100 dark:hover:bg-zinc-800"}`}>发送消息</button>
              <button onClick={() => setTab("history")} className={`rounded-full px-4 py-2 text-sm ${tab === "history" ? "bg-zinc-900 text-white dark:bg-white dark:text-zinc-900" : "hover:bg-zinc-100 dark:hover:bg-zinc-800"}`}>我的消息</button>
            </div>

            <div className="min-h-[420px] p-5">
              {tab === "send" ? (
                <div className="space-y-4">
                  <label className="block space-y-2 text-sm">
                    <span className="font-medium">主题</span>
                    <input value={subject} onChange={(e) => setSubject(e.target.value)} className="w-full rounded-xl border border-zinc-300 px-4 py-3 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800" placeholder="简要描述你的问题" />
                  </label>
                  <label className="block space-y-2 text-sm">
                    <span className="font-medium">内容</span>
                    <textarea value={content} onChange={(e) => setContent(e.target.value)} rows={8} className="w-full rounded-xl border border-zinc-300 px-4 py-3 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800" placeholder="补充问题场景、已发生现象和期望结果" />
                  </label>
                  {error ? <div className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600 dark:bg-red-950/30 dark:text-red-300">{error}</div> : null}
                  <button onClick={handleSend} disabled={sending} className="inline-flex items-center gap-2 rounded-xl bg-zinc-900 px-4 py-3 text-sm font-medium text-white hover:bg-zinc-800 disabled:opacity-50 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-100">
                    <Send className="h-4 w-4" />
                    {sending ? "发送中..." : "发送消息"}
                  </button>
                </div>
              ) : (
                <div className="space-y-3">
                  {loadingHistory ? <div className="text-sm text-zinc-500">加载中...</div> : null}
                  {!loadingHistory && history.length === 0 ? (
                    <div className="flex min-h-[260px] flex-col items-center justify-center text-center text-zinc-400">
                      <MessageSquare className="mb-3 h-10 w-10 opacity-40" />
                      <div className="text-sm">暂时还没有消息</div>
                    </div>
                  ) : null}
                  {history.map((item) => (
                    <article key={item.id} className="rounded-2xl border border-zinc-200 p-4 dark:border-zinc-800">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <div className="font-medium">{item.subject}</div>
                          <div className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">{formatSupportStatus(item.status || "OPEN")}</div>
                        </div>
                        <button onClick={() => handleDelete(item.id)} className="rounded-lg px-2 py-1 text-xs text-red-600 hover:bg-red-50 dark:hover:bg-red-950/20">删除</button>
                      </div>
                      <div className="mt-3 whitespace-pre-wrap text-sm text-zinc-600 dark:text-zinc-300">{item.content}</div>
                      {item.reply ? <div className="mt-4 rounded-xl bg-zinc-50 px-4 py-3 text-sm dark:bg-zinc-800/70"><div className="mb-1 text-xs font-medium uppercase tracking-[0.18em] text-zinc-400">管理员回复</div>{item.reply}</div> : null}
                    </article>
                  ))}
                  {error ? <div className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600 dark:bg-red-950/30 dark:text-red-300">{error}</div> : null}
                </div>
              )}
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
