"use client"
import { Asterisk, Menu, ChevronDown } from "lucide-react"
import { useState } from "react"
import { useSelectedModel } from "./hooks/useSelectedModel"

export default function Header({
  sidebarCollapsed,
  setSidebarOpen,
  models = [],
  selectedModelId,
  onSelectModel,
}) {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false)

  const { chatModels, selectedModel } = useSelectedModel(models, selectedModelId)

  return (
    <div className="sticky top-0 z-30 flex items-center gap-2 border-b border-zinc-200/60 bg-white/80 px-4 py-3 backdrop-blur dark:border-zinc-800 dark:bg-zinc-900/70">
      {sidebarCollapsed && (
        <button
          onClick={() => setSidebarOpen(true)}
          className="md:hidden inline-flex items-center justify-center rounded-lg p-2 hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800"
          aria-label="打开侧边栏"
        >
          <Menu className="h-5 w-5" />
        </button>
      )}

      <div className="hidden md:flex relative">
        <button
          onClick={() => setIsDropdownOpen((value) => !value)}
          className="inline-flex items-center gap-2 rounded-full border border-zinc-200 bg-white px-3 py-2 text-sm font-semibold tracking-tight hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:border-zinc-800 dark:bg-zinc-950 dark:hover:bg-zinc-800"
        >
          <Asterisk className="h-4 w-4" />
          {selectedModel?.displayName || selectedModel?.modelId || selectedModel?.id || "选择模型"}
          <ChevronDown className="h-4 w-4" />
        </button>

        {isDropdownOpen && (
          <div className="absolute top-full left-0 mt-1 max-h-[360px] w-72 overflow-y-auto rounded-lg border border-zinc-200 bg-white shadow-lg dark:border-zinc-800 dark:bg-zinc-950 z-50">
            {chatModels.map((model) => (
              <button
                key={model.id}
                onClick={() => {
                  onSelectModel?.(model.id)
                  setIsDropdownOpen(false)
                }}
                className={`flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-zinc-100 dark:hover:bg-zinc-800 ${model.id === selectedModelId ? "bg-zinc-100 font-medium dark:bg-zinc-800" : ""}`}
              >
                <Asterisk className="h-4 w-4 shrink-0" />
                <div className="min-w-0">
                  <div className="truncate">{model.displayName || model.modelId || model.id}</div>
                  <div className="truncate text-xs text-zinc-500 dark:text-zinc-400">{model.providerName || model.modelId || model.id}</div>
                </div>
              </button>
            ))}
            {chatModels.length === 0 && (
              <div className="px-3 py-4 text-sm text-zinc-500 dark:text-zinc-400">暂无可用模型</div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
