/**
 * Message.jsx — 单条消息的气泡外壳
 *
 * 职责：
 *   根据消息角色（user / assistant）决定气泡的布局方向、样式和头像位置。
 *   本组件只处理"外壳"（布局、气泡、头像），消息的具体内容通过 children 传入。
 *
 * 布局规则：
 *   用户消息  → 整行右对齐（justify-end），头像在右侧，深色气泡
 *   AI 消息   → 整行左对齐（justify-start），头像在左侧，浅色气泡带边框
 *
 * 使用示例：
 *   <Message role="user" avatarLabel="张三" avatarImage={user.avatar}>
 *     <div>你好，请问...</div>
 *   </Message>
 */

import { cls } from "./utils"
// cls：拼接 Tailwind 类名的工具函数

/**
 * Props：
 *   role         "user" | "assistant"  决定布局方向和样式
 *   avatarLabel  string  头像无图片时显示的文字（如 "张三" 或 "AI"）
 *   avatarImage  string（可选）  头像图片 URL（data URL 或普通 URL）
 *   children     React 节点  消息正文区域（纯文本 / Markdown 渲染器）
 */
export default function Message({ role, avatarLabel, avatarImage, children }) {
  // 根据 role 决定布局方向
  const isUser = role === "user"

  return (
    /*
      外层 flex 容器：
        gap-3              头像和气泡之间的间距
        justify-end        用户消息：靠右排列（头像在气泡右侧）
        justify-start      AI 消息：靠左排列（头像在气泡左侧）
    */
    <div className={cls("flex gap-3", isUser ? "justify-end" : "justify-start")}>

      {/* AI 头像（左侧，用户消息不显示） */}
      {!isUser && (
        /*
          grid place-items-center：将内容（文字或图片）在 div 内居中
          overflow-hidden rounded-full：圆形头像，超出部分裁剪
          h-7 w-7：头像尺寸 28×28px
        */
        <div className="mt-0.5 grid h-7 w-7 place-items-center overflow-hidden rounded-full bg-zinc-900 text-[10px] font-bold text-white dark:bg-white dark:text-zinc-900">
          {/* 如果有头像图片就显示图片，否则显示文字缩写 */}
          {avatarImage
            ? <img src={avatarImage} alt={avatarLabel || "assistant"} className="h-full w-full object-cover" />
            : avatarLabel || "AI"
          }
        </div>
      )}

      {/*
        消息气泡
          max-w-[80%]：最大宽度限制，防止气泡撑满整行，留出头像空间
          rounded-2xl：大圆角气泡样式
          shadow-sm：轻微投影，增加层次感

          用户消息：深色背景（zinc-900），白色文字
                    深色模式：白色背景，深色文字（反转）
          AI 消息：白色背景 + 细边框，深色文字
                   深色模式：深色背景 + 深色边框
      */}
      <div
        className={cls(
          "max-w-[80%] rounded-2xl px-3 py-2 text-sm shadow-sm",
          isUser
            ? "bg-zinc-900 text-white dark:bg-white dark:text-zinc-900"
            : "border border-zinc-200 bg-white text-zinc-900 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-100",
        )}
      >
        {/* children：消息内容由父组件（ChatPane）传入，可以是纯文本 div 或 MarkdownRenderer */}
        {children}
      </div>

      {/* 用户头像（右侧，AI 消息不显示） */}
      {isUser && (
        <div className="mt-0.5 grid h-7 w-7 place-items-center overflow-hidden rounded-full bg-zinc-900 text-[10px] font-bold text-white dark:bg-white dark:text-zinc-900">
          {avatarImage
            ? <img src={avatarImage} alt={avatarLabel || "user"} className="h-full w-full object-cover" />
            : avatarLabel || "ME"
          }
        </div>
      )}
    </div>
  )
}
