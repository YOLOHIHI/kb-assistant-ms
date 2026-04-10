"use client"
import { useState } from "react"
import { Paperclip, Bot, Search, Palette, BookOpen, MoreHorizontal, Globe, ChevronRight } from "lucide-react"
import { Popover, PopoverContent, PopoverTrigger } from "./ui/popover"
import { appCopy } from "../lib/copy"

export default function ComposerActionsPopover({ children }) {
  const [open, setOpen] = useState(false)
  const [showMore, setShowMore] = useState(false)

  const mainActions = [
    {
      icon: Paperclip,
      label: "添加图片与文件",
      description: "统一前端架构完成后接入",
    },
    {
      icon: Bot,
      label: "智能代理模式",
      badge: appCopy.plannedLabel,
      description: "统一前端架构完成后接入",
    },
    {
      icon: Search,
      label: "深度研究",
      description: "统一前端架构完成后接入",
    },
    {
      icon: Palette,
      label: "生成图片",
      description: "统一前端架构完成后接入",
    },
    {
      icon: BookOpen,
      label: "学习模式",
      description: "统一前端架构完成后接入",
    },
  ]

  const moreActions = [
    {
      icon: Globe,
      label: "联网搜索",
      description: "统一前端架构完成后接入",
    },
    {
      icon: Palette,
      label: "画布",
      description: "统一前端架构完成后接入",
    },
    {
      icon: () => (
        <div className="h-5 w-5 rounded bg-gradient-to-br from-blue-500 via-green-400 to-yellow-400 flex items-center justify-center">
          <div className="h-2.5 w-2.5 bg-white rounded-sm" />
        </div>
      ),
      label: "连接 Google Drive",
      description: "统一前端架构完成后接入",
    },
    {
      icon: () => (
        <div className="h-5 w-5 rounded bg-blue-500 flex items-center justify-center">
          <div className="h-2.5 w-2.5 bg-white rounded-sm" />
        </div>
      ),
      label: "连接 OneDrive",
      description: "统一前端架构完成后接入",
    },
    {
      icon: () => (
        <div className="h-5 w-5 rounded bg-teal-500 flex items-center justify-center">
          <div className="h-2.5 w-2.5 bg-white rounded-sm" />
        </div>
      ),
      label: "连接 SharePoint",
      description: "统一前端架构完成后接入",
    },
  ]

  const handleMoreClick = () => {
    setShowMore(true)
  }

  const handleBackClick = () => {
    setShowMore(false)
  }

  const handleOpenChange = (newOpen) => {
    setOpen(newOpen)
    if (!newOpen) {
      setShowMore(false)
    }
  }

  return (
    <Popover open={open} onOpenChange={handleOpenChange}>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start" side="top">
        {!showMore ? (
          <div className="p-2 min-w-[220px]">
            <div className="space-y-0.5">
              {mainActions.map((action, index) => {
                const IconComponent = action.icon
                return (
                  <button
                    key={index}
                    type="button"
                    disabled
                    title={action.description}
                    className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-zinc-400 opacity-70"
                  >
                    <IconComponent className="h-5 w-5 text-zinc-600 dark:text-zinc-400" />
                    <span className="flex-1">{action.label}</span>
                    {action.badge && (
                      <span className="ml-auto px-2 py-0.5 text-xs bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300 rounded-full font-medium">
                        {action.badge}
                      </span>
                    )}
                    {!action.badge && (
                      <span className="ml-auto text-[11px] text-zinc-400">{appCopy.plannedLabel}</span>
                    )}
                  </button>
                )
              })}
              <div className="my-1 border-t border-zinc-200 dark:border-zinc-700" />
              <button
                onClick={handleMoreClick}
                className="flex items-center gap-3 w-full px-3 py-2.5 text-sm text-left hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-lg transition-colors"
              >
                <MoreHorizontal className="h-5 w-5 text-zinc-600 dark:text-zinc-400" />
                <span>更多能力</span>
                <ChevronRight className="h-4 w-4 ml-auto text-zinc-400" />
              </button>
            </div>
            <div className="px-3 pb-2 pt-1 text-[11px] text-zinc-400">
              这些入口会在统一到 Next 前端架构后陆续接入。
            </div>
          </div>
        ) : (
          <div className="flex min-w-[440px]">
            <div className="flex-1 p-2 border-r border-zinc-200 dark:border-zinc-700">
              <div className="space-y-0.5">
                {mainActions.map((action, index) => {
                  const IconComponent = action.icon
                  return (
                    <button
                      key={index}
                      type="button"
                      disabled
                      title={action.description}
                      className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-zinc-400 opacity-70"
                    >
                      <IconComponent className="h-5 w-5 text-zinc-600 dark:text-zinc-400" />
                      <span className="flex-1">{action.label}</span>
                      {action.badge && (
                        <span className="ml-auto px-2 py-0.5 text-xs bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300 rounded-full font-medium">
                          {action.badge}
                        </span>
                      )}
                      {!action.badge && (
                        <span className="ml-auto text-[11px] text-zinc-400">{appCopy.plannedLabel}</span>
                      )}
                    </button>
                  )
                })}
                <div className="my-1 border-t border-zinc-200 dark:border-zinc-700" />
                <button
                  onClick={handleBackClick}
                  className="flex items-center gap-3 w-full px-3 py-2.5 text-sm text-left hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-lg transition-colors bg-zinc-100 dark:bg-zinc-800"
                >
                  <MoreHorizontal className="h-5 w-5 text-zinc-600 dark:text-zinc-400" />
                  <span>更多能力</span>
                  <ChevronRight className="h-4 w-4 ml-auto text-zinc-400" />
                </button>
              </div>
            </div>
            <div className="flex-1 p-2">
              <div className="space-y-0.5">
                {moreActions.map((action, index) => {
                  const IconComponent = action.icon
                  return (
                    <button
                      key={index}
                      type="button"
                      disabled
                      title={action.description}
                      className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-zinc-400 opacity-70"
                    >
                      {typeof IconComponent === "function" ? (
                        <IconComponent />
                      ) : (
                        <IconComponent className="h-5 w-5 text-zinc-600 dark:text-zinc-400" />
                      )}
                      <span className="flex-1">{action.label}</span>
                      <span className="text-[11px] text-zinc-400">{appCopy.plannedLabel}</span>
                    </button>
                  )
                })}
              </div>
            </div>
          </div>
        )}
      </PopoverContent>
    </Popover>
  )
}
