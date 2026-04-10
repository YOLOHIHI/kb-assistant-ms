"use client"

import { useRef, useState, forwardRef, useImperativeHandle, useEffect, useMemo } from "react"
import { useSelectedModel } from "./hooks/useSelectedModel"
import { Send, Loader2, Plus, Database, ChevronDown, ChevronUp, Bot, Minus, Check, Mic } from "lucide-react"
import ComposerActionsPopover from "./ComposerActionsPopover"
import { cls } from "./utils"
import { appCopy } from "../lib/copy"

const Composer = forwardRef(function Composer(
  {
    onSend,
    busy,
    models = [],
    selectedModelId,
    onSelectModel,
    useKb,
    setUseKb,
    kbs = [],
    selectedKbIds = [],
    setSelectedKbIds,
    contextSize,
    setContextSize,
  },
  ref,
) {
  const [value, setValue] = useState("")
  const [sending, setSending] = useState(false)
  const [kbPickerOpen, setKbPickerOpen] = useState(false)
  const [modelPickerOpen, setModelPickerOpen] = useState(false)

  const inputRef = useRef(null)
  const kbPickerRef = useRef(null)
  const modelPickerRef = useRef(null)

  const { chatModels, selectedModel } = useSelectedModel(models, selectedModelId)

  const selectedKbNames = useMemo(() => {
    const map = new Map((kbs || []).map((kb) => [kb.id, kb.name]))
    return (selectedKbIds || []).map((id) => map.get(id)).filter(Boolean)
  }, [kbs, selectedKbIds])

  useEffect(() => {
    function handleClick(event) {
      if (kbPickerRef.current && !kbPickerRef.current.contains(event.target)) setKbPickerOpen(false)
      if (modelPickerRef.current && !modelPickerRef.current.contains(event.target)) setModelPickerOpen(false)
    }
    document.addEventListener("mousedown", handleClick)
    return () => document.removeEventListener("mousedown", handleClick)
  }, [])

  useEffect(() => {
    if (!inputRef.current) return
    const textarea = inputRef.current
    textarea.style.height = "auto"
    textarea.style.height = `${Math.min(textarea.scrollHeight, 12 * 24)}px`
    textarea.style.overflowY = textarea.scrollHeight > 12 * 24 ? "auto" : "hidden"
  }, [value])

  useImperativeHandle(
    ref,
    () => ({
      insertTemplate: (templateContent) => {
        setValue((prev) => {
          const nextValue = prev ? `${prev}\n\n${templateContent}` : templateContent
          setTimeout(() => {
            inputRef.current?.focus()
            const length = nextValue.length
            inputRef.current?.setSelectionRange(length, length)
          }, 0)
          return nextValue
        })
      },
      focus: () => inputRef.current?.focus(),
    }),
    [],
  )

  async function handleSend() {
    if (!value.trim() || sending) return
    const text = value
    setSending(true)
    setValue("")
    inputRef.current?.focus()
    try {
      await onSend?.(text)
    } finally {
      setSending(false)
    }
  }

  function toggleKb(kbId) {
    const next = selectedKbIds.includes(kbId)
      ? selectedKbIds.filter((id) => id !== kbId)
      : [...selectedKbIds, kbId]
    setSelectedKbIds?.(next)
  }

  const hasContent = value.trim().length > 0
  const kbSummary = !useKb ? "知识库已关闭" : selectedKbNames.length ? selectedKbNames.join("、") : "全部知识库"

  return (
    <div className="border-t border-zinc-200/60 p-4 dark:border-zinc-800">
      <div className="mx-auto flex max-w-3xl flex-col rounded-3xl border border-zinc-200 bg-white shadow-sm transition-all duration-200 dark:border-zinc-800 dark:bg-zinc-950">
        <div className="flex-1 px-4 pt-4 pb-2">
          <textarea
            ref={inputRef}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="输入你的问题、任务或指令"
            rows={1}
            className={cls(
              "min-h-[24px] w-full resize-none bg-transparent text-left text-sm leading-6 outline-none placeholder:text-zinc-400 transition-all duration-200",
            )}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault()
                handleSend()
              }
            }}
          />
        </div>

        <div className="flex flex-wrap items-center gap-1.5 border-t border-zinc-100 px-3 pt-2 pb-2 dark:border-zinc-800">
          <button
            onClick={() => {
              const next = !useKb
              setUseKb?.(next)
              if (next) setKbPickerOpen(true)
            }}
            className={cls(
              "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium transition-colors",
              useKb
                ? "bg-blue-50 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400"
                : "text-zinc-500 hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800",
            )}
          >
            <Database className="h-3.5 w-3.5" />
            知识库
          </button>

          {useKb && (
            <div className="relative" ref={kbPickerRef}>
              <button
                onClick={() => setKbPickerOpen((value) => !value)}
                className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium text-zinc-500 transition-colors hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
                title="选择知识库"
              >
                <span className="max-w-[180px] truncate">{kbSummary}</span>
                <ChevronDown className="h-3 w-3" />
              </button>
              {kbPickerOpen && (
                <div className="absolute bottom-full left-0 z-50 mb-1.5 w-72 rounded-xl border border-zinc-200 bg-white p-2 shadow-lg dark:border-zinc-700 dark:bg-zinc-900">
                  <div className="px-2 py-1 text-[11px] font-semibold tracking-wider text-zinc-400">知识库列表</div>
                  <div className="max-h-64 overflow-y-auto space-y-1">
                    {kbs.map((kb) => {
                      const checked = selectedKbIds.includes(kb.id)
                      return (
                        <button
                          key={kb.id}
                          onClick={() => toggleKb(kb.id)}
                          className={cls(
                            "flex w-full items-center gap-2 rounded-lg px-2 py-2 text-left text-sm transition-colors",
                            checked ? "bg-zinc-100 dark:bg-zinc-800" : "hover:bg-zinc-50 dark:hover:bg-zinc-800",
                          )}
                        >
                          <span className="flex h-4 w-4 items-center justify-center rounded border border-zinc-300 dark:border-zinc-700">
                            {checked ? <Check className="h-3 w-3" /> : null}
                          </span>
                          <span className="truncate flex-1">{kb.name}</span>
                        </button>
                      )
                    })}
                    {kbs.length === 0 ? <div className="px-2 py-3 text-xs text-zinc-500 dark:text-zinc-400">暂无可用知识库</div> : null}
                  </div>
                </div>
              )}
            </div>
          )}

          <div className="mx-1 h-4 w-px bg-zinc-200 dark:bg-zinc-700" />

          <div className="relative" ref={modelPickerRef}>
            <button
              onClick={() => setModelPickerOpen((value) => !value)}
              className="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium text-zinc-500 transition-colors hover:bg-zinc-100 dark:text-zinc-400 dark:hover:bg-zinc-800"
              title="选择模型"
            >
              <Bot className="h-3.5 w-3.5" />
              {selectedModel?.displayName || selectedModel?.modelId || selectedModel?.id || "选择模型"}
              <ChevronDown className="h-3 w-3" />
            </button>
            {modelPickerOpen && (
              <div className="absolute bottom-full left-0 z-50 mb-1.5 w-56 overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-lg dark:border-zinc-700 dark:bg-zinc-900">
                <div className="border-b border-zinc-100 px-3 py-2 text-[11px] font-semibold tracking-wider text-zinc-400 dark:border-zinc-800">选择模型</div>
                <div className="max-h-72 overflow-y-auto">
                  {chatModels.map((model) => (
                    <button
                      key={model.id}
                      onClick={() => {
                        onSelectModel?.(model.id)
                        setModelPickerOpen(false)
                      }}
                      className={cls(
                        "flex w-full items-center gap-2 px-3 py-2 text-left text-sm transition-colors",
                        model.id === selectedModelId ? "bg-zinc-100 font-medium dark:bg-zinc-800" : "hover:bg-zinc-50 dark:hover:bg-zinc-800",
                      )}
                    >
                      <Bot className="h-4 w-4 shrink-0" />
                      <span className="truncate">{model.displayName || model.modelId || model.id}</span>
                    </button>
                  ))}
                  {chatModels.length === 0 ? <div className="px-3 py-3 text-sm text-zinc-500 dark:text-zinc-400">暂无可用模型</div> : null}
                </div>
              </div>
            )}
          </div>

          <div className="mx-1 h-4 w-px bg-zinc-200 dark:bg-zinc-700" />

          <div className="inline-flex items-center gap-1 rounded-full border border-zinc-200 px-1.5 py-0.5 dark:border-zinc-700">
            <button
              onClick={() => setContextSize?.(Math.max(0, contextSize - 1))}
              className="rounded-full p-0.5 text-zinc-400 transition-colors hover:text-zinc-700 dark:hover:text-zinc-200"
              title="减少上下文轮数"
            >
              <Minus className="h-3 w-3" />
            </button>
            <span className="min-w-[36px] text-center text-xs font-medium text-zinc-500 dark:text-zinc-400">{contextSize} 轮</span>
            <button
              onClick={() => setContextSize?.(Math.min(20, contextSize + 1))}
              className="rounded-full p-0.5 text-zinc-400 transition-colors hover:text-zinc-700 dark:hover:text-zinc-200"
              title="增加上下文轮数"
            >
              <ChevronUp className="h-3 w-3" />
            </button>
          </div>
        </div>

        <div className="flex items-center justify-between px-3 pb-3">
          <ComposerActionsPopover>
            <button className="inline-flex shrink-0 items-center justify-center rounded-full p-2 text-zinc-500 transition-colors hover:bg-zinc-100 hover:text-zinc-700 dark:text-zinc-400 dark:hover:bg-zinc-800 dark:hover:text-zinc-300" title="更多能力">
              <Plus className="h-5 w-5" />
            </button>
          </ComposerActionsPopover>

          <div className="flex shrink-0 items-center gap-1">
            <button className="inline-flex items-center justify-center rounded-full p-2 text-zinc-400 transition-colors hover:bg-zinc-100 hover:text-zinc-600 dark:hover:bg-zinc-800 dark:hover:text-zinc-300" title="语音输入（即将支持）" disabled>
              <Mic className="h-5 w-5" />
            </button>
            <button
              onClick={handleSend}
              disabled={sending || busy || !hasContent}
              className={cls(
                "inline-flex shrink-0 items-center justify-center rounded-full p-2.5 transition-colors",
                hasContent
                  ? "bg-zinc-900 text-white hover:bg-zinc-800 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-200"
                  : "cursor-not-allowed bg-zinc-200 text-zinc-400 dark:bg-zinc-800 dark:text-zinc-600",
              )}
            >
              {sending || busy ? <Loader2 className="h-5 w-5 animate-spin" /> : <Send className="h-5 w-5" />}
            </button>
          </div>
        </div>
      </div>

      <div className="mx-auto mt-2 max-w-3xl px-1 text-center text-[11px] text-zinc-400 dark:text-zinc-500">
        {busy ? "助手正在生成回复，请稍候…" : appCopy.disclaimer}
      </div>
    </div>
  )
})

export default Composer
