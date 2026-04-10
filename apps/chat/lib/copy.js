export const appCopy = {
  brandName: "知识库助手",
  appDescription: "基于知识库的智能问答工作台",
  newChatTitle: "新对话",
  guestLabel: "访客",
  assistantLabel: "助手",
  aiLabel: "助手",
  meLabel: "我",
  systemLabel: "系统",
  workspaceLabel: "工作区",
  plannedLabel: "规划中",
  loadingWorkspaceLabel: "正在加载工作区...",
  disclaimer: "智能助手可能会出错，请核对重要信息。",
  routeLabels: {
    chat: "用户对话",
    home: "首页",
    admin: "管理后台",
  },
}

export function formatUserRole(role) {
  const value = String(role || "").trim().toUpperCase()
  if (value === "ADMIN") return "系统管理员"
  if (value === "TENANT_ADMIN") return "租户管理员"
  if (value === "USER") return "普通用户"
  return "访客"
}

export function formatSupportStatus(status) {
  const value = String(status || "").trim().toUpperCase()
  if (value === "OPEN") return "待处理"
  if (value === "REPLIED") return "已回复"
  if (value === "CLOSED") return "已关闭"
  return value || "未知状态"
}

export function formatMessageCount(count) {
  return `${Number(count || 0)} 条消息`
}

export function formatCitationCount(count) {
  return `${Number(count || 0)} 条引用`
}

export function formatKnowledgeBaseLabel(kbId) {
  const value = String(kbId || "").trim()
  return value ? `知识库：${value}` : ""
}
