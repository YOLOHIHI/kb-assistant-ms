"use client"

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react"
import Sidebar from "./Sidebar"
import Header from "./Header"
import ChatPane from "./ChatPane"
import LoginModal from "./LoginModal"
import ProfileModal from "./ProfileModal"
import SupportModal from "./SupportModal"
import CitationsModal from "./CitationsModal"
import {
  apiFetch,
  createClientId,
  normalizeKbDocumentsResponse,
  readStored,
  writeStored,
  readTextStorage,
  writeTextStorage,
} from "../lib/kb-api"
import { appCopy } from "../lib/copy"

const KB_SELECTION_VERSION = "2"

const STORAGE_KEYS = {
  theme: "kb_theme",
  sidebarSections: "kb_sidebar_sections",
  sidebarCollapsed: "kb_chat_sidebar_collapsed",
  sessionId: "kb_session",
  pinnedSessions: "kb_pinned_sessions",
  useKb: "kb_use",
  contextSize: "kb_context_size",
  modelIds: "kb_models",
  kbIds: "kb_kbs",
  kbSelectionVersion: "kb_kbs_v",
}

const DEFAULT_SECTION_STATE = {
  pinned: true,
  recent: false,
}

function uniqueStrings(values) {
  const seen = new Set()
  const output = []
  for (const value of values || []) {
    const text = String(value || "").trim()
    if (!text || seen.has(text)) continue
    seen.add(text)
    output.push(text)
  }
  return output
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

function readCsvStorage(key) {
  const raw = readTextStorage(key, "")
  if (!raw) return []
  return uniqueStrings(raw.split(/[\s,，;；]+/))
}

function writeCsvStorage(key, values) {
  const normalized = uniqueStrings(values)
  if (!normalized.length) {
    writeTextStorage(key, "")
    return
  }
  writeTextStorage(key, normalized.join(","))
}

function createEmptyConversation(id, title = appCopy.newChatTitle) {
  const now = new Date().toISOString()
  return {
    id,
    title,
    createdAt: now,
    updatedAt: now,
    messagesLoaded: true,
    messages: [],
    messageCount: 0,
    preview: "",
  }
}

function previewFromMessages(messages) {
  const last = [...(Array.isArray(messages) ? messages : [])]
    .reverse()
    .find((message) => String(message?.content || "").trim())
  return last ? String(last.content).slice(0, 80) : ""
}

function sortConversations(conversations) {
  return [...(Array.isArray(conversations) ? conversations : [])].sort((left, right) => {
    const a = new Date(left.updatedAt || left.createdAt || 0).getTime()
    const b = new Date(right.updatedAt || right.createdAt || 0).getTime()
    return b - a
  })
}

function mergeSessionList(previous, incoming) {
  const previousMap = new Map((previous || []).map((conversation) => [conversation.id, conversation]))
  return sortConversations(
    (incoming || [])
      .map((session) => {
        const id = String(session?.id || session?.sessionId || "")
        if (!id) return null
        const existing = previousMap.get(id)
        const next = {
          id,
          title: String(session?.title || existing?.title || appCopy.newChatTitle),
          createdAt: String(session?.createdAt || existing?.createdAt || new Date().toISOString()),
          updatedAt: String(session?.updatedAt || existing?.updatedAt || session?.createdAt || new Date().toISOString()),
          messagesLoaded: Boolean(existing?.messagesLoaded),
          messages: Array.isArray(existing?.messages) ? existing.messages : [],
          messageCount: existing?.messagesLoaded
            ? (existing?.messages || []).length
            : Number(existing?.messageCount || 0),
          preview: String(existing?.preview || ""),
        }
        if (next.messagesLoaded) {
          next.messageCount = next.messages.length
          next.preview = previewFromMessages(next.messages)
        }
        return next
      })
      .filter(Boolean),
  )
}

function resolveModelLabel(modelSelector, models) {
  const selector = String(modelSelector || "").trim()
  if (!selector) return ""
  const match = (models || []).find((model) => {
    const candidates = [model?.id, model?.modelId, model?.displayName].map((value) => String(value || "").trim())
    return candidates.includes(selector)
  })
  return match?.displayName || match?.modelId || match?.id || selector
}

function messageIdentity(message, index) {
  const role = String(message?.role || "USER").toUpperCase()
  const at = String(message?.at || message?.createdAt || index)
  const content = String(message?.content || "")
  const model = String(message?.model || "")
  return `${role}|${at}|${model}|${content}`
}

function normalizeSessionMessages(sessionId, rawMessages, previousMessages, models) {
  const previousMap = new Map((previousMessages || []).map((message, index) => [messageIdentity(message, index), message]))
  return (Array.isArray(rawMessages) ? rawMessages : []).map((message, index) => {
    const role = String(message?.role || "USER").toUpperCase() === "ASSISTANT" ? "assistant" : "user"
    const existing = previousMap.get(messageIdentity(message, index))
    const model = role === "assistant" ? String(message?.model || existing?.model || "") : ""
    return {
      id: existing?.id || `${sessionId}_${index}_${role}`,
      role,
      content: String(message?.content || ""),
      createdAt: String(message?.at || existing?.createdAt || new Date().toISOString()),
      model,
      modelLabel: role === "assistant" ? resolveModelLabel(model, models) : "",
      citations: Array.isArray(existing?.citations) ? existing.citations : [],
    }
  })
}

function normalizeCitationEntry(citation, index) {
  return {
    id: String(citation?.id || citation?.chunkId || citation?.docId || `citation_${index}`),
    docId: String(citation?.docId || ""),
    filename: String(citation?.filename || ""),
    chunkId: String(citation?.chunkId || ""),
    chunkIndex: Number(citation?.chunkIndex || 0),
    sourceHint: String(citation?.sourceHint || ""),
    snippet: String(citation?.snippet || ""),
    kbId: String(citation?.kbId || ""),
    fullText: String(citation?.fullText || ""),
  }
}

function isGenericNewChatTitle(title) {
  const value = String(title || "").trim().toLowerCase()
  return !value || value === "new chat" || value === appCopy.newChatTitle.toLowerCase() || value === "新会话"
}

export default function AIAssistantUI() {
  const [theme, setTheme] = useState(() => {
    const saved = readTextStorage(STORAGE_KEYS.theme, "")
    if (saved === "dark" || saved === "light") return saved
    if (
      typeof window !== "undefined" &&
      window.matchMedia &&
      window.matchMedia("(prefers-color-scheme: dark)").matches
    ) {
      return "dark"
    }
    return "light"
  })

  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => ({
    ...DEFAULT_SECTION_STATE,
    ...(readStored(STORAGE_KEYS.sidebarSections, {}) || {}),
  }))
  const [sidebarCollapsed, setSidebarCollapsed] = useState(
    () => readTextStorage(STORAGE_KEYS.sidebarCollapsed, "0") === "1",
  )
  const [rawConversations, setRawConversations] = useState([])
  const [selectedId, setSelectedIdState] = useState(() => readTextStorage(STORAGE_KEYS.sessionId, ""))
  const [pinnedSessionIds, setPinnedSessionIds] = useState(() => {
    const stored = readStored(STORAGE_KEYS.pinnedSessions, [])
    return uniqueStrings(Array.isArray(stored) ? stored : [])
  })
  const [query, setQuery] = useState("")
  const searchRef = useRef(null)
  const composerRef = useRef(null)

  const [user, setUser] = useState(null)
  const [booting, setBooting] = useState(true)
  const [loginOpen, setLoginOpen] = useState(false)
  const [loginLoading, setLoginLoading] = useState(false)
  const [loginError, setLoginError] = useState("")
  const [profileOpen, setProfileOpen] = useState(false)
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState("")
  const [supportOpen, setSupportOpen] = useState(false)

  const [models, setModels] = useState([])
  const [selectedModelId, setSelectedModelId] = useState(() => readCsvStorage(STORAGE_KEYS.modelIds)[0] || "")
  const [kbs, setKbs] = useState([])
  const [useKb, setUseKb] = useState(() => readTextStorage(STORAGE_KEYS.useKb, "1") !== "0")
  const [selectedKbIds, setSelectedKbIds] = useState(() => readCsvStorage(STORAGE_KEYS.kbIds))
  const [contextSize, setContextSize] = useState(() => {
    const value = Number.parseInt(readTextStorage(STORAGE_KEYS.contextSize, "10"), 10)
    return clamp(Number.isFinite(value) ? value : 10, 0, 20)
  })

  const [isThinking, setIsThinking] = useState(false)
  const [thinkingConvId, setThinkingConvId] = useState("")

  const [citationsOpen, setCitationsOpen] = useState(false)
  const [citationEntries, setCitationEntries] = useState([])
  const [activeCitation, setActiveCitation] = useState(null)

  const initialAutoCreateRef = useRef(false)
  const loadingSessionsRef = useRef(new Set())

  const updateSelectedId = useCallback((nextId) => {
    const value = String(nextId || "")
    setSelectedIdState(value)
    writeTextStorage(STORAGE_KEYS.sessionId, value)
  }, [])

  const handleUnauthorized = useCallback(
    (message = "请先登录后继续。") => {
      initialAutoCreateRef.current = false
      setUser(null)
      setModels([])
      setKbs([])
      setRawConversations([])
      updateSelectedId("")
      setIsThinking(false)
      setThinkingConvId("")
      setProfileOpen(false)
      setSupportOpen(false)
      setCitationsOpen(false)
      setLoginError(message)
      setLoginOpen(true)
    },
    [updateSelectedId],
  )

  const requestApi = useCallback(
    async (path, options = {}) => {
      try {
        return await apiFetch(path, options)
      } catch (error) {
        if (error?.status === 401) {
          handleUnauthorized("登录状态已过期，请重新登录。")
          return null
        }
        throw error
      }
    },
    [handleUnauthorized],
  )

  const refreshSessions = useCallback(async () => {
    const data = await requestApi("/api/sessions")
    if (!data) return []
    const sessions = Array.isArray(data?.sessions) ? data.sessions : []
    setRawConversations((previous) => mergeSessionList(previous, sessions))
    return sessions
  }, [requestApi])

  const loadModels = useCallback(async () => {
    const data = await requestApi("/api/models")
    if (!data) return []
    const nextModels = Array.isArray(data?.models) ? data.models : []
    setModels(nextModels)
    return nextModels
  }, [requestApi])

  const loadKnowledgeBases = useCallback(async () => {
    const data = await requestApi("/api/kbs")
    if (!data) return []
    const nextKbs = Array.isArray(data?.kbs) ? data.kbs : []
    setKbs((previous) => {
      const previousMap = new Map(previous.map((kb) => [kb.id, kb]))
      return nextKbs.map((kb) => {
        const existing = previousMap.get(kb.id)
        const sizeBytes = Number(kb?.sizeBytes ?? kb?.size ?? existing?.sizeBytes ?? existing?.size ?? 0)
        return {
          ...kb,
          docs: Array.isArray(existing?.docs) ? existing.docs : [],
          documentsLoaded: Boolean(existing?.documentsLoaded),
          size: sizeBytes,
          sizeBytes,
          sizeDisplay: String(kb?.sizeDisplay || existing?.sizeDisplay || ""),
          actualDocumentCount: Number(kb?.actualDocumentCount ?? existing?.actualDocumentCount ?? 0),
        }
      })
    })
    return nextKbs
  }, [requestApi])

  const loadAppData = useCallback(async () => {
    await Promise.all([refreshSessions(), loadModels(), loadKnowledgeBases()])
  }, [loadKnowledgeBases, loadModels, refreshSessions])

  const loadConversation = useCallback(
    async (conversationId, force = false) => {
      if (!conversationId) return
      if (loadingSessionsRef.current.has(conversationId)) return
      const currentConversation = rawConversations.find((conversation) => conversation.id === conversationId)
      if (!force && currentConversation?.messagesLoaded) return
      loadingSessionsRef.current.add(conversationId)
      try {
        const data = await requestApi(`/api/sessions/${encodeURIComponent(conversationId)}`)
        if (!data) return
        setRawConversations((previous) => {
          const existing = previous.find((conversation) => conversation.id === conversationId) || createEmptyConversation(conversationId)
          const messages = normalizeSessionMessages(conversationId, data?.messages, existing.messages, models)
          const updated = {
            ...existing,
            title: String(data?.title || existing.title || appCopy.newChatTitle),
            updatedAt: String(data?.updatedAt || existing.updatedAt || new Date().toISOString()),
            messagesLoaded: true,
            messages,
            messageCount: messages.length,
            preview: previewFromMessages(messages),
          }
          return sortConversations(
            previous.some((conversation) => conversation.id === conversationId)
              ? previous.map((conversation) => (conversation.id === conversationId ? updated : conversation))
              : [updated, ...previous],
          )
        })
      } catch (error) {
        if (error?.status === 404) {
          setRawConversations((prev) => prev.filter((conversation) => conversation.id !== conversationId))
          setPinnedSessionIds((prev) => prev.filter((id) => id !== conversationId))
          setSelectedIdState((currentId) => {
            if (currentId !== conversationId) return currentId
            writeTextStorage(STORAGE_KEYS.sessionId, "")
            return ""
          })
          refreshSessions().catch(() => {})
        }
      } finally {
        loadingSessionsRef.current.delete(conversationId)
      }
    },
    [models, rawConversations, requestApi, refreshSessions],
  )

  const maybeAutoRenameConversation = useCallback(
    async (conversationId, message) => {
      const title = String(message || "").replace(/\s+/g, " ").trim().slice(0, 60)
      if (!conversationId || !title) return
      try {
        const response = await requestApi(`/api/sessions/${encodeURIComponent(conversationId)}`, {
          method: "PATCH",
          json: { title },
        })
        if (!response) return
        setRawConversations((previous) =>
          sortConversations(
            previous.map((conversation) =>
              conversation.id === conversationId ? { ...conversation, title } : conversation,
            ),
          ),
        )
      } catch {}
    },
    [requestApi],
  )

  const createNewChat = useCallback(async () => {
    if (!user) {
      setLoginError("请先登录后再发起对话。")
      setLoginOpen(true)
      return null
    }
    try {
      const created = await requestApi("/api/sessions", {
        method: "POST",
        json: { title: appCopy.newChatTitle },
      })
      if (!created) return null
      const id = String(created?.id || created?.sessionId || "")
      if (!id) return null
      const conversation = {
        ...createEmptyConversation(id, String(created?.title || appCopy.newChatTitle)),
        createdAt: String(created?.createdAt || new Date().toISOString()),
        updatedAt: String(created?.updatedAt || new Date().toISOString()),
      }
      setRawConversations((previous) => sortConversations([conversation, ...previous.filter((item) => item.id !== id)]))
      updateSelectedId(id)
      setSidebarOpen(false)
      setTimeout(() => composerRef.current?.focus?.(), 0)
      initialAutoCreateRef.current = true
      return id
    } catch (error) {
      if (error?.status !== 401) console.error(error)
      return null
    }
  }, [requestApi, updateSelectedId, user])

  const sendMessage = useCallback(
    async (conversationId, content) => {
      const messageText = String(content || "").trim()
      if (!messageText) return
      if (!user) {
        setLoginError("请先登录后继续。")
        setLoginOpen(true)
        return
      }

      let targetId = String(conversationId || "")
      if (!targetId) {
        targetId = String((await createNewChat()) || "")
      }
      if (!targetId) return

      const now = new Date().toISOString()
      const userMessage = {
        id: createClientId("msg"),
        role: "user",
        content: messageText,
        createdAt: now,
      }

      let shouldAutoRename = false
      setRawConversations((previous) => {
        const existing = previous.find((conversation) => conversation.id === targetId) || createEmptyConversation(targetId)
        shouldAutoRename = isGenericNewChatTitle(existing.title) && (existing.messages || []).length === 0
        const messages = [...(Array.isArray(existing.messages) ? existing.messages : []), userMessage]
        const updated = {
          ...existing,
          messagesLoaded: true,
          messages,
          updatedAt: now,
          messageCount: messages.length,
          preview: messageText.slice(0, 80),
        }
        return sortConversations(
          previous.some((conversation) => conversation.id === targetId)
            ? previous.map((conversation) => (conversation.id === targetId ? updated : conversation))
            : [updated, ...previous],
        )
      })

      updateSelectedId(targetId)
      setIsThinking(true)
      setThinkingConvId(targetId)

      try {
        const response = await requestApi("/api/chat", {
          method: "POST",
          json: {
            sessionId: targetId,
            message: messageText,
            topK: 6,
            kbIds: useKb ? (selectedKbIds.length ? selectedKbIds : null) : [],
            model: selectedModelId || "",
            appendUser: true,
            contextSize,
          },
        })

        if (!response) return

        const finalConversationId = String(response?.sessionId || targetId)
        const assistantMessage = {
          id: createClientId("msg"),
          role: "assistant",
          content: String(response?.answer || "").trim() || "未返回有效回复。",
          createdAt: new Date().toISOString(),
          model: selectedModelId || "",
          modelLabel: resolveModelLabel(selectedModelId, models),
          citations: (Array.isArray(response?.citations) ? response.citations : []).map(normalizeCitationEntry),
        }

        setRawConversations((previous) => {
          const existing = previous.find((conversation) => conversation.id === finalConversationId) || createEmptyConversation(finalConversationId)
          const messages = [...(Array.isArray(existing.messages) ? existing.messages : []), assistantMessage]
          const updated = {
            ...existing,
            messagesLoaded: true,
            messages,
            updatedAt: assistantMessage.createdAt,
            messageCount: messages.length,
            preview: previewFromMessages(messages),
          }
          return sortConversations(
            previous.some((conversation) => conversation.id === finalConversationId)
              ? previous.map((conversation) => (conversation.id === finalConversationId ? updated : conversation))
              : [updated, ...previous],
          )
        })

        updateSelectedId(finalConversationId)
        if (shouldAutoRename) {
          void maybeAutoRenameConversation(finalConversationId, messageText)
        }
        void refreshSessions()
      } catch (error) {
        const fallbackMessage = {
          id: createClientId("msg"),
          role: "assistant",
          content: error?.message || "请求失败，请稍后重试。",
          createdAt: new Date().toISOString(),
          model: "",
          modelLabel: appCopy.systemLabel,
          citations: [],
        }
        setRawConversations((previous) =>
          sortConversations(
            previous.map((conversation) => {
              if (conversation.id !== targetId) return conversation
              const messages = [...(Array.isArray(conversation.messages) ? conversation.messages : []), fallbackMessage]
              return {
                ...conversation,
                messagesLoaded: true,
                messages,
                updatedAt: fallbackMessage.createdAt,
                messageCount: messages.length,
                preview: previewFromMessages(messages),
              }
            }),
          ),
        )
      } finally {
        setIsThinking(false)
        setThinkingConvId("")
      }
    },
    [
      contextSize,
      createNewChat,
      maybeAutoRenameConversation,
      models,
      refreshSessions,
      requestApi,
      selectedKbIds,
      selectedModelId,
      updateSelectedId,
      useKb,
      user,
    ],
  )

  const renameConversation = useCallback(
    async (conversationId, nextTitle) => {
      const title = String(nextTitle || "").trim()
      if (!conversationId || !title) return
      setRawConversations((previous) =>
        previous.map((conversation) =>
          conversation.id === conversationId ? { ...conversation, title } : conversation,
        ),
      )
      try {
        await requestApi(`/api/sessions/${encodeURIComponent(conversationId)}`, {
          method: "PATCH",
          json: { title },
        })
      } catch (error) {
        if (error?.status !== 401) console.error(error)
      }
    },
    [requestApi],
  )

  const deleteConversation = useCallback(
    async (conversationId) => {
      if (!conversationId) return
      const wasSelected = selectedId === conversationId
      setPinnedSessionIds((previous) => previous.filter((id) => id !== conversationId))
      setRawConversations((previous) => previous.filter((conversation) => conversation.id !== conversationId))
      if (wasSelected) updateSelectedId("")
      try {
        await requestApi(`/api/sessions/${encodeURIComponent(conversationId)}`, { method: "DELETE" })
      } catch (error) {
        if (error?.status !== 401) console.error(error)
      } finally {
        const sessions = await refreshSessions()
        if (wasSelected) {
          const fallback = Array.isArray(sessions) && sessions.length ? String(sessions[0]?.id || "") : ""
          updateSelectedId(fallback)
        }
      }
    },
    [refreshSessions, requestApi, selectedId, updateSelectedId],
  )

  const togglePin = useCallback((conversationId) => {
    setPinnedSessionIds((previous) =>
      previous.includes(conversationId)
        ? previous.filter((id) => id !== conversationId)
        : [...previous, conversationId],
    )
  }, [])

  const editMessage = useCallback((conversationId, messageId, nextContent) => {
    const content = String(nextContent || "")
    setRawConversations((previous) =>
      sortConversations(
        previous.map((conversation) => {
          if (conversation.id !== conversationId) return conversation
          const messages = (conversation.messages || []).map((message) =>
            message.id === messageId ? { ...message, content } : message,
          )
          return {
            ...conversation,
            messages,
            messageCount: messages.length,
            preview: previewFromMessages(messages),
          }
        }),
      ),
    )
  }, [])

  const resendMessage = useCallback(
    async (conversationId, messageId) => {
      const conversation = rawConversations.find((item) => item.id === conversationId)
      const message = conversation?.messages?.find((item) => item.id === messageId)
      if (!message?.content) return
      await sendMessage(conversationId, message.content)
    },
    [rawConversations, sendMessage],
  )

  const handlePauseThinking = useCallback(() => {
    setIsThinking(false)
    setThinkingConvId("")
  }, [])

  const handleLogin = useCallback(
    async ({ username, password }) => {
      setLoginLoading(true)
      setLoginError("")
      try {
        const me = await apiFetch("/api/auth/login", {
          method: "POST",
          json: { username, password },
        })
        setUser(me)
        setLoginOpen(false)
        initialAutoCreateRef.current = false
        await loadAppData()
      } catch (error) {
        setLoginError(error?.message || "登录失败。")
      } finally {
        setLoginLoading(false)
        setBooting(false)
      }
    },
    [loadAppData],
  )

  const handleLogout = useCallback(async () => {
    try {
      await apiFetch("/api/auth/logout", { method: "POST" })
    } catch {}
    window.location.href = "/"
  }, [])

  const handleProfileSave = useCallback(
    async (payload) => {
      setProfileLoading(true)
      setProfileError("")
      try {
        const me = await requestApi("/api/auth/profile", {
          method: "PATCH",
          json: payload,
        })
        if (!me) return
        setUser(me)
        setProfileOpen(false)
      } catch (error) {
        setProfileError(error?.message || "保存个人资料失败。")
      } finally {
        setProfileLoading(false)
      }
    },
    [requestApi],
  )

  const handleSendSupportMessage = useCallback(
    async (payload) => {
      await requestApi("/api/messages", {
        method: "POST",
        json: payload,
      })
    },
    [requestApi],
  )

  const handleLoadSupportHistory = useCallback(async () => {
    const data = await requestApi("/api/messages")
    return Array.isArray(data?.messages) ? data.messages : []
  }, [requestApi])

  const handleDeleteSupportMessage = useCallback(
    async (messageId) => {
      await requestApi(`/api/messages/${encodeURIComponent(messageId)}`, {
        method: "DELETE",
      })
    },
    [requestApi],
  )

  const handleCreateKb = useCallback(
    async (name) => {
      try {
        await requestApi("/api/kbs", {
          method: "POST",
          json: { name },
        })
        await loadKnowledgeBases()
      } catch (error) {
        if (error?.status !== 401) window.alert(error?.message || "创建知识库失败。")
      }
    },
    [loadKnowledgeBases, requestApi],
  )

  const handleDeleteKb = useCallback(
    async (kbId) => {
      try {
        await requestApi(`/api/kbs/${encodeURIComponent(kbId)}`, {
          method: "DELETE",
        })
        await loadKnowledgeBases()
      } catch (error) {
        if (error?.status !== 401) window.alert(error?.message || "删除知识库失败。")
      }
    },
    [loadKnowledgeBases, requestApi],
  )

  const handleLoadDocuments = useCallback(
    async (kbId) => {
      try {
        const data = await requestApi(`/api/kbs/${encodeURIComponent(kbId)}/documents`)
        if (!data) return []
        const documents = normalizeKbDocumentsResponse(data)
        const totalSize = documents.reduce((sum, document) => sum + Number(document?.sizeBytes || 0), 0)
        setKbs((previous) =>
          previous.map((kb) =>
            kb.id === kbId
              ? {
                  ...kb,
                  docs: documents,
                  documentsLoaded: true,
                  size: totalSize,
                  sizeBytes: totalSize,
                  actualDocumentCount: documents.length,
                }
              : kb,
          ),
        )
        return documents
      } catch (error) {
        if (error?.status !== 401) window.alert(error?.message || "加载文档失败。")
        return []
      }
    },
    [requestApi],
  )

  const handleUploadDocument = useCallback(
    async (kbId, file) => {
      const formData = new FormData()
      formData.append("file", file)
      try {
        await requestApi(`/api/kbs/${encodeURIComponent(kbId)}/documents/upload`, {
          method: "POST",
          body: formData,
        })
      } catch (error) {
        if (error?.status !== 401) window.alert(error?.message || "上传文档失败。")
      }
    },
    [requestApi],
  )

  const handleDeleteDocument = useCallback(
    async (kbId, documentId) => {
      try {
        await requestApi(`/api/kbs/${encodeURIComponent(kbId)}/documents/${encodeURIComponent(documentId)}`, {
          method: "DELETE",
        })
        await handleLoadDocuments(kbId)
      } catch (error) {
        if (error?.status !== 401) window.alert(error?.message || "删除文档失败。")
      }
    },
    [handleLoadDocuments, requestApi],
  )

  const loadCitationDetail = useCallback(
    async (citation) => {
      if (!citation) return null
      if (!citation.kbId || !citation.chunkId) {
        setActiveCitation(citation)
        return citation
      }
      try {
        const data = await requestApi(
          `/api/kbs/${encodeURIComponent(citation.kbId)}/chunks/${encodeURIComponent(citation.chunkId)}`,
        )
        if (!data) return null
        const fullText = String(data?.chunk?.content || citation.fullText || citation.snippet || "")
        const nextCitation = {
          ...citation,
          filename: citation.filename || data?.document?.filename || citation.docId || "引用来源",
          snippet: citation.snippet || data?.chunk?.sourceHint || fullText.slice(0, 220),
          fullText,
        }
        setCitationEntries((previous) =>
          previous.map((entry) => (entry.id === nextCitation.id ? nextCitation : entry)),
        )
        setActiveCitation(nextCitation)
        return nextCitation
      } catch (error) {
        if (error?.status !== 401) console.error(error)
        setActiveCitation(citation)
        return citation
      }
    },
    [requestApi],
  )

  const handleOpenCitations = useCallback(
    async (citations, activeCitationOrIdx) => {
      const nextEntries = (Array.isArray(citations) ? citations : []).map(normalizeCitationEntry)
      setCitationEntries(nextEntries)
      const initial =
        (typeof activeCitationOrIdx === "number" ? nextEntries[activeCitationOrIdx] : null) ||
        nextEntries[0] ||
        null
      setActiveCitation(initial)
      setCitationsOpen(true)
      if (initial) {
        await loadCitationDetail(initial)
      }
    },
    [loadCitationDetail],
  )

  const handleSelectCitation = useCallback(
    (citation) => {
      setActiveCitation(citation)
      if (!citation?.fullText && citation?.kbId && citation?.chunkId) {
        void loadCitationDetail(citation)
      }
    },
    [loadCitationDetail],
  )

  useEffect(() => {
    if (theme === "dark") document.documentElement.classList.add("dark")
    else document.documentElement.classList.remove("dark")
    document.documentElement.setAttribute("data-theme", theme)
    document.documentElement.style.colorScheme = theme
    writeTextStorage(STORAGE_KEYS.theme, theme)
  }, [theme])

  useEffect(() => {
    writeStored(STORAGE_KEYS.sidebarSections, collapsed)
  }, [collapsed])

  useEffect(() => {
    writeTextStorage(STORAGE_KEYS.sidebarCollapsed, sidebarCollapsed ? "1" : "0")
  }, [sidebarCollapsed])

  useEffect(() => {
    writeStored(STORAGE_KEYS.pinnedSessions, uniqueStrings(pinnedSessionIds))
  }, [pinnedSessionIds])

  useEffect(() => {
    writeTextStorage(STORAGE_KEYS.useKb, useKb ? "1" : "0")
  }, [useKb])

  useEffect(() => {
    writeTextStorage(STORAGE_KEYS.contextSize, String(contextSize))
  }, [contextSize])

  useEffect(() => {
    writeCsvStorage(STORAGE_KEYS.modelIds, selectedModelId ? [selectedModelId] : [])
  }, [selectedModelId])

  useEffect(() => {
    writeCsvStorage(STORAGE_KEYS.kbIds, selectedKbIds)
    writeTextStorage(STORAGE_KEYS.kbSelectionVersion, KB_SELECTION_VERSION)
  }, [selectedKbIds])

  useEffect(() => {
    const listener = (event) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "n") {
        event.preventDefault()
        void createNewChat()
      }
      if (!event.metaKey && !event.ctrlKey && event.key === "/") {
        const tag = document.activeElement?.tagName?.toLowerCase()
        if (tag !== "input" && tag !== "textarea") {
          event.preventDefault()
          searchRef.current?.focus()
        }
      }
      if (event.key === "Escape" && sidebarOpen) {
        setSidebarOpen(false)
      }
    }
    window.addEventListener("keydown", listener)
    return () => window.removeEventListener("keydown", listener)
  }, [createNewChat, sidebarOpen])

  useEffect(() => {
    let cancelled = false
    const bootstrap = async () => {
      setBooting(true)
      try {
        const me = await apiFetch("/api/auth/me", { silent401: true })
        if (cancelled) return
        if (!me) {
          setUser(null)
          setLoginOpen(true)
          return
        }
        setUser(me)
        setLoginOpen(false)
        setLoginError("")
        await loadAppData()
      } catch (error) {
        if (cancelled) return
        console.error(error)
        setLoginError(error?.message || "加载工作区失败。")
        setLoginOpen(true)
      } finally {
        if (!cancelled) setBooting(false)
      }
    }
    void bootstrap()
    return () => {
      cancelled = true
    }
  }, [loadAppData])

  useEffect(() => {
    if (!models.length) {
      if (selectedModelId) setSelectedModelId("")
      return
    }
    if (models.some((model) => model.id === selectedModelId)) return
    const storedId = readCsvStorage(STORAGE_KEYS.modelIds)[0] || ""
    const fallback = models.find((model) => model.id === storedId)?.id || models[0]?.id || ""
    if (fallback !== selectedModelId) setSelectedModelId(fallback)
  }, [models, selectedModelId])

  useEffect(() => {
    const availableIds = uniqueStrings(kbs.map((kb) => kb.id))
    if (!availableIds.length) {
      if (selectedKbIds.length) setSelectedKbIds([])
      return
    }

    const currentSelected = uniqueStrings(selectedKbIds.filter((id) => availableIds.includes(id)))
    const storedSelected = uniqueStrings(readCsvStorage(STORAGE_KEYS.kbIds).filter((id) => availableIds.includes(id)))
    const storedVersion = readTextStorage(STORAGE_KEYS.kbSelectionVersion, "")
    const systemIds = uniqueStrings(kbs.filter((kb) => kb.isSystem).map((kb) => kb.id))

    let nextSelected = currentSelected.length ? currentSelected : storedSelected
    const legacyOnlySystem =
      storedVersion !== KB_SELECTION_VERSION &&
      nextSelected.length &&
      systemIds.length &&
      nextSelected.length === systemIds.length &&
      systemIds.every((id) => nextSelected.includes(id)) &&
      availableIds.length > systemIds.length

    if (!nextSelected.length || legacyOnlySystem) nextSelected = availableIds

    const isSame =
      nextSelected.length === currentSelected.length && nextSelected.every((id, index) => id === currentSelected[index])
    if (!isSame) setSelectedKbIds(nextSelected)
  }, [kbs, selectedKbIds])

  useEffect(() => {
    if (!models.length) return
    setRawConversations((previous) => {
      let changed = false
      const next = previous.map((conversation) => {
        if (!conversation.messages?.length) return conversation
        let messageChanged = false
        const messages = conversation.messages.map((message) => {
          if (message.role !== "assistant" || !message.model) return message
          const modelLabel = resolveModelLabel(message.model, models)
          if (message.modelLabel === modelLabel) return message
          messageChanged = true
          return { ...message, modelLabel }
        })
        if (!messageChanged) return conversation
        changed = true
        return {
          ...conversation,
          messages,
          preview: previewFromMessages(messages),
          messageCount: messages.length,
        }
      })
      return changed ? next : previous
    })
  }, [models])

  useEffect(() => {
    if (!rawConversations.length) {
      if (selectedId) updateSelectedId("")
      return
    }
    if (selectedId && rawConversations.some((conversation) => conversation.id === selectedId)) return
    const storedId = readTextStorage(STORAGE_KEYS.sessionId, "")
    const fallback = rawConversations.find((conversation) => conversation.id === storedId)?.id || rawConversations[0]?.id || ""
    if (fallback) updateSelectedId(fallback)
  }, [rawConversations, selectedId, updateSelectedId])

  useEffect(() => {
    if (!user || !selectedId) return
    const selectedConversation = rawConversations.find((conversation) => conversation.id === selectedId)
    if (!selectedConversation || !selectedConversation.messagesLoaded) {
      void loadConversation(selectedId)
    }
  }, [loadConversation, rawConversations, selectedId, user])

  useEffect(() => {
    if (booting || !user || rawConversations.length || initialAutoCreateRef.current) return
    initialAutoCreateRef.current = true
    void createNewChat()
  }, [booting, createNewChat, rawConversations.length, user])

  const conversations = useMemo(
    () =>
      rawConversations.map((conversation) => ({
        ...conversation,
        pinned: pinnedSessionIds.includes(conversation.id),
      })),
    [pinnedSessionIds, rawConversations],
  )

  const filteredConversations = useMemo(() => {
    if (!query.trim()) return conversations
    const keyword = query.toLowerCase()
    return conversations.filter((conversation) => {
      const title = String(conversation.title || "").toLowerCase()
      const preview = String(conversation.preview || "").toLowerCase()
      return title.includes(keyword) || preview.includes(keyword)
    })
  }, [conversations, query])

  const pinned = useMemo(
    () => filteredConversations.filter((conversation) => conversation.pinned),
    [filteredConversations],
  )

  const recent = useMemo(
    () => filteredConversations.filter((conversation) => !conversation.pinned).slice(0, 10),
    [filteredConversations],
  )

  const selectedConversation = useMemo(
    () => conversations.find((conversation) => conversation.id === selectedId) || null,
    [conversations, selectedId],
  )

  const knowledgeBaseProps = useMemo(
    () => ({
      kbs,
      onCreateKb: handleCreateKb,
      onDeleteKb: handleDeleteKb,
      onLoadDocuments: handleLoadDocuments,
      onUploadDocument: handleUploadDocument,
      onDeleteDocument: handleDeleteDocument,
    }),
    [handleCreateKb, handleDeleteDocument, handleDeleteKb, handleLoadDocuments, handleUploadDocument, kbs],
  )

  return (
    <div className="h-screen w-full overflow-hidden bg-zinc-50 text-zinc-900 dark:bg-zinc-950 dark:text-zinc-100">
      <div className="flex h-screen w-full overflow-hidden">
        <Sidebar
          open={sidebarOpen}
          onClose={() => setSidebarOpen(false)}
          theme={theme}
          setTheme={setTheme}
          collapsed={collapsed}
          setCollapsed={setCollapsed}
          sidebarCollapsed={sidebarCollapsed}
          setSidebarCollapsed={setSidebarCollapsed}
          conversations={conversations}
          pinned={pinned}
          recent={recent}
          selectedId={selectedId}
          onSelect={(id) => {
            updateSelectedId(id)
            setSidebarOpen(false)
          }}
          togglePin={togglePin}
          query={query}
          setQuery={setQuery}
          searchRef={searchRef}
          renameConversation={renameConversation}
          deleteConversation={deleteConversation}
          createNewChat={() => void createNewChat()}
          user={user}
          onOpenProfile={() => {
            if (!user) {
              setLoginError("请先登录后再编辑个人资料。")
              setLoginOpen(true)
              return
            }
            setProfileError("")
            setProfileOpen(true)
          }}
          onOpenSupport={() => {
            if (!user) {
              setLoginError("请先登录后联系管理员。")
              setLoginOpen(true)
              return
            }
            setSupportOpen(true)
          }}
          onLogout={() => void handleLogout()}
          knowledgeBaseProps={knowledgeBaseProps}
        />

        <main className="relative flex min-w-0 flex-1 flex-col">
          <Header
            models={models}
            selectedModelId={selectedModelId}
            onSelectModel={setSelectedModelId}
            sidebarCollapsed={sidebarCollapsed}
            setSidebarOpen={setSidebarOpen}
          />
          <ChatPane
            ref={composerRef}
            conversation={selectedConversation}
            onSend={(content) => sendMessage(selectedId, content)}
            onEditMessage={(messageId, nextContent) => {
              if (!selectedId) return
              editMessage(selectedId, messageId, nextContent)
            }}
            onResendMessage={(messageId) => {
              if (!selectedId) return
              void resendMessage(selectedId, messageId)
            }}
            isThinking={isThinking && thinkingConvId === selectedId}
            onPauseThinking={handlePauseThinking}
            models={models}
            selectedModelId={selectedModelId}
            onSelectModel={setSelectedModelId}
            useKb={useKb}
            setUseKb={setUseKb}
            kbs={kbs}
            selectedKbIds={selectedKbIds}
            setSelectedKbIds={setSelectedKbIds}
            contextSize={contextSize}
            setContextSize={setContextSize}
            user={user}
            onOpenCitations={(citations, idx) => void handleOpenCitations(citations, idx)}
          />
        </main>
      </div>

      <LoginModal
        isOpen={loginOpen}
        loading={loginLoading}
        error={loginError}
        onClose={() => setLoginOpen(false)}
        onSubmit={handleLogin}
      />
      <ProfileModal
        isOpen={profileOpen}
        user={user}
        loading={profileLoading}
        error={profileError}
        onClose={() => setProfileOpen(false)}
        onSave={handleProfileSave}
      />
      <SupportModal
        isOpen={supportOpen}
        onClose={() => setSupportOpen(false)}
        onSend={handleSendSupportMessage}
        onLoadHistory={handleLoadSupportHistory}
        onDeleteMessage={handleDeleteSupportMessage}
      />
      <CitationsModal
        isOpen={citationsOpen}
        onClose={() => setCitationsOpen(false)}
        citations={citationEntries}
        activeCitation={activeCitation}
        onSelectCitation={handleSelectCitation}
      />

      {booting ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center bg-zinc-50/40 backdrop-blur-[1px] dark:bg-zinc-950/40">
          <div className="rounded-full border border-zinc-200 bg-white px-4 py-2 text-sm text-zinc-500 shadow-sm dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-400">
            {appCopy.loadingWorkspaceLabel}
          </div>
        </div>
      ) : null}
    </div>
  )
}
