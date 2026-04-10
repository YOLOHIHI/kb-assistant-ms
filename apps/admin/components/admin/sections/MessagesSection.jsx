"use client"

import React, { useCallback, useEffect, useMemo, useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { MessageSquare, Reply, X, Send, Loader2 } from "lucide-react"
import {
  formatDateTime,
  isUnauthorizedError,
  listAdminMessages,
  replyAdminMessage,
  updateAdminMessageStatus,
} from "@/lib/admin-api"

const STATUS_CONFIG = {
  OPEN: {
    label: "待回复",
    color: "bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400",
  },
  REPLIED: {
    label: "已回复",
    color: "bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400",
  },
  CLOSED: {
    label: "已关闭",
    color: "bg-zinc-100 text-zinc-500 dark:bg-zinc-800 dark:text-zinc-400",
  },
}

const FILTER_OPTIONS = [
  { value: "", label: "全部" },
  { value: "OPEN", label: "待回复" },
  { value: "REPLIED", label: "已回复" },
  { value: "CLOSED", label: "已关闭" },
]

export default function MessagesSection({ enabled, onUnauthorized }) {
  const [messages, setMessages] = useState([])
  const [filter, setFilter] = useState("")
  const [replyModal, setReplyModal] = useState(null)
  const [replyText, setReplyText] = useState("")
  const [replying, setReplying] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")
  const [statusMessage, setStatusMessage] = useState("")

  const loadMessages = useCallback(async () => {
    if (!enabled) return
    setLoading(true)
    setError("")

    try {
      const data = await listAdminMessages(filter || undefined)
      setMessages(Array.isArray(data?.messages) ? data.messages : [])
    } catch (loadError) {
      if (isUnauthorizedError(loadError)) {
        onUnauthorized?.(loadError.message)
        return
      }
      setError(loadError?.message || "留言列表加载失败")
    } finally {
      setLoading(false)
    }
  }, [enabled, filter, onUnauthorized])

  useEffect(() => {
    if (!enabled) {
      setMessages([])
      setError("")
      setStatusMessage("")
      return
    }
    loadMessages()
  }, [enabled, loadMessages])

  const filteredMessages = useMemo(() => {
    if (!filter) return messages
    return messages.filter((message) => message.status === filter)
  }, [filter, messages])

  const handleReply = (message) => {
    setReplyModal(message)
    setReplyText(message.reply || "")
  }

  const handleSubmitReply = async () => {
    if (!replyText.trim() || !replyModal) return

    setReplying(true)
    setError("")

    try {
      await replyAdminMessage(replyModal.id, replyText.trim())
      setReplyModal(null)
      setReplyText("")
      setStatusMessage("留言已回复")
      await loadMessages()
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "回复留言失败")
    } finally {
      setReplying(false)
    }
  }

  const handleClose = async (id) => {
    if (!confirm("确定要关闭此留言吗？")) return

    try {
      await updateAdminMessageStatus(id, "CLOSED")
      setStatusMessage("留言已关闭")
      await loadMessages()
    } catch (actionError) {
      if (isUnauthorizedError(actionError)) {
        onUnauthorized?.(actionError.message)
        return
      }
      setError(actionError?.message || "关闭留言失败")
    }
  }

  return (
    <div className="mx-auto max-w-4xl">
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-6"
      >
        <h1 className="text-2xl font-bold tracking-tight">用户留言</h1>
        <p className="mt-1 text-zinc-500 dark:text-zinc-400">查看和回复用户反馈</p>
        {statusMessage && <p className="mt-2 text-sm text-emerald-500">{statusMessage}</p>}
        {error && <p className="mt-2 text-sm text-red-500">{error}</p>}
      </motion.div>

      <motion.div
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.1 }}
        className="mb-6"
      >
        <select
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
          className="rounded-xl border border-zinc-200 bg-white px-4 py-2 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
        >
          {FILTER_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </motion.div>

      <div className="space-y-4">
        <AnimatePresence>
          {filteredMessages.map((message, index) => (
            <motion.div
              key={message.id}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -20 }}
              transition={{ delay: index * 0.05 }}
              className="overflow-hidden rounded-2xl border border-zinc-200/60 bg-white dark:border-zinc-800 dark:bg-zinc-900"
            >
              <div className="flex items-start justify-between border-b border-zinc-100 px-6 py-4 dark:border-zinc-800">
                <div className="flex items-start gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 text-white">
                    <MessageSquare className="h-5 w-5" />
                  </div>
                  <div>
                    <h3 className="font-semibold">{message.subject}</h3>
                    <p className="text-xs text-zinc-500 dark:text-zinc-400">
                      用户 {String(message.userId || "").slice(0, 12)} · {formatDateTime(message.createdAt)}
                    </p>
                  </div>
                </div>
                <span
                  className={`shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium ${STATUS_CONFIG[message.status]?.color}`}
                >
                  {STATUS_CONFIG[message.status]?.label}
                </span>
              </div>

              <div className="px-6 py-4">
                <p className="text-zinc-700 dark:text-zinc-300">{message.content}</p>

                {message.reply && (
                  <motion.div
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="mt-4 rounded-xl bg-blue-50 p-4 dark:bg-blue-900/20"
                  >
                    <div className="mb-2 flex items-center gap-2 text-xs text-blue-600 dark:text-blue-400">
                      <Reply className="h-3.5 w-3.5" />
                      回复于 {formatDateTime(message.repliedAt)}
                    </div>
                    <p className="text-sm text-blue-800 dark:text-blue-200">{message.reply}</p>
                  </motion.div>
                )}
              </div>

              {message.status === "OPEN" && (
                <div className="flex gap-2 border-t border-zinc-100 px-6 py-3 dark:border-zinc-800">
                  <motion.button
                    onClick={() => handleReply(message)}
                    className="inline-flex items-center gap-2 rounded-xl bg-zinc-900 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-100"
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                  >
                    <Reply className="h-4 w-4" />
                    回复
                  </motion.button>
                  <motion.button
                    onClick={() => handleClose(message.id)}
                    className="inline-flex items-center gap-2 rounded-xl border border-zinc-200 px-4 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-400 dark:hover:bg-zinc-800"
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                  >
                    <X className="h-4 w-4" />
                    关闭
                  </motion.button>
                </div>
              )}
            </motion.div>
          ))}
        </AnimatePresence>

        {!loading && filteredMessages.length === 0 && (
          <div className="py-12 text-center text-zinc-500">暂无留言</div>
        )}
      </div>

      <AnimatePresence>
        {replyModal && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
            onClick={(event) => event.target === event.currentTarget && setReplyModal(null)}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              transition={{ type: "spring", stiffness: 300, damping: 30 }}
              className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-zinc-900"
            >
              <div className="flex items-center justify-between border-b border-zinc-200 px-6 py-4 dark:border-zinc-800">
                <h2 className="text-lg font-semibold">回复留言</h2>
                <button
                  onClick={() => setReplyModal(null)}
                  className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-100 dark:hover:bg-zinc-800"
                >
                  <X className="h-5 w-5" />
                </button>
              </div>

              <div className="p-6">
                <div className="mb-4 rounded-xl bg-zinc-50 p-4 dark:bg-zinc-800">
                  <p className="mb-1 text-sm font-medium">{replyModal.subject}</p>
                  <p className="text-sm text-zinc-600 dark:text-zinc-400">{replyModal.content}</p>
                </div>

                <div>
                  <label className="mb-1.5 block text-sm font-medium">回复内容</label>
                  <textarea
                    value={replyText}
                    onChange={(event) => setReplyText(event.target.value)}
                    rows={4}
                    placeholder="输入您的回复..."
                    className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-3 text-sm outline-none transition-all focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
                  />
                </div>

                <div className="mt-4 flex justify-end gap-3">
                  <button
                    onClick={() => setReplyModal(null)}
                    className="rounded-xl px-4 py-2 text-sm font-medium text-zinc-600 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                  >
                    取消
                  </button>
                  <motion.button
                    onClick={handleSubmitReply}
                    disabled={!replyText.trim() || replying}
                    className="inline-flex items-center gap-2 rounded-xl bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600 disabled:opacity-50"
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                  >
                    {replying ? (
                      <>
                        <Loader2 className="h-4 w-4 animate-spin" />
                        发送中...
                      </>
                    ) : (
                      <>
                        <Send className="h-4 w-4" />
                        发送回复
                      </>
                    )}
                  </motion.button>
                </div>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
