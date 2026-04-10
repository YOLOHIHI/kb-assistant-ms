"use client"

import { useEffect, useState } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { X, Upload, Trash2 } from "lucide-react"
import { displayNameForUser, initials } from "../lib/kb-api"

export default function ProfileModal({ isOpen, user, loading, error, onClose, onSave }) {
  const [displayName, setDisplayName] = useState("")
  const [avatarDataUrl, setAvatarDataUrl] = useState("")

  useEffect(() => {
    if (!isOpen) return
    setDisplayName(String(user?.displayName || ""))
    setAvatarDataUrl(String(user?.avatarDataUrl || ""))
  }, [isOpen, user])

  const handleFileChange = async (event) => {
    const file = event.target.files?.[0]
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => setAvatarDataUrl(String(reader.result || ""))
    reader.readAsDataURL(file)
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    await onSave?.({ displayName: displayName.trim(), avatarDataUrl })
  }

  const name = displayName || displayNameForUser(user)

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-[80] bg-black/60"
            onClick={onClose}
          />
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            className="fixed left-1/2 top-1/2 z-[90] w-full max-w-xl -translate-x-1/2 -translate-y-1/2 rounded-2xl border border-zinc-200 bg-white p-6 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900"
          >
            <div className="mb-5 flex items-center justify-between">
              <div>
                <div className="text-xs font-medium uppercase tracking-[0.24em] text-zinc-400">个人资料</div>
                <h2 className="mt-2 text-xl font-semibold">个人资料</h2>
              </div>
              <button onClick={onClose} className="rounded-lg p-1.5 hover:bg-zinc-100 dark:hover:bg-zinc-800">
                <X className="h-5 w-5" />
              </button>
            </div>

            <form className="space-y-5" onSubmit={handleSubmit}>
              <div className="flex items-center gap-4 rounded-2xl border border-zinc-200 p-4 dark:border-zinc-800">
                <div className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-zinc-900 text-lg font-semibold text-white dark:bg-white dark:text-zinc-900">
                  {avatarDataUrl ? <img src={avatarDataUrl} alt={name} className="h-full w-full object-cover" /> : initials(name)}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-base font-medium">{name}</div>
                  <div className="truncate text-sm text-zinc-500 dark:text-zinc-400">{user?.username || ""}</div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <label className="inline-flex cursor-pointer items-center gap-2 rounded-xl border border-zinc-300 px-3 py-2 text-sm hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-800">
                      <Upload className="h-4 w-4" />
                      <span>上传头像</span>
                      <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" className="hidden" onChange={handleFileChange} />
                    </label>
                    <button type="button" onClick={() => setAvatarDataUrl("")} className="inline-flex items-center gap-2 rounded-xl border border-zinc-300 px-3 py-2 text-sm hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-800">
                      <Trash2 className="h-4 w-4" />
                      <span>清空</span>
                    </button>
                  </div>
                </div>
              </div>

              <label className="block space-y-2 text-sm">
                <span className="font-medium">显示名称</span>
                <input
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="w-full rounded-xl border border-zinc-300 px-4 py-3 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
                  placeholder="输入你希望展示的名称"
                />
              </label>

              {error ? <div className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-600 dark:bg-red-950/30 dark:text-red-300">{error}</div> : null}

              <div className="flex gap-3">
                <button type="button" onClick={onClose} className="flex-1 rounded-xl border border-zinc-300 px-4 py-3 text-sm font-medium hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-800">取消</button>
                <button type="submit" disabled={loading} className="flex-1 rounded-xl bg-zinc-900 px-4 py-3 text-sm font-medium text-white transition hover:bg-zinc-800 disabled:opacity-50 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-100">{loading ? "保存中..." : "保存资料"}</button>
              </div>
            </form>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
