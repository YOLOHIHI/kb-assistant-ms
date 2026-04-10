/**
 * SidebarSection.jsx — 侧边栏的可折叠分区组件
 *
 * 职责：
 *   渲染侧边栏中的一个分组（如"置顶会话"、"最近会话"），包含：
 *   - 分区标题按钮（支持点击折叠/展开，sticky 吸顶，带 backdrop-blur 效果）
 *   - 展开/折叠状态下的内容列表（带高度过渡动画）
 *
 * ⚠️ backdrop-blur 与闪烁的关系（重要）：
 *   标题按钮的 backdrop-blur 会让浏览器为这个元素创建独立的合成图层（Compositing Layer）。
 *   这意味着：每当滚动容器内的任何子元素发生"display 变化"（如原来的预览气泡），
 *   compositor 就需要重新合成这个模糊图层，产生视觉上的一次"闪一下"。
 *   ConversationRow 的预览气泡已从 hidden/block 改为 invisible/visible，
 *   解决了这个合成抖动问题。
 */

import React from "react"
import { AnimatePresence, motion } from "framer-motion"
// AnimatePresence 监控子元素的挂载/卸载，在退出时播放动画
// motion.div      带动画能力的普通 div

import { ChevronDown, ChevronRight } from "lucide-react"
// ChevronDown  → 向下的箭头（展开状态）
// ChevronRight → 向右的箭头（折叠状态）

/**
 * Props：
 *   icon      分区图标（React 节点，如 <Star /> <Clock />）
 *   title     分区标题文字
 *   children  分区内容（ConversationRow 列表等）
 *   collapsed boolean  true 表示当前折叠
 *   onToggle  () => void  点击标题时调用，切换折叠状态
 */
export default function SidebarSection({ icon, title, children, collapsed, onToggle }) {
  return (
    <section>
      {/*
        分区标题按钮

        sticky top-0：在滚动时吸附到滚动容器顶部（不是页面顶部）。
                      滚动时已过去的会话会"滑入"标题下方，标题始终可见。
        z-10：比普通内容的 z-index 高，确保标题浮在列表上方，不被遮挡。
        backdrop-blur：毛玻璃效果。让标题背景模糊显示下方内容，视觉上有"玻璃"的质感。
                       这个属性会创建独立合成图层（见上方警告说明）。
        bg-gradient-to-b from-white to-white/70：
                       从纯白到半透明白的渐变，配合 backdrop-blur 实现
                       "可以隐约看到下方内容"的效果，而不是完全不透明的白色。
        -mx-2 w-[calc(100%+16px)]：
                       负边距 + 加宽，让标题横向覆盖到容器边缘的 padding 区域，
                       避免标题背景与两侧内边距之间出现空隙。
      */}
      <button
        onClick={onToggle}
        className="sticky top-0 z-10 -mx-2 mb-1 flex w-[calc(100%+16px)] items-center gap-2 border-y border-transparent bg-gradient-to-b from-white to-white/70 px-2 py-2 text-[11px] font-semibold tracking-wide text-zinc-500 backdrop-blur hover:text-zinc-700 dark:from-zinc-900 dark:to-zinc-900/70 dark:hover:text-zinc-300"
        aria-expanded={!collapsed}  // 无障碍属性：告知屏幕阅读器当前是展开(true)还是折叠(false)
      >
        {/* 折叠/展开指示箭头 */}
        <span className="mr-1" aria-hidden>
          {/* aria-hidden：对屏幕阅读器隐藏纯装饰性图标，避免读出"图标"干扰 */}
          {collapsed ? <ChevronRight className="h-3.5 w-3.5" /> : <ChevronDown className="h-3.5 w-3.5" />}
        </span>

        {/* 分区图标 + 标题文字 */}
        <span className="flex items-center gap-2">
          <span className="opacity-70" aria-hidden>{icon}</span>
          {title}
        </span>
      </button>

      {/*
        AnimatePresence initial={false}：
          initial={false} 表示组件首次渲染时不播放初始动画（直接显示最终状态）。
          只有后续的切换操作才会触发过渡动画，避免页面刚加载时所有分区同时展开的动画噪音。
      */}
      <AnimatePresence initial={false}>
        {!collapsed && (
          /*
            折叠/展开的高度动画：
              initial  { height: 0, opacity: 0 }  ← 从高度0、透明开始
              animate  { height: "auto", opacity: 1 }  ← 动画到自然高度、完全不透明
              exit     { height: 0, opacity: 0 }  ← 收缩时退回到高度0
              duration: 0.18s 时长，足够感知但不拖沓

            height: "auto" 的特殊性：
              CSS 本身不支持从 height:0 过渡到 height:auto。
              framer-motion 内部通过先获取元素实际高度再设置具体像素值来实现这个效果。
          */
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: "auto", opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="space-y-0.5"  // 子列表项之间的间距
          >
            {children}
          </motion.div>
        )}
      </AnimatePresence>
    </section>
  )
}
