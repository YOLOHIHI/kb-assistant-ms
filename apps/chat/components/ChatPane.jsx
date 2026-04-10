/**
 * ChatPane.jsx — 聊天主内容区域
 *
 * 职责：
 *   渲染一个会话（Conversation）的完整界面，包含：
 *   - 会话标题、元信息标签（当前模型 / 知识库状态 / 上下文轮数）
 *   - 历史消息列表（用户消息气泡 + AI 回复气泡）
 *   - AI 正在思考时的动画占位（ThinkingMessage）
 *   - 底部消息输入框（Composer）
 *   - 自动滚到底部的逻辑（新消息 / 回复到来时跟随滚动）
 *
 * 关键设计：
 *   使用 forwardRef 包裹，让父组件（AIAssistantUI）可以通过 ref
 *   调用本组件内部的 insertTemplate 方法，将模板文本注入输入框。
 */

"use client"
// ↑ Next.js 约定：凡需要用到 React Hooks、浏览器 API、用户交互的组件，
//   必须在文件顶部写 "use client"，表示这是浏览器端渲染的客户端组件。

import { useState, useEffect, useRef, forwardRef, useImperativeHandle } from "react"
// useState          管理组件内部状态（变化时触发重新渲染）
// useEffect         在渲染完成后执行"副作用"（如滚动、订阅事件、API 请求）
// useRef            创建一个持久引用，修改 .current 不触发重渲染；常用于挂载 DOM 节点
// forwardRef        包裹函数组件，使其能接收父组件传来的 ref 并转发
// useImperativeHandle 配合 forwardRef，精确控制 ref 暴露给父组件的内容

import { useSelectedModel } from "./hooks/useSelectedModel"
// 自定义 Hook：给定模型列表和当前选中的模型 ID，返回对应的模型对象

import { Pencil, RefreshCw, Check, X, Square, BookOpen } from "lucide-react"
// lucide-react 是一套纯 SVG 图标库，每个图标都是 React 组件
// Pencil    → 铅笔（编辑）
// RefreshCw → 顺时针刷新箭头（重新发送）
// Check     → 勾号（保存确认）
// X         → 叉号（取消）
// Square    → 实心方块（停止生成）
// BookOpen  → 翻开的书（查看引用来源）

import Message from "./Message"
// Message：渲染单条消息气泡的外壳组件（处理左右布局、头像、气泡样式）

import MarkdownRenderer from "./MarkdownRenderer"
// MarkdownRenderer：把 AI 回复的 Markdown 文本渲染成带样式的 HTML，
// 支持代码高亮、数学公式（KaTeX）、引用角标等特性

import Composer from "./Composer"
// Composer：底部输入区，包含文字输入框、发送按钮、模型/知识库选择控件

import { cls, formatConvTime } from "./utils"
// cls            拼接 Tailwind 类名的工具函数（等价于 classnames 库）
// formatConvTime 格式化时间戳为相对时间，如"3 分钟前"、"昨天"

import { appCopy, formatCitationCount, formatMessageCount } from "../lib/copy"
// appCopy            全局文案常量对象（如标签 "AI"、"我" 等）
// formatCitationCount 把数字格式化为"查看 3 条引用来源"
// formatMessageCount  把数字格式化为"共 5 条消息"

// =============================================================================
// 子组件：AI 正在思考时的动画指示器
// =============================================================================

/**
 * ThinkingMessage — 等待 AI 响应时的占位动画
 *
 * 展示三个依次弹跳的圆点 + "助手正在思考..."文字 + "停止生成"按钮。
 * 这是一个纯展示（Presentational）组件：它没有自己的状态，
 * 所有逻辑由父组件通过 props 传入。
 */
