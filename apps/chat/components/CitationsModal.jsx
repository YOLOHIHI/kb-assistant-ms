"use client"

import { motion, AnimatePresence } from "framer-motion"
import { X, FileText } from "lucide-react"
import { formatCitationCount, formatKnowledgeBaseLabel } from "../lib/copy"

export default function CitationsModal({ isOpen, onClose, citations = [], activeCitation, onSelectCitation }) {
  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 z-[80] bg-black/60" onClick={onClose} />
          <motion.div initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.96 }} className="fixed left-1/2 top-1/2 z-[90] flex h-[80vh] w-[min(1080px,94vw)] -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-2xl border border-zinc-200 bg-white shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
            <div className="flex w-[360px] shrink-0 flex-col border-r border-zinc-200 dark:border-zinc-800">
              <div className="flex items-center justify-between px-5 py-4">
                <div>
                  <div className="text-xs font-medium uppercase tracking-[0.24em] text-zinc-400">引用资料</div>
                  <h2 className="mt-2 text-xl font-semibold">引用来源</h2>
                  <div className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">{formatCitationCount(citations.length)}</div>
                </div>
                <button onClick={onClose} className="rounded-lg p-1.5 hover:bg-zinc-100 dark:hover:bg-zinc-800"><X className="h-5 w-5" /></button>
              </div>
              <div className="flex-1 space-y-2 overflow-y-auto px-4 pb-4">
                {citations.map((citation, index) => {
                  const active = activeCitation?.id === citation.id || (!activeCitation && index === 0)
                  return (
                    <button key={citation.id || `${citation.chunkId}_${index}`} onClick={() => onSelectCitation?.(citation)} className={`w-full rounded-2xl border p-4 text-left transition ${active ? "border-zinc-900 bg-zinc-50 dark:border-white dark:bg-zinc-800" : "border-zinc-200 hover:bg-zinc-50 dark:border-zinc-800 dark:hover:bg-zinc-800/60"}`}>
                      <div className="flex items-start gap-3">
                        <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-zinc-100 dark:bg-zinc-800"><FileText className="h-4 w-4 text-zinc-500" /></div>
                        <div className="min-w-0">
                          <div className="truncate text-sm font-medium">{citation.filename || citation.docId || `引用 ${index + 1}`}</div>
                          <div className="mt-1 line-clamp-3 text-xs text-zinc-500 dark:text-zinc-400">{citation.snippet || "暂无摘要预览。"}</div>
                        </div>
                      </div>
                    </button>
                  )
                })}
              </div>
            </div>
            <div className="flex min-w-0 flex-1 flex-col">
              <div className="border-b border-zinc-200 px-5 py-4 dark:border-zinc-800">
                <div className="text-sm font-medium">{activeCitation?.filename || activeCitation?.docId || "来源预览"}</div>
                <div className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">{formatKnowledgeBaseLabel(activeCitation?.kbId)}</div>
              </div>
              <div className="flex-1 overflow-y-auto px-5 py-4 text-sm leading-7 text-zinc-700 dark:text-zinc-300">
                {activeCitation?.fullText || activeCitation?.snippet || "选择左侧引用后可查看对应来源内容。"}
              </div>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
