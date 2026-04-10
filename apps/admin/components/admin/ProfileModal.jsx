"use client"

import React, { useState, useEffect, useRef } from "react"
import { motion, AnimatePresence } from "framer-motion"
import { X, Camera, Trash2, Loader2 } from "lucide-react"
import { validateAvatarFile } from "@/lib/admin-api"

function getAvatarText(name) {
  if (!name) return "AD"
  return name.slice(0, 2).toUpperCase()
}

export default function ProfileModal({ isOpen, onClose, user, onSave }) {
  const [displayName, setDisplayName] = useState("")
  const [avatarDataUrl, setAvatarDataUrl] = useState("")
  const [loading, setSaving] = useState(false)
  const [error, setError] = useState("")
  const fileInputRef = useRef(null)

  useEffect(() => {
    if (user) {
      setDisplayName(user.displayName || "")
      setAvatarDataUrl(user.avatarDataUrl || "")
    }
    setError("")
  }, [isOpen, user])

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return

    try {
      const result = await validateAvatarFile(file)
      if (!result.valid) {
        setError(result.error || "头像上传失败")
        return
      }
      setAvatarDataUrl(result.dataUrl || "")
      setError("")
    } catch (uploadError) {
      setError(uploadError?.message || "头像上传失败")
    }
  }

  const handleClearAvatar = () => {
    setAvatarDataUrl("")
    if (fileInputRef.current) {
      fileInputRef.current.value = ""
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    setError("")

    try {
      await onSave({
        displayName,
        avatarDataUrl,
      })
    } catch (saveError) {
      setError(saveError?.message || "保存失败，请稍后重试")
    } finally {
      setSaving(false)
    }
  }

  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
          onClick={(e) => e.target === e.currentTarget && onClose()}
        >
          <motion.div
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            transition={{ type: "spring", stiffness: 300, damping: 30 }}
            className="relative w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl dark:bg-zinc-900"
          >
            {/* Close button */}
            <button
              onClick={onClose}
              className="absolute right-4 top-4 rounded-lg p-1.5 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-200"
            >
              <X className="h-5 w-5" />
            </button>

            {/* Header */}
            <div className="border-b border-zinc-200 px-6 py-4 dark:border-zinc-800">
              <h2 className="text-lg font-semibold">个人资料</h2>
              <p className="text-sm text-zinc-500 dark:text-zinc-400">修改您的昵称和头像</p>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmit} className="p-6">
              {/* Avatar section */}
              <div className="mb-6 flex flex-col items-center">
                <div className="relative">
                  {avatarDataUrl ? (
                    <motion.img
                      key={avatarDataUrl}
                      initial={{ scale: 0.8, opacity: 0 }}
                      animate={{ scale: 1, opacity: 1 }}
                      src={avatarDataUrl}
                      alt="Avatar"
                      className="h-24 w-24 rounded-full object-cover ring-4 ring-zinc-100 dark:ring-zinc-800"
                    />
                  ) : (
                    <motion.div
                      key={displayName || "default"}
                      initial={{ scale: 0.8, opacity: 0 }}
                      animate={{ scale: 1, opacity: 1 }}
                      className="flex h-24 w-24 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-indigo-600 text-2xl font-bold text-white ring-4 ring-zinc-100 dark:ring-zinc-800"
                    >
                      {getAvatarText(displayName)}
                    </motion.div>
                  )}

                  <motion.button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="absolute -bottom-1 -right-1 rounded-full bg-white p-2 shadow-lg ring-2 ring-white transition-colors hover:bg-zinc-100 dark:bg-zinc-800 dark:ring-zinc-800 dark:hover:bg-zinc-700"
                    whileHover={{ scale: 1.1 }}
                    whileTap={{ scale: 0.9 }}
                  >
                    <Camera className="h-4 w-4" />
                  </motion.button>
                </div>

                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/png,image/jpeg,image/webp,image/gif"
                  onChange={handleFileChange}
                  className="hidden"
                />

                {avatarDataUrl && (
                  <motion.button
                    type="button"
                    onClick={handleClearAvatar}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="mt-3 flex items-center gap-1.5 text-sm text-zinc-500 transition-colors hover:text-red-500"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    移除头像
                  </motion.button>
                )}
              </div>

              {/* Display name input */}
              <div>
                <label htmlFor="profile-displayname" className="mb-1.5 block text-sm font-medium">
                  昵称
                </label>
                <input
                  id="profile-displayname"
                  type="text"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  maxLength={64}
                  placeholder="输入您的昵称"
                  className="w-full rounded-xl border border-zinc-200 bg-white px-4 py-2.5 text-sm outline-none transition-all focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 dark:border-zinc-700 dark:bg-zinc-800"
                />
              </div>

              {error && (
                <motion.p
                  initial={{ opacity: 0, y: -10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="mt-3 text-sm text-red-500"
                >
                  {error}
                </motion.p>
              )}

              {/* Actions */}
              <div className="mt-6 flex gap-3">
                <button
                  type="button"
                  onClick={onClose}
                  className="flex-1 rounded-xl border border-zinc-200 px-4 py-2.5 text-sm font-medium transition-colors hover:bg-zinc-50 dark:border-zinc-700 dark:hover:bg-zinc-800"
                >
                  取消
                </button>
                <motion.button
                  type="submit"
                  disabled={loading}
                  className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-zinc-900 px-4 py-2.5 text-sm font-medium text-white transition-all hover:bg-zinc-800 disabled:opacity-50 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-100"
                  whileHover={{ scale: 1.01 }}
                  whileTap={{ scale: 0.99 }}
                >
                  {loading ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      保存中...
                    </>
                  ) : (
                    "保存"
                  )}
                </motion.button>
              </div>
            </form>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
