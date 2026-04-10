'use client'

import { Button } from '@/components/ui/button'
import { ArrowRight, Sparkles, Zap, Shield, Globe } from 'lucide-react'

interface HeroSectionProps {
  isLoggedIn: boolean
  onEnterSystem: () => void
  onLogin: () => void
}

export default function HeroSection({ isLoggedIn, onEnterSystem, onLogin }: HeroSectionProps) {
  const handleClick = () => {
    if (isLoggedIn) {
      onEnterSystem()
    } else {
      onLogin()
    }
  }

  return (
    <section className="relative overflow-hidden pt-32 pb-24 sm:pt-44 sm:pb-36">
      {/* Premium background effects */}
      <div className="absolute inset-0 -z-10 gradient-mesh" />
      <div className="absolute inset-0 -z-10">
        <div className="absolute left-1/4 top-1/4 h-[500px] w-[500px] rounded-full bg-accent/10 blur-[120px] animate-float" />
        <div className="absolute right-1/4 bottom-1/4 h-[400px] w-[400px] rounded-full bg-accent/5 blur-[100px] animate-float animation-delay-300" />
      </div>
      
      {/* Floating decorative elements */}
      <div className="absolute left-10 top-40 hidden lg:block opacity-20">
        <div className="h-20 w-20 rounded-2xl border border-accent/30 animate-float" />
      </div>
      <div className="absolute right-20 top-60 hidden lg:block opacity-20">
        <div className="h-14 w-14 rounded-full border border-accent/30 animate-float animation-delay-200" />
      </div>

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div className="text-center">
          {/* Badge */}
          <div className="mb-8 inline-flex items-center gap-2 rounded-full border border-accent/30 bg-accent/10 px-5 py-2 text-sm font-medium text-accent animate-fade-up opacity-0">
            <Sparkles className="h-4 w-4" />
            <span>新一代智能知识库助手</span>
          </div>

          {/* Title */}
          <h1 className="mx-auto max-w-4xl text-balance text-4xl font-bold tracking-tight sm:text-6xl lg:text-7xl animate-fade-up opacity-0 animation-delay-100">
            让 AI 成为您的
            <span className="text-gradient"> 智能助手</span>
          </h1>

          {/* Description */}
          <p className="mx-auto mt-8 max-w-2xl text-pretty text-lg text-muted-foreground sm:text-xl leading-relaxed animate-fade-up opacity-0 animation-delay-200">
            基于先进的大语言模型，为您提供智能问答、知识检索、文档分析等全方位服务。
            快速获取所需信息，提升工作效率。
          </p>

          {/* CTA Buttons */}
          <div className="mt-12 flex flex-col items-center justify-center gap-4 sm:flex-row animate-fade-up opacity-0 animation-delay-300">
            <Button 
              size="lg" 
              className="gap-2 px-8 h-12 rounded-full bg-accent text-accent-foreground hover:bg-accent/90 shadow-xl shadow-accent/25 transition-all hover:shadow-2xl hover:shadow-accent/30 hover:scale-105"
              onClick={handleClick}
            >
              {isLoggedIn ? '进入系统' : '开始使用'}
              <ArrowRight className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="lg"
              className="px-8 h-12 rounded-full border-border/60 hover:border-accent/50 hover:bg-accent/5 transition-all"
              onClick={() => {
                const element = document.querySelector('#features')
                if (element) {
                  element.scrollIntoView({ behavior: 'smooth' })
                }
              }}
            >
              了解更多
            </Button>
          </div>

          {/* Feature Pills */}
          <div className="mt-16 flex flex-wrap items-center justify-center gap-3 animate-fade-up opacity-0 animation-delay-400">
            {[
              { icon: Zap, label: '快速响应' },
              { icon: Shield, label: '安全可靠' },
              { icon: Globe, label: '全天候服务' },
            ].map((item) => (
              <div 
                key={item.label}
                className="flex items-center gap-2 rounded-full bg-muted/60 px-4 py-2 text-sm text-muted-foreground"
              >
                <item.icon className="h-4 w-4 text-accent" />
                <span>{item.label}</span>
              </div>
            ))}
          </div>

          {/* Stats */}
          <div className="mt-20 grid grid-cols-2 gap-8 sm:grid-cols-4 animate-fade-up opacity-0 animation-delay-500">
            {[
              { label: '活跃用户', value: '10,000+' },
              { label: '问答次数', value: '1M+' },
              { label: '知识文档', value: '50,000+' },
              { label: '满意度', value: '99%' },
            ].map((stat, index) => (
              <div 
                key={stat.label} 
                className="group relative flex flex-col items-center p-6 rounded-2xl transition-all hover:bg-muted/50"
              >
                <span className="text-4xl font-bold tracking-tight text-gradient">{stat.value}</span>
                <span className="mt-2 text-sm text-muted-foreground">{stat.label}</span>
                <div className="absolute inset-0 rounded-2xl border border-transparent group-hover:border-accent/20 transition-colors" />
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
