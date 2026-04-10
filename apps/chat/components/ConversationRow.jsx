/**
 * ConversationRow.jsx — 侧边栏中的单条会话行
 *
 * 职责：
 *   展示一条会话的标题、时间、消息数，以及：
 *   - 点击整行 → 切换到该会话
 *   - 悬停时显示"···"更多操作按钮
 *   - 点击更多按钮 → 下拉菜单（置顶 / 重命名 / 删除）
 *   - 桌面端悬停 → 在行右侧显示内容预览气泡
 *
 * 已知修复：
 *   预览气泡原来用 hidden/md:group-hover:block（display 切换）触发了闪烁。
 *   现已改为 invisible/md:group-hover:visible（visibility 切换），
 *   避免 display 变化引起 backdrop-blur 重合成，消除侧边栏列表抖动。
 */

"use client"

import { useState, useRef, useEffect } from "react"
// useState   管理组件状态（这里用于控制下拉菜单的显示/隐藏）
// useRef     持久引用，不触发重渲染；这里用于指向菜单 DOM 节点，实现点击外部关闭
// useEffect  副作用；这里用于挂载/卸载"全局点击事件监听"，实现点外关闭菜单

import { MoreHorizontal, Pin, Edit3, Trash2 } from "lucide-react"
// MoreHorizontal → 三个横向点（更多操作）
// Pin           → 图钉（置顶）
// Edit3         → 铅笔（重命名）
// Trash2        → 垃圾桶（删除）

import { cls, formatConvTime } from "./utils"
import { motion, AnimatePresence } from "framer-motion"
// framer-motion 是一个 React 动画库
// motion.div    → 带动画能力的普通 <div>
// AnimatePresence → 在元素挂载/卸载时执行进入/退出动画

import { formatMessageCount } from "../lib/copy"

/**
 * Props 说明：
 *   data       会话数据对象 { id, title, pinned, updatedAt, preview, messages, messageCount }
 *   active     boolean  当前行是否被选中（高亮显示）
 *   onSelect   () => void  点击行时调用（切换到该会话）
 *   onTogglePin () => void  切换置顶状态
 *   onDelete   (id) => void  删除会话
 *   onRename   (id, newName) => void  重命名会话
 *   showMeta   boolean  是否显示消息数等元信息（"最近会话"区域显示，"置顶"区域不显示）
 */
