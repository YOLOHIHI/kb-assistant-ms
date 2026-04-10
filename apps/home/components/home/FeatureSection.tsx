'use client'

import { useEffect, useRef, useState } from 'react'
import { MessageSquare, Search, FileText, Zap, Shield, Clock } from 'lucide-react'
import { cn } from '@/lib/utils'

const features = [
  {
    icon: MessageSquare,
    title: '智能对话',
    description: '基于大语言模型的自然对话能力，理解上下文，提供精准回答',
    gradient: 'from-orange-500/20 to-amber-500/20',
    iconColor: 'text-orange-500',
  },
  {
    icon: Search,
    title: '知识检索',
    description: '快速从海量知识库中检索相关内容，支持语义搜索',
    gradient: 'from-blue-500/20 to-cyan-500/20',
    iconColor: 'text-blue-500',
  },
  {
    icon: FileText,
    title: '文档分析',
    description: '上传文档自动分析，提取关键信息，生成摘要总结',
    gradient: 'from-emerald-500/20 to-teal-500/20',
    iconColor: 'text-emerald-500',
  },
  {
    icon: Zap,
    title: '快速响应',
    description: '毫秒级响应速度，即时获取答案，提升工作效率',
    gradient: 'from-yellow-500/20 to-orange-500/20',
    iconColor: 'text-yellow-500',
  },
  {
    icon: Shield,
    title: '安全可靠',
    description: '企业级数据加密，严格的访问控制，保障信息安全',
    gradient: 'from-purple-500/20 to-pink-500/20',
    iconColor: 'text-purple-500',
  },
  {
    icon: Clock,
    title: '全天候服务',
    description: '7x24 小时在线服务，随时随地获取帮助支持',
    gradient: 'from-rose-500/20 to-red-500/20',
    iconColor: 'text-rose-500',
  },
]

export default function FeatureSection() {
  const [visibleItems, setVisibleItems] = useState<number[]>([])
  const sectionRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const index = Number(entry.target.getAttribute('data-index'))
            setVisibleItems((prev) => [...new Set([...prev, index])])
          }
        })
      },
      { threshold: 0.2, rootMargin: '0px 0px -50px 0px' }
    )

    const cards = sectionRef.current?.querySelectorAll('[data-index]')
    cards?.forEach((card) => observer.observe(card))

    return () => observer.disconnect()
  }, [])

  return (
    <section id="features" className="relative py-24 sm:py-36 overflow-hidden">
      {/* Background */}
      <div className="absolute inset-0 -z-10 bg-muted/30" />
      <div className="absolute inset-0 -z-10">
        <div className="absolute left-0 top-1/2 h-[400px] w-[400px] -translate-y-1/2 rounded-full bg-accent/5 blur-[100px]" />
        <div className="absolute right-0 bottom-0 h-[300px] w-[300px] rounded-full bg-accent/5 blur-[80px]" />
      </div>

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8" ref={sectionRef}>
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto">
          <span className="inline-block mb-4 text-sm font-medium text-accent tracking-wider uppercase">
            核心功能
          </span>
          <h2 className="text-3xl font-bold tracking-tight sm:text-5xl">
            全面的智能化功能
          </h2>
          <p className="mx-auto mt-6 max-w-2xl text-lg text-muted-foreground leading-relaxed">
            我们提供一整套智能化工具，满足您在知识管理和信息获取方面的各种需求
          </p>
        </div>

        {/* Features Grid */}
        <div className="mt-20 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((feature, index) => (
            <div
              key={feature.title}
              data-index={index}
              className={cn(
                "group relative overflow-hidden rounded-3xl glass-card p-8 transition-all duration-500 hover:shadow-xl hover:shadow-accent/5 hover:-translate-y-1",
                visibleItems.includes(index) 
                  ? "opacity-100 translate-y-0" 
                  : "opacity-0 translate-y-8"
              )}
              style={{ transitionDelay: `${index * 100}ms` }}
            >
              {/* Gradient background on hover */}
              <div className={cn(
                "absolute inset-0 bg-gradient-to-br opacity-0 transition-opacity duration-500 group-hover:opacity-100",
                feature.gradient
              )} />
              
              {/* Content */}
              <div className="relative">
                <div className={cn(
                  "mb-6 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-muted transition-all duration-300 group-hover:scale-110",
                  feature.iconColor
                )}>
                  <feature.icon className="h-7 w-7" />
                </div>
                <h3 className="text-xl font-semibold mb-3">{feature.title}</h3>
                <p className="text-muted-foreground leading-relaxed">{feature.description}</p>
              </div>

              {/* Decorative corner */}
              <div className="absolute -right-6 -top-6 h-24 w-24 rounded-full border border-accent/10 opacity-0 transition-opacity group-hover:opacity-100" />
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