function ThinkingMessage({ onPause }) {
  return (
    // role="assistant" 告诉 Message 组件用 AI 的样式渲染（左对齐、灰色气泡）
    <Message role="assistant" avatarLabel={appCopy.assistantLabel}>
      <div className="flex items-center gap-3">

        {/* 三个错开延迟的弹跳圆点，形成"波浪"效果 */}
        <div className="flex items-center gap-1">
          {/* [animation-delay:-0.3s] 是 Tailwind 任意值语法：
              方括号内可写任意合法 CSS 值，Tailwind 会动态生成对应的类。
              负延迟让动画"已经开始了一段时间"，避免三点同时弹起。 */}
          <div className="h-2 w-2 animate-bounce rounded-full bg-zinc-400 [animation-delay:-0.3s]"></div>
          <div className="h-2 w-2 animate-bounce rounded-full bg-zinc-400 [animation-delay:-0.15s]"></div>
          <div className="h-2 w-2 animate-bounce rounded-full bg-zinc-400"></div>
        </div>

        <span className="text-sm text-zinc-500">助手正在思考...</span>

        {/* 停止按钮：点击后调用父组件传来的 onPause，中断当前 AI 生成 */}
        <button
          onClick={onPause}
          className="ml-auto inline-flex items-center gap-1 rounded-full border border-zinc-300 px-2 py-1 text-xs text-zinc-600 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-400 dark:hover:bg-zinc-800"
        >
          <Square className="h-3 w-3" /> 停止生成
        </button>
      </div>
    </Message>
  )
}

// =============================================================================
// 主组件：ChatPane
// =============================================================================

/**
 * forwardRef 说明：
 *   正常情况下，父组件无法访问子组件内部的 DOM 节点或方法。
 *   forwardRef 允许父组件（AIAssistantUI）传入一个 ref 给 ChatPane，
 *   ChatPane 再通过 useImperativeHandle 控制 ref 暴露什么内容。
 *   这里暴露的是 insertTemplate 方法，供父组件在点击模板时注入文字。
 */