export default function ConversationRow({ data, active, onSelect, onTogglePin, onDelete, onRename, showMeta }) {

  // showMenu：控制右键菜单（置顶/重命名/删除）的显示状态
  const [showMenu, setShowMenu] = useState(false)

  // menuRef：指向菜单容器的 DOM 节点，用于判断点击是否发生在菜单外
  const menuRef = useRef(null)

  // 消息数：优先用实际加载的 messages 数组长度，未加载时用 messageCount 字段
  const count = Array.isArray(data.messages) ? data.messages.length : data.messageCount

  // ── 点击菜单外部自动关闭 ─────────────────────────────────────────────────
  /**
   * useEffect 的清理机制：
   *   当 showMenu 变为 true 时，在 document 上添加 mousedown 监听器。
   *   如果点击的元素不在 menuRef 包含的 DOM 树内，则关闭菜单。
   *   当 showMenu 变为 false 或组件卸载时，useEffect 返回的清理函数会执行，
   *   移除监听器，防止内存泄漏和重复绑定。
   *
   *   为什么用 mousedown 而不是 click？
   *   mousedown 比 click 更早触发，可以抢先关闭菜单，
   *   避免 click 事件被菜单子元素处理后再冒泡导致的时序问题。
   */
  useEffect(() => {
    const handleClickOutside = (event) => {
      // menuRef.current.contains(event.target)：
      // 检查被点击的元素是否是菜单 div 本身或其后代节点
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setShowMenu(false)
      }
    }

    if (showMenu) {
      // 只在菜单打开时绑定监听，避免每次渲染都消耗事件监听资源
      document.addEventListener("mousedown", handleClickOutside)
    }

    // 清理函数：组件卸载或下次 effect 执行前，移除上次绑定的监听器
    return () => {
      document.removeEventListener("mousedown", handleClickOutside)
    }
  }, [showMenu])  // showMenu 变化时才重新执行

  // ── 菜单操作处理函数 ─────────────────────────────────────────────────────
  /**
   * e.stopPropagation() — 阻止事件冒泡
   *
   * DOM 事件会从触发元素向上"冒泡"到所有父元素。
   * 菜单按钮在行的 onClick 区域内，如果不阻止冒泡：
   *   点击"删除" → handleDelete() 执行 ✓
   *                冒泡到父 div → onSelect() 也执行 ✗（意外切换了会话）
   * 加上 stopPropagation，事件在按钮处停止，不再向上传递。
   */

  const handlePin = (e) => {
    e.stopPropagation()
    onTogglePin?.()  // ?. 可选调用：onTogglePin 未传入时不报错
    setShowMenu(false)
  }

  const handleRename = (e) => {
    e.stopPropagation()
    // prompt() 是浏览器原生弹窗，第二个参数是输入框的初始值
    const newName = prompt(`将会话"${data.title}"重命名为：`, data.title)
    if (newName && newName.trim() && newName !== data.title) {
      onRename?.(data.id, newName.trim())
    }
    setShowMenu(false)
  }

  const handleDelete = (e) => {
    e.stopPropagation()
    // confirm() 是浏览器原生确认弹窗，返回 true/false
    if (confirm(`确定要删除会话"${data.title}"吗？`)) {
      onDelete?.(data.id)
    }
    setShowMenu(false)
  }

  // ==========================================================================
  // 渲染
  // ==========================================================================
  return (
    /*
      外层容器：group relative
        group    → Tailwind"分组"标记。子元素可以用 group-hover: 前缀
                   响应这个父容器的 hover 状态（悬停父元素时改变子元素样式）。
        relative → 为绝对定位的子元素（预览气泡、下拉菜单）提供定位基准。
    */
    <div className="group relative">

      {/*
        行主体：整行可点击区域
          role="button" + tabIndex={0}：语义化 + 键盘可聚焦（无障碍支持）
          onKeyDown：支持 Enter/空格键触发选择
      */}
      <div
        role="button"
        tabIndex={0}
        onClick={onSelect}
        onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") onSelect?.() }}
        className={cls(
          // 负边距 + 加宽：让 hover 背景覆盖到容器边缘的 padding，视觉更整洁
          "-mx-1 flex w-[calc(100%+8px)] items-center gap-2 rounded-lg px-2 py-2 text-left cursor-pointer",
          // active 时高亮背景，未选中时仅 hover 时变色
          active
            ? "bg-zinc-100 text-zinc-900 dark:bg-zinc-800/60 dark:text-zinc-100"
            : "hover:bg-zinc-100 dark:hover:bg-zinc-800",
        )}
        title={data.title}
      >

        {/* 文本内容区：min-w-0 防止长标题撑破 flex 布局 */}
        <div className="min-w-0 flex-1">

          {/* 标题行 */}
          <div className="flex items-center gap-2">
            {/* 仅置顶会话显示图钉图标 */}
            {data.pinned && <Pin className="h-3 w-3 shrink-0 text-zinc-500 dark:text-zinc-400" />}
            {/* truncate：超出宽度时用"…"截断 */}
            <span className="truncate text-sm font-medium tracking-tight">{data.title}</span>
            <span className="shrink-0 text-[11px] text-zinc-500 dark:text-zinc-400">{formatConvTime(data.updatedAt)}</span>
          </div>

          {/* 元信息：消息数（showMeta=true 时才显示） */}
          {showMeta && <div className="mt-0.5 text-[11px] text-zinc-500 dark:text-zinc-400">{formatMessageCount(count)}</div>}
        </div>

        {/* 更多操作按钮 + 下拉菜单区域 */}
        <div className="relative" ref={menuRef}>

          {/*
            "···"更多按钮
              opacity-0 group-hover:opacity-100 transition：
                默认不可见，鼠标悬停在整行（group）时淡入显示。
                transition 让透明度变化有动画效果，不是突变。
          */}
          <button
            onClick={(e) => {
              e.stopPropagation()
              setShowMenu(!showMenu)
            }}
            className="rounded-md p-1 text-zinc-500 opacity-0 transition group-hover:opacity-100 hover:bg-zinc-200/50 dark:text-zinc-300 dark:hover:bg-zinc-700/60"
            aria-label="会话操作"
          >
            <MoreHorizontal className="h-4 w-4" />
          </button>

          {/*
            AnimatePresence：
              监控子元素的挂载/卸载，在元素从 DOM 移除时执行 exit 动画。
              如果没有 AnimatePresence 包裹，motion.div 卸载时不会播放退出动画。
          */}
          <AnimatePresence>
            {showMenu && (
              /*
                motion.div 动画：
                  initial  挂载时的初始状态（透明 + 略微缩小）
                  animate  动画目标状态（完全不透明 + 原尺寸）
                  exit     卸载时的退出状态（回到 initial 状态）
                framer-motion 自动在这三个状态间插值，生成流畅过渡。
              */
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                exit={{ opacity: 0, scale: 0.95 }}
                className="absolute right-0 top-full mt-1 w-36 rounded-lg border border-zinc-200 bg-white py-1 shadow-lg dark:border-zinc-800 dark:bg-zinc-900 z-[100]"
              >
                {/* 置顶 / 取消置顶 */}
                <button
                  onClick={handlePin}
                  className="w-full px-3 py-1.5 text-left text-xs hover:bg-zinc-100 dark:hover:bg-zinc-800 flex items-center gap-2"
                >
                  {data.pinned ? (
                    <><Pin className="h-3 w-3" />取消置顶</>
                  ) : (
                    <><Pin className="h-3 w-3" />置顶</>
                  )}
                </button>

                {/* 重命名 */}
                <button
                  onClick={handleRename}
                  className="w-full px-3 py-1.5 text-left text-xs hover:bg-zinc-100 dark:hover:bg-zinc-800 flex items-center gap-2"
                >
                  <Edit3 className="h-3 w-3" />
                  重命名
                </button>

                {/* 删除（红色文字，提示这是危险操作） */}
                <button
                  onClick={handleDelete}
                  className="w-full px-3 py-1.5 text-left text-xs text-red-600 hover:bg-zinc-100 dark:hover:bg-zinc-800 flex items-center gap-2"
                >
                  <Trash2 className="h-3 w-3" />
                  删除
                </button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>

    </div>
  )
}
