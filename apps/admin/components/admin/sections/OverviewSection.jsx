"use client"

import React, { useEffect, useState } from "react"
import { motion } from "framer-motion"
import { Users, Monitor, Book, ArrowRight } from "lucide-react"
import {
  isUnauthorizedError,
  listAdminKbs,
  listAdminModels,
  listAdminUsers,
  normalizeKnowledgeBases,
} from "@/lib/admin-api"

const OVERVIEW_CARDS = [
  {
    id: "users",
    kicker: "Users",
    title: "审批与权限",
    description: "快速过滤待审批、启用和禁用用户，管理用户角色与访问权限",
    icon: Users,
    gradient: "from-blue-500 to-indigo-600",
    shadowColor: "shadow-blue-500/20",
  },
  {
    id: "models",
    kicker: "Models",
    title: "渠道与模型治理",
    description: "集中维护 Provider、同步模型列表，启用或停用可用模型",
    icon: Monitor,
    gradient: "from-purple-500 to-pink-600",
    shadowColor: "shadow-purple-500/20",
  },
  {
    id: "kb",
    kicker: "Knowledge",
    title: "文档与知识沉淀",
    description: "统一维护公共知识库与文档索引，支持本地或云端嵌入模式",
    icon: Book,
    gradient: "from-emerald-500 to-teal-600",
    shadowColor: "shadow-emerald-500/20",
  },
]

const container = {
  hidden: { opacity: 0 },
  show: {
    opacity: 1,
    transition: {
      staggerChildren: 0.1,
    },
  },
}

const item = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0 },
}

export default function OverviewSection({ enabled, onNavigate, onUnauthorized, pendingUsersCount = 0 }) {
  const [summary, setSummary] = useState({
    totalUsers: 0,
    pendingUsers: pendingUsersCount,
    activeModels: 0,
    documents: 0,
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    let cancelled = false

    async function loadSummary() {
      if (!enabled) return
      setLoading(true)
      setError("")

      try {
        const [usersData, modelsData, kbsData] = await Promise.all([
          listAdminUsers(),
          listAdminModels(),
          listAdminKbs(),
        ])

        if (cancelled) return

        const users = Array.isArray(usersData?.users) ? usersData.users : []
        const models = Array.isArray(modelsData?.models) ? modelsData.models : []
        const kbs = normalizeKnowledgeBases(kbsData)

        setSummary({
          totalUsers: users.length,
          pendingUsers: users.filter((user) => user?.status === "PENDING").length,
          activeModels: models.filter((model) => model?.enabled).length,
          documents: kbs.reduce(
            (total, kb) => total + Number(kb?.documentCount || 0),
            0
          ),
        })
      } catch (loadError) {
        if (cancelled) return
        if (isUnauthorizedError(loadError)) {
          onUnauthorized?.(loadError.message)
          return
        }
        setError(loadError?.message || "加载概览失败")
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    loadSummary()

    return () => {
      cancelled = true
    }
  }, [enabled, onUnauthorized])

  useEffect(() => {
    setSummary((prev) => ({ ...prev, pendingUsers: pendingUsersCount }))
  }, [pendingUsersCount])

  return (
    <div className="mx-auto max-w-5xl">
      {/* Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-8"
      >
        <h1 className="text-2xl font-bold tracking-tight">欢迎回来</h1>
        <p className="mt-1 text-zinc-500 dark:text-zinc-400">
          管理系统用户、模型配置和知识库资源
        </p>
        {error && <p className="mt-2 text-sm text-red-500">{error}</p>}
      </motion.div>

      {/* Cards */}
      <motion.div
        variants={container}
        initial="hidden"
        animate="show"
        className="grid gap-6 md:grid-cols-2 lg:grid-cols-3"
      >
        {OVERVIEW_CARDS.map((card) => {
          const Icon = card.icon
          return (
            <motion.div
              key={card.id}
              variants={item}
              whileHover={{ y: -4, transition: { duration: 0.2 } }}
              className="group relative overflow-hidden rounded-2xl border border-zinc-200/60 bg-white p-6 shadow-sm transition-shadow hover:shadow-lg dark:border-zinc-800 dark:bg-zinc-900"
            >
              {/* Background gradient on hover */}
              <div className="pointer-events-none absolute inset-0 bg-gradient-to-br from-zinc-50/50 to-transparent opacity-0 transition-opacity group-hover:opacity-100 dark:from-zinc-800/50" />

              {/* Icon */}
              <motion.div
                className={`mb-4 inline-flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br ${card.gradient} shadow-lg ${card.shadowColor}`}
                whileHover={{ scale: 1.05, rotate: 5 }}
                transition={{ type: "spring", stiffness: 300, damping: 20 }}
              >
                <Icon className="h-6 w-6 text-white" />
              </motion.div>

              {/* Kicker */}
              <p className="mb-1 text-xs font-semibold uppercase tracking-wider text-zinc-400 dark:text-zinc-500">
                {card.kicker}
              </p>

              {/* Title */}
              <h3 className="mb-2 text-lg font-semibold">{card.title}</h3>

              {/* Description */}
              <p className="mb-4 text-sm text-zinc-500 dark:text-zinc-400">
                {card.description}
              </p>

              {/* Action */}
              <motion.button
                onClick={() => onNavigate(card.id)}
                className="inline-flex items-center gap-1.5 text-sm font-medium text-blue-600 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
                whileHover={{ x: 4 }}
              >
                立即管理
                <ArrowRight className="h-4 w-4" />
              </motion.button>
            </motion.div>
          )
        })}
      </motion.div>

      {/* Quick stats */}
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ delay: 0.3 }}
        className="mt-8 grid grid-cols-2 gap-4 md:grid-cols-4"
      >
        {[
          { label: "总用户数", value: summary.totalUsers, change: "" },
          { label: "待审批", value: summary.pendingUsers, change: "" },
          { label: "活跃模型", value: summary.activeModels, change: "" },
          { label: "知识文档", value: summary.documents, change: "" },
        ].map((stat, i) => (
          <motion.div
            key={stat.label}
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: 0.4 + i * 0.05 }}
            className="rounded-xl border border-zinc-200/60 bg-white p-4 dark:border-zinc-800 dark:bg-zinc-900"
          >
            <p className="text-xs text-zinc-500 dark:text-zinc-400">{stat.label}</p>
            <div className="mt-1 flex items-baseline gap-2">
              <span className="text-2xl font-bold">{loading ? "..." : stat.value}</span>
              {stat.change && (
                <span className="text-xs font-medium text-emerald-500">{stat.change}</span>
              )}
            </div>
          </motion.div>
        ))}
      </motion.div>
    </div>
  )
}