const ChatPane = forwardRef(function ChatPane(
  {
    // ── 会话数据 ────────────────────────────────────────────────────────────
    conversation,       // 当前会话对象 { id, title, messages[], updatedAt, ... }

    // ── 消息操作回调（由父组件传入，ChatPane 只负责调用）────────────────────
    onSend,             // (text: string) => void  用户点击发送时调用
    onEditMessage,      // (messageId, newContent) => void  保存编辑后的消息内容
    onResendMessage,    // (messageId) => void  重新触发某条消息的 AI 回复

    // ── AI 生成状态 ──────────────────────────────────────────────────────────
    isThinking,         // boolean  true 时显示思考动画，禁用输入框
    onPauseThinking,    // () => void  点击"停止生成"时调用

    // ── 模型选择 ──────────────────────────────────────────────────────────────
    models,             // 可用模型列表 [{ id, displayName, ... }]
    selectedModelId,    // 当前选中模型的 ID（字符串）
    onSelectModel,      // (modelId) => void  切换模型时调用

    // ── 知识库配置 ────────────────────────────────────────────────────────────
    useKb,              // boolean  是否启用知识库增强检索（RAG）
    setUseKb,           // (boolean) => void
    kbs,                // 可用的知识库列表
    selectedKbIds,      // 已选知识库 ID 数组
    setSelectedKbIds,   // (ids[]) => void

    // ── 其它配置 ──────────────────────────────────────────────────────────────
    contextSize,        // number  发给 AI 的历史轮数（上下文窗口大小）
    setContextSize,     // (number) => void
    user,               // 当前登录用户对象（用于显示头像、名字缩写）
    onOpenCitations,    // (citations, idx?) => void  点击引用来源时打开详情弹窗
  },
  ref,  // 由 forwardRef 注入：父组件创建的 ref 对象
) {

  // ── 消息内联编辑状态 ────────────────────────────────────────────────────────
  // editingId：记录当前正在编辑的消息 ID，null 表示没有消息处于编辑状态
  const [editingId, setEditingId] = useState(null)
  // draft：编辑框里的临时文本，只有点"保存"后才提交到实际消息数据
  const [draft, setDraft] = useState("")

  // ── DOM 引用（Refs）──────────────────────────────────────────────────────────
  // useRef(initialValue) 返回 { current: initialValue }。
  // 与 useState 的区别：修改 .current 不会触发重新渲染，
  // 因此适合存储 DOM 节点引用、定时器 ID、标志位等"不需要触发渲染"的值。

  // composerRef：指向 Composer 输入框组件，用于调用其内部 insertTemplate 方法
  const composerRef = useRef(null)

  // scrollAreaRef：指向消息列表的可滚动 <div> 容器，
  // 用于在 onScroll 事件中读取滚动位置（scrollTop、scrollHeight、clientHeight）
  const scrollAreaRef = useRef(null)

  // endRef：指向消息列表末尾的一个空占位 <div>（"哨兵"元素）。
  // 调用 endRef.current.scrollIntoView() 时，浏览器会将视口滚动到该元素处，
  // 实现"滚到底部"效果，比手动计算 scrollTop 更简洁可靠。
  const endRef = useRef(null)

  // isNearBottomRef：标志位，记录用户当前是否位于消息列表底部附近（120px 以内）。
  // 选用 ref 而非 state 的原因：
  //   - 这个值仅在 scroll 事件和 useEffect 中读写，不需要驱动界面更新
  //   - 如果用 state，每次滚动都会触发重渲染，造成不必要的性能开销
  const isNearBottomRef = useRef(true)  // 初始 true：组件刚挂载时默认视为在底部

  // ── 当前选中的模型对象 ─────────────────────────────────────────────────────
  // useSelectedModel 是一个自定义 Hook，从 models 列表中找到 selectedModelId 对应的对象
  const { selectedModel } = useSelectedModel(models, selectedModelId)

  // ── 从会话中取消息列表 ──────────────────────────────────────────────────────
  // ?. 是"可选链"：如果 conversation 是 undefined/null，不报错，直接返回 undefined
  // Array.isArray 是防御性检查：确保 messages 字段真的是数组再使用
  const messages = Array.isArray(conversation?.messages) ? conversation.messages : []
  const count = messages.length || conversation?.messageCount || 0

  // ── 顶部状态标签（模型 / 知识库 / 上下文）────────────────────────────────
  const tags = [
    // 显示模型名，按优先级依次 fallback
    selectedModel?.displayName || selectedModel?.modelId || selectedModel?.id || "未选择模型",
    // 知识库状态的文案
    useKb ? (selectedKbIds?.length ? `已选 ${selectedKbIds.length} 个知识库` : "已启用全部知识库") : "知识库已关闭",
    `上下文 ${contextSize} 轮`,
  ]

  // ── 向父组件暴露的方法 ─────────────────────────────────────────────────────
  // useImperativeHandle(ref, createHandle, deps)：
  //   定义父组件通过 chatPaneRef.current 能访问哪些东西。
  //   这里只暴露 insertTemplate 一个方法，父组件无法访问组件内部其他状态。
  useImperativeHandle(
    ref,
    () => ({
      // 父组件调用 chatPaneRef.current.insertTemplate(text) 时执行此函数
      insertTemplate: (templateContent) => {
        // 再转发给 Composer 组件的同名方法
        composerRef.current?.insertTemplate(templateContent)
      },
    }),
    [],  // 空依赖数组 = 只在组件挂载时创建一次，不随状态变化重建
  )

  // ── 滚动位置追踪 ───────────────────────────────────────────────────────────
  /**
   * handleScrollArea — 消息容器的 onScroll 事件处理器
   *
   * 每当用户手动滚动消息列表时触发。
   * 计算公式：距底部距离 = 总内容高度 - 已滚动高度 - 可见区域高度
   *
   *  ┌─────────────────────┐  ← 顶部（scrollTop = 0）
   *  │      不可见内容      │
   *  │   （已滚过的部分）   │  高度 = scrollTop
   *  ├─────────────────────┤  ← 视口顶部
   *  │                     │
   *  │      可见内容        │  高度 = clientHeight
   *  │                     │
   *  ├─────────────────────┤  ← 视口底部
   *  │      不可见内容      │
   *  │   （未滚到的部分）   │  高度 = scrollHeight - scrollTop - clientHeight
   *  └─────────────────────┘  ← 底部（scrollHeight）
   *
   * 如果"未滚到的部分"< 120px，认为用户"在底部"，允许自动跟随。
   * 如果用户主动向上翻，这个距离变大，isNearBottomRef 变 false，
   * 后续新消息到来时不会强制把用户拉回底部（不打断阅读）。
   */
  function handleScrollArea() {
    const el = scrollAreaRef.current
    if (!el) return  // ref 还没挂载时直接跳过，避免报错
    isNearBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 120
  }

  // ── 自动滚底逻辑 ───────────────────────────────────────────────────────────
  /**
   * useEffect(fn, deps)：
   *   React 在每次渲染完成后检查 deps 数组，
   *   如果数组中有任意值与上次渲染相比发生了变化，就执行 fn。
   *
   * 监听的三个依赖：
   *   messages.length  有新消息加入时触发
   *   isThinking       思考动画出现/消失时触发（动画本身也占高度，需要滚动跟随）
   *   lastMessage?.id  最后一条消息的 ID 变化（处理消息内容更新但数量不变的边缘情况）
   *
   * 滚动策略（平衡"自动跟随"和"不打扰用户"）：
   *   - 用户刚发出消息 → 无条件立即滚到底（让用户确认自己的消息已发出）
   *   - AI 回复到来 / thinking 变化 → 仅在用户位于底部附近时才跟随
   *     （防止用户正在上翻查看历史时被强行拉回底部）
   */
  const lastMessage = messages.at(-1)  // Array.at(-1) 等同于 arr[arr.length - 1]，更简洁
  useEffect(() => {
    if (!endRef.current) return  // 哨兵 div 还没挂载时跳过

    if (lastMessage?.role === "user") {
      // 用户刚发送了消息：强制立即滚到底
      // behavior: "instant" 无动画，立即跳转（此时用户主动触发，不需要过渡）
      endRef.current.scrollIntoView({ behavior: "instant" })
      // 重置标志为"在底部"，确保后续 AI 回复也会自动跟随
      isNearBottomRef.current = true
      return
    }

    // AI 回复更新或 thinking 状态切换：仅在用户位于底部附近时才自动滚
    if (isNearBottomRef.current) {
      // behavior: "smooth" 平滑滚动，视觉体验更自然（AI 持续输出时不会产生卡顿感）
      endRef.current.scrollIntoView({ behavior: "smooth" })
    }
  }, [messages.length, isThinking, lastMessage?.id])

  // =============================================================================
  // 空状态：未选中任何会话时的占位界面
  // =============================================================================
  if (!conversation) {
    return (
      <div className="flex h-full min-h-0 flex-1 flex-col">
        <div className="flex-1 px-4 py-6 sm:px-8">
          <div className="rounded-2xl border border-dashed border-zinc-300 p-8 text-sm text-zinc-500 dark:border-zinc-700 dark:text-zinc-400">
            新建或选择一个会话后开始对话。
          </div>
        </div>
        {/* 即使没有选中会话，输入框仍然可用；发送时会自动创建新会话 */}
        <Composer
          ref={composerRef}
          onSend={async (text) => {
            if (!text.trim()) return
            await onSend?.(text)
          }}
          busy={isThinking}
          models={models}
          selectedModelId={selectedModelId}
          onSelectModel={onSelectModel}
          useKb={useKb}
          setUseKb={setUseKb}
          kbs={kbs}
          selectedKbIds={selectedKbIds}
          setSelectedKbIds={setSelectedKbIds}
          contextSize={contextSize}
          setContextSize={setContextSize}
        />
      </div>
    )
  }

  // =============================================================================
  // 消息编辑操作函数
  // =============================================================================

  /** 进入编辑模式：记录正在编辑的消息 ID，并把原文复制到草稿区 */
  function startEdit(message) {
    setEditingId(message.id)
    setDraft(message.content)
  }

  /** 取消编辑：清空编辑状态，草稿直接丢弃 */
  function cancelEdit() {
    setEditingId(null)
    setDraft("")
  }

  /** 仅保存：把草稿写入消息内容，但不重新触发 AI 回复 */
  function saveEdit() {
    if (!editingId) return
    onEditMessage?.(editingId, draft)
    cancelEdit()
  }

  /** 保存并重发：写入草稿内容 + 触发 AI 重新生成回复 */
  function saveAndResend() {
    if (!editingId) return
    onEditMessage?.(editingId, draft)
    onResendMessage?.(editingId)
    cancelEdit()
  }

  // =============================================================================
  // 主渲染
  // =============================================================================
  return (
    // 外层容器：flex 纵向布局，撑满父容器高度
    // min-h-0：flex 子元素的常见修复，防止内容溢出 flex 容器
    <div className="flex h-full min-h-0 flex-1 flex-col">

      {/*
        ── 消息滚动容器 ──
        ref={scrollAreaRef}        把这个 div 挂到 ref，供 handleScrollArea 读取尺寸
        onScroll={handleScrollArea} 用户每次滚动时更新 isNearBottomRef
        overflow-y-auto            内容超出高度时自动显示垂直滚动条
        flex-1                     占据 Composer 以外的全部剩余高度
      */}
      <div ref={scrollAreaRef} onScroll={handleScrollArea} className="flex-1 space-y-5 overflow-y-auto px-4 py-6 sm:px-8">

        {/* 会话标题 */}
        <div className="mb-2 text-3xl font-serif tracking-tight sm:text-4xl md:text-5xl">
          <span className="block leading-[1.05] font-sans text-2xl">{conversation.title}</span>
        </div>

        {/* 元信息行：更新时间 · 消息数 */}
        <div className="mb-4 text-sm text-zinc-500 dark:text-zinc-400">
          更新于 {formatConvTime(conversation.updatedAt)} · {formatMessageCount(count)}
        </div>

        {/* 状态标签行（模型 / 知识库 / 上下文轮数） */}
        <div className="mb-6 flex flex-wrap gap-2 border-b border-zinc-200 pb-5 dark:border-zinc-800">
          {tags.map((tag) => (
            <span key={tag} className="inline-flex items-center rounded-full border border-zinc-200 px-3 py-1 text-xs text-zinc-700 dark:border-zinc-800 dark:text-zinc-200">
              {tag}
            </span>
          ))}
        </div>

        {/* 消息列表区域 */}
        {messages.length === 0 ? (
          // 空会话占位
          <div className="rounded-xl border border-dashed border-zinc-300 p-6 text-sm text-zinc-500 dark:border-zinc-700 dark:text-zinc-400">
            还没有消息，发一句话开始吧。
          </div>
        ) : (
          // React Fragment（<>...</>）：
          // 包裹多个兄弟元素，但不在 DOM 中生成额外节点
          <>
            {messages.map((message) => {
              const isUser = message.role === "user"

              // 头像文字：取名字前两个字符并转大写
              // 用户：显示名 → 用户名 → 全局文案 "我"
              // AI：模型标签 → 模型 ID → 全局文案 "AI"
              const avatarLabel = isUser
                ? String(user?.displayName || user?.username || appCopy.meLabel).slice(0, 2).toUpperCase()
                : String(message.modelLabel || message.model || appCopy.aiLabel).slice(0, 2).toUpperCase()

              return (
                // key={message.id}：列表渲染必须提供唯一 key，
                // React 用 key 做虚拟 DOM diff，判断元素是新增/删除/移动了
                <div key={message.id} className="space-y-2">

                  {/* 编辑模式 vs 正常显示 */}
                  {editingId === message.id ? (

                    // ── 编辑状态：显示 textarea + 操作按钮 ──
                    <div className={cls("rounded-2xl border p-2", "border-zinc-200 dark:border-zinc-800")}>
                      {/* 受控 textarea：value 绑定 draft，onChange 更新 draft */}
                      <textarea value={draft} onChange={(e) => setDraft(e.target.value)} className="w-full resize-y rounded-xl bg-transparent p-2 text-sm outline-none" rows={3} />
                      <div className="mt-2 flex items-center gap-2">
                        <button onClick={saveEdit} className="inline-flex items-center gap-1 rounded-full bg-zinc-900 px-3 py-1.5 text-xs text-white dark:bg-white dark:text-zinc-900">
                          <Check className="h-3.5 w-3.5" /> 保存
                        </button>
                        <button onClick={saveAndResend} className="inline-flex items-center gap-1 rounded-full border px-3 py-1.5 text-xs">
                          <RefreshCw className="h-3.5 w-3.5" /> 保存并重发
                        </button>
                        <button onClick={cancelEdit} className="inline-flex items-center gap-1 rounded-full px-3 py-1.5 text-xs">
                          <X className="h-3.5 w-3.5" /> 取消
                        </button>
                      </div>
                    </div>

                  ) : (

                    // ── 正常显示状态 ──
                    <Message role={message.role} avatarLabel={avatarLabel} avatarImage={isUser ? user?.avatarDataUrl : ""}>

                      {/* AI 消息顶部显示模型名（用户消息不显示） */}
                      {!isUser ? (
                        <div className="mb-2 text-xs font-medium uppercase tracking-[0.18em] text-zinc-400">
                          {message.modelLabel || message.model || appCopy.assistantLabel}
                        </div>
                      ) : null}

                      {/* 消息正文 */}
                      {isUser ? (
                        // 用户消息：纯文本，保留换行符
                        <div className="whitespace-pre-wrap">{message.content}</div>
                      ) : (
                        // AI 消息：Markdown 渲染，带引用角标
                        <MarkdownRenderer
                          content={message.content}
                          citations={message.citations}
                          // 点击角标 [N] 时打开引用弹窗，并定位到第 idx 条
                          onCitationClick={(idx) => onOpenCitations?.(message.citations, idx)}
                        />
                      )}

                      {/* 引用来源按钮（只有 AI 消息有引用数据时才显示） */}
                      {Array.isArray(message.citations) && message.citations.length > 0 ? (
                        <button
                          className="mt-3 inline-flex items-center gap-2 rounded-full border border-zinc-200 px-3 py-1 text-xs text-zinc-600 hover:bg-zinc-50 dark:border-zinc-700 dark:text-zinc-300 dark:hover:bg-zinc-800"
                          onClick={() => onOpenCitations?.(message.citations)}
                        >
                          <BookOpen className="h-3.5 w-3.5" />
                          {formatCitationCount(message.citations.length)}
                        </button>
                      ) : null}

                      {/* 用户消息底部的操作区（编辑 / 重新发送） */}
                      {message.role === "user" ? (
                        <div className="mt-3 flex gap-2 text-[11px] text-zinc-500">
                          <button className="inline-flex items-center gap-1 hover:underline" onClick={() => startEdit(message)}>
                            <Pencil className="h-3.5 w-3.5" /> 编辑
                          </button>
                          <button className="inline-flex items-center gap-1 hover:underline" onClick={() => onResendMessage?.(message.id)}>
                            <RefreshCw className="h-3.5 w-3.5" /> 重新发送
                          </button>
                        </div>
                      ) : null}
                    </Message>
                  )}
                </div>
              )
            })}

            {/* AI 思考中动画 */}
            {isThinking ? <ThinkingMessage onPause={onPauseThinking} /> : null}

            {/*
              滚动哨兵（Scroll Sentinel）
              这是一个高度为 0 的空占位 div，始终放在消息列表的最末尾。
              通过 endRef.current.scrollIntoView() 可以让浏览器把
              视口滚动到这个元素的位置，从而实现"滚到底部"效果。

              为什么用 scrollIntoView 而不是直接设 scrollTop？
                - 更简洁：不需要手动计算目标位置
                - behavior 参数内置支持平滑动画和即时跳转两种模式
                - 浏览器原生实现，性能更好
            */}
            <div ref={endRef} />
          </>
        )}
      </div>

      {/* 底部输入框 */}
      <Composer
        ref={composerRef}
        onSend={async (text) => {
          if (!text.trim()) return  // 防止发送纯空白内容
          await onSend?.(text)
        }}
        busy={isThinking}  // busy=true 时输入框禁用，防止 AI 还在回复时重复发送
        models={models}
        selectedModelId={selectedModelId}
        onSelectModel={onSelectModel}
        useKb={useKb}
        setUseKb={setUseKb}
        kbs={kbs}
        selectedKbIds={selectedKbIds}
        setSelectedKbIds={setSelectedKbIds}
        contextSize={contextSize}
        setContextSize={setContextSize}
      />
    </div>
  )
})

export default ChatPane
