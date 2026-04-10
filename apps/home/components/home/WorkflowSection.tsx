'use client'

import { useEffect, useRef, useState } from 'react'
import { UserPlus, LogIn, MessageCircle, CheckCircle } from 'lucide-react'
import { cn } from '@/lib/utils'

const steps = [
  {
    icon: UserPlus,
    title: '注册账号',
    description: '填写基本信息，提交注册申请',
  },
  {
    icon: LogIn,
    title: '登录系统',
    description: '审批通过后，使用账号密码登录',
  },
  {
    icon: MessageCircle,
    title: '开始对话',
    description: '在对话界面输入问题，AI 即时响应',
  },
  {
    icon: CheckCircle,
    title: '获取答案',
    description: '获取精准答案，支持追问和导出',
  },
]

export default function WorkflowSection() {
  const [activeStep, setActiveStep] = useState(-1)
  const sectionRef = useRef<HTMLElement>(null)

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          // Animate steps sequentially
          steps.forEach((_, index) => {
            setTimeout(() => {
              setActiveStep(index)
            }, index * 300)
          })
        }
      },
      { threshold: 0.3 }
    )

    if (sectionRef.current) {
      observer.observe(sectionRef.current)
    }

    return () => observer.disconnect()
  }, [])

  return (
    <section id="workflow" className="relative py-24 sm:py-36 overflow-hidden" ref={sectionRef}>
      {/* Background decoration */}
      <div className="absolute inset-0 -z-10">
        <div className="absolute left-1/2 top-1/2 h-[600px] w-[600px] -translate-x-1/2 -translate-y-1/2 rounded-full bg-accent/5 blur-[120px]" />
      </div>

      <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        {/* Section Header */}
        <div className="text-center max-w-3xl mx-auto">
          <span className="inline-block mb-4 text-sm font-medium text-accent tracking-wider uppercase">
            快速上手
          </span>
          <h2 className="text-3xl font-bold tracking-tight sm:text-5xl">
            简单四步，开始使用
          </h2>
          <p className="mx-auto mt-6 max-w-2xl text-lg text-muted-foreground leading-relaxed">
            我们简化了使用流程，让您能够快速上手并享受智能助手带来的便利
          </p>
        </div>

        {/* Steps */}
        <div className="mt-20">
          <div className="relative">
            {/* Connection Line - Desktop */}
            <div className="absolute left-0 right-0 top-[60px] hidden h-px bg-gradient-to-r from-transparent via-accent/30 to-transparent lg:block" />

            <div className="grid gap-8 lg:grid-cols-4">
              {steps.map((step, index) => (
                <div 
                  key={step.title} 
                  className={cn(
                    "relative flex flex-col items-center text-center transition-all duration-700",
                    activeStep >= index 
                      ? "opacity-100 translate-y-0" 
                      : "opacity-0 translate-y-8"
                  )}
                >
                  {/* Step Number & Icon */}
                  <div className="relative z-10">
                    {/* Outer ring */}
                    <div className={cn(
                      "absolute inset-0 rounded-full transition-all duration-500",
                      activeStep >= index 
                        ? "bg-accent/20 scale-110 animate-pulse-glow" 
                        : "bg-muted scale-100"
                    )} />
                    
                    {/* Inner circle */}
                    <div className={cn(
                      "relative flex h-[120px] w-[120px] flex-col items-center justify-center rounded-full border-2 transition-all duration-500",
                      activeStep >= index 
                        ? "border-accent bg-background shadow-xl shadow-accent/20" 
                        : "border-border bg-background"
                    )}>
                      <span className={cn(
                        "text-xs font-semibold tracking-wider uppercase transition-colors",
                        activeStep >= index ? "text-accent" : "text-muted-foreground"
                      )}>
                        步骤 {index + 1}
                      </span>
                      <step.icon className={cn(
                        "mt-2 h-10 w-10 transition-colors",
                        activeStep >= index ? "text-accent" : "text-muted-foreground"
                      )} />
                    </div>
                  </div>

                  {/* Content */}
                  <h3 className="mt-8 text-xl font-semibold">{step.title}</h3>
                  <p className="mt-3 text-muted-foreground max-w-[200px]">{step.description}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
