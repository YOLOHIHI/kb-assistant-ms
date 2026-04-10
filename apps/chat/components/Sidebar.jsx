"use client"
import { motion, AnimatePresence } from "framer-motion"
import {
  PanelLeftClose,
  PanelLeftOpen,
  SearchIcon,
  Plus,
  Star,
  Clock,
  Settings,
  Asterisk,
  Database,
} from "lucide-react"
import SidebarSection from "./SidebarSection"
import ConversationRow from "./ConversationRow"
import ThemeToggle from "./ThemeToggle"
import SearchModal from "./SearchModal"
import SettingsPopover from "./SettingsPopover"
import KnowledgeBaseModal from "./KnowledgeBaseModal"
import { cls } from "./utils"
import { useState } from "react"
import { displayNameForUser, initials } from "../lib/kb-api"
import { appCopy, formatUserRole } from "../lib/copy"

export default function Sidebar({
  open,
  onClose,
  theme,
  setTheme,
  collapsed,
  setCollapsed,
  conversations,
  pinned,
  recent,
  selectedId,
  onSelect,
  togglePin,
  query,
  setQuery,
  searchRef,
  renameConversation = () => {},
  deleteConversation = () => {},
  createNewChat,
  sidebarCollapsed = false,
  setSidebarCollapsed = () => {},
  user = null,
  onOpenProfile = () => {},
  onOpenSupport = () => {},
  onLogout = () => {},
  knowledgeBaseProps = {},
}) {
  const [showSearchModal, setShowSearchModal] = useState(false)
  const [showKbModal, setShowKbModal] = useState(false)

  const handleSearchClick = () => {
    setShowSearchModal(true)
  }

  const handleNewChatClick = () => {
    createNewChat()
  }

  return (
    <>
      <AnimatePresence>
        {open && (
          <motion.div
            key="overlay"
            initial={{ opacity: 0 }}
            animate={{ opacity: 0.5 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-40 bg-black/60 md:hidden"
            onClick={onClose}
          />
        )}
      </AnimatePresence>

      <motion.aside
        animate={{ width: sidebarCollapsed ? 64 : 320 }}
        transition={{ type: "spring", stiffness: 260, damping: 28 }}
        className={cls(
          "z-50 flex h-full shrink-0 flex-col border-r border-zinc-200/60 bg-white dark:border-zinc-800 dark:bg-zinc-900 overflow-hidden",
          "fixed inset-y-0 left-0 md:static md:translate-x-0",
          !open && "hidden md:flex",
        )}
        style={{ minWidth: sidebarCollapsed ? 64 : 320 }}
      >
        {/* Collapsed icon-only strip */}
        <AnimatePresence mode="wait">
        {sidebarCollapsed && (
          <motion.div
            key="collapsed-content"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="flex flex-col h-full w-full"
          >
            <div className="flex items-center justify-center border-b border-zinc-200/60 px-3 py-3 dark:border-zinc-800">
              <button
                onClick={() => setSidebarCollapsed(false)}
                className="rounded-xl p-2 hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800"
                aria-label="展开侧边栏"
                title="展开侧边栏"
              >
                <PanelLeftOpen className="h-5 w-5" />
              </button>
            </div>

            <div className="flex flex-1 flex-col items-center gap-2 pt-4">
              <button
                onClick={handleNewChatClick}
                className="rounded-xl p-2.5 hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800 transition-colors"
                title={appCopy.newChatTitle}
              >
                <Plus className="h-5 w-5" />
              </button>
              <button
                onClick={handleSearchClick}
                className="rounded-xl p-2.5 hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800 transition-colors"
                title="搜索会话"
              >
                <SearchIcon className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-auto flex flex-col items-center gap-2 pb-4">
              <button
                onClick={() => setShowKbModal(true)}
                className="rounded-xl p-2.5 hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800 transition-colors"
                title="知识库"
              >
                <Database className="h-5 w-5" />
              </button>
              <SettingsPopover
                user={user}
                onOpenProfile={onOpenProfile}
                onOpenKnowledgeBase={() => setShowKbModal(true)}
                onOpenSupport={onOpenSupport}
                onLogout={onLogout}
              >
                <button
                  className="rounded-xl p-2.5 hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800 transition-colors"
                  title="设置"
                >
                  <Settings className="h-5 w-5" />
                </button>
              </SettingsPopover>
            </div>
          </motion.div>
        )}
        </AnimatePresence>

        {/* Full expanded sidebar content */}
        <AnimatePresence mode="wait">
        {!sidebarCollapsed && (
          <motion.div
            key="expanded-content"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="flex flex-col h-full w-full"
          >
            <div className="flex items-center gap-2 border-b border-zinc-200/60 px-3 py-3 dark:border-zinc-800">
              <div className="flex items-center gap-2">
                <div className="grid h-8 w-8 place-items-center rounded-xl bg-gradient-to-br from-blue-500 to-indigo-500 text-white shadow-sm dark:from-zinc-200 dark:to-zinc-300 dark:text-zinc-900">
                  <Asterisk className="h-4 w-4" />
                </div>
                <div className="text-sm font-semibold tracking-tight">{appCopy.brandName}</div>
              </div>
              <div className="ml-auto flex items-center gap-1">
                <button
                  onClick={() => setSidebarCollapsed(true)}
                  className="hidden md:block rounded-xl p-2 hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800"
                  aria-label="收起侧边栏"
                  title="收起侧边栏"
                >
                  <PanelLeftClose className="h-5 w-5" />
                </button>

                <button
                  onClick={onClose}
                  className="md:hidden rounded-xl p-2 hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800"
                  aria-label="关闭侧边栏"
                >
                  <PanelLeftClose className="h-5 w-5" />
                </button>
              </div>
            </div>

            <div className="px-3 pt-3">
              <label htmlFor="search" className="sr-only">
                搜索会话
              </label>
              <div className="relative">
                <SearchIcon className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-400" />
                <input
                  id="search"
                  ref={searchRef}
                  type="text"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="搜索会话…"
                  onClick={() => setShowSearchModal(true)}
                  onFocus={() => setShowSearchModal(true)}
                  className="w-full rounded-full border border-zinc-200 bg-white py-2 pl-9 pr-3 text-sm outline-none ring-0 placeholder:text-zinc-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 dark:border-zinc-800 dark:bg-zinc-950/50"
                />
              </div>
            </div>

            <div className="px-3 pt-3">
              <button
                onClick={createNewChat}
                className="flex w-full items-center justify-center gap-2 rounded-full bg-zinc-900 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-zinc-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:bg-white dark:text-zinc-900"
                title="新建对话（Ctrl/Cmd+N）"
              >
                <Plus className="h-4 w-4" /> 发起新对话
              </button>
            </div>

            <nav className="mt-4 flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-2 pb-4">
              <SidebarSection
                icon={<Star className="h-4 w-4" />}
                title="置顶会话"
                collapsed={collapsed.pinned}
                onToggle={() => setCollapsed((s) => ({ ...s, pinned: !s.pinned }))}
              >
                {pinned.length === 0 ? (
                  <div className="select-none rounded-lg border border-dashed border-zinc-200 px-3 py-3 text-center text-xs text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">
                    把重要会话置顶，后续访问会更快。
                  </div>
                ) : (
                  pinned.map((c) => (
                    <ConversationRow
                      key={c.id}
                      data={c}
                      active={c.id === selectedId}
                      onSelect={() => onSelect(c.id)}
                      onTogglePin={() => togglePin(c.id)}
                      onRename={renameConversation}
                      onDelete={deleteConversation}
                    />
                  ))
                )}
              </SidebarSection>

              <SidebarSection
                icon={<Clock className="h-4 w-4" />}
                title="最近会话"
                collapsed={collapsed.recent}
                onToggle={() => setCollapsed((s) => ({ ...s, recent: !s.recent }))}
              >
                {recent.length === 0 ? (
                  <div className="select-none rounded-lg border border-dashed border-zinc-200 px-3 py-3 text-center text-xs text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">
                    还没有会话，先发起一个新对话吧。
                  </div>
                ) : (
                  recent.map((c) => (
                    <ConversationRow
                      key={c.id}
                      data={c}
                      active={c.id === selectedId}
                      onSelect={() => onSelect(c.id)}
                      onTogglePin={() => togglePin(c.id)}
                      onRename={renameConversation}
                      onDelete={deleteConversation}
                      showMeta
                    />
                  ))
                )}
              </SidebarSection>
            </nav>

            <div className="mt-auto border-t border-zinc-200/60 px-3 py-3 dark:border-zinc-800">
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setShowKbModal(true)}
                  className="inline-flex items-center gap-2 rounded-lg px-2 py-2 text-sm text-zinc-600 hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-zinc-300 dark:hover:bg-zinc-800 transition-colors"
                  title="知识库管理"
                >
                  <Database className="h-4 w-4" /> 知识库
                </button>
                <SettingsPopover
                  user={user}
                  onOpenProfile={onOpenProfile}
                  onOpenKnowledgeBase={() => setShowKbModal(true)}
                  onOpenSupport={onOpenSupport}
                  onLogout={onLogout}
                >
                  <button className="inline-flex items-center gap-2 rounded-lg px-2 py-2 text-sm hover:bg-zinc-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-zinc-800">
                    <Settings className="h-4 w-4" /> 设置
                  </button>
                </SettingsPopover>
                <div className="ml-auto">
                  <ThemeToggle theme={theme} setTheme={setTheme} />
                </div>
              </div>
              <div className="mt-2 flex items-center gap-2 rounded-xl bg-zinc-50 p-2 dark:bg-zinc-800/60">
                <div className="grid h-8 w-8 shrink-0 place-items-center rounded-full overflow-hidden bg-zinc-900 dark:bg-white">
                  {user?.avatarDataUrl ? (
                    <img src={user.avatarDataUrl} alt={displayNameForUser(user)} className="h-full w-full object-cover" />
                  ) : (
                    <span className="text-xs font-bold text-white dark:text-zinc-900">{initials(displayNameForUser(user))}</span>
                  )}
                </div>
                <div className="min-w-0">
                  <div className="truncate text-sm font-medium">{displayNameForUser(user)}</div>
                  <div className="truncate text-xs text-zinc-500 dark:text-zinc-400">
                    {formatUserRole(user?.role)} {appCopy.workspaceLabel}
                  </div>
                </div>
              </div>
            </div>
          </motion.div>
        )}
        </AnimatePresence>
      </motion.aside>

      <KnowledgeBaseModal isOpen={showKbModal} onClose={() => setShowKbModal(false)} {...knowledgeBaseProps} />

      <SearchModal
        isOpen={showSearchModal}
        onClose={() => setShowSearchModal(false)}
        conversations={conversations}
        selectedId={selectedId}
        onSelect={onSelect}
        togglePin={togglePin}
        createNewChat={createNewChat}
      />
    </>
  )
}
