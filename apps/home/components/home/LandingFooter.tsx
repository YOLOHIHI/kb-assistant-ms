'use client'

import { Sparkles, Github, Twitter } from 'lucide-react'

export default function LandingFooter() {
  const currentYear = new Date().getFullYear()

  const scrollToSection = (href: string) => {
    const element = document.querySelector(href)
    if (element) element.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <footer id="footer" className="relative border-t bg-muted/20">
      {/* Gradient decoration */}
      <div className="absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-accent/30 to-transparent" />
      
      <div className="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
        <div className="flex flex-col gap-12">
          {/* Top section */}
          <div className="flex flex-col items-center justify-between gap-8 sm:flex-row">
            {/* Logo */}
            <div className="flex items-center gap-3 group">
              <div className="relative flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-accent to-accent/70 shadow-lg shadow-accent/20">
                <Sparkles className="h-5 w-5 text-accent-foreground" />
              </div>
              <div>
                <span className="text-xl font-bold tracking-tight">知识助手</span>
                <p className="text-sm text-muted-foreground">智能知识管理平台</p>
              </div>
            </div>

            {/* Navigation */}
            <nav className="flex flex-wrap items-center justify-center gap-2">
              {[
                { href: '#features', label: '功能特性' },
                { href: '#workflow', label: '使用流程' },
                { href: '#cta', label: '立即体验' },
              ].map((link) => (
                <button
                  key={link.href}
                  onClick={() => scrollToSection(link.href)}
                  className="rounded-full px-4 py-2 text-sm text-muted-foreground transition-all hover:bg-muted hover:text-foreground"
                >
                  {link.label}
                </button>
              ))}
            </nav>

            {/* Social links */}
            <div className="flex items-center gap-2">
              <button className="flex h-10 w-10 items-center justify-center rounded-full text-muted-foreground transition-all hover:bg-muted hover:text-foreground">
                <Github className="h-5 w-5" />
              </button>
              <button className="flex h-10 w-10 items-center justify-center rounded-full text-muted-foreground transition-all hover:bg-muted hover:text-foreground">
                <Twitter className="h-5 w-5" />
              </button>
            </div>
          </div>

          {/* Divider */}
          <div className="h-px w-full bg-border" />

          {/* Bottom section */}
          <div className="flex flex-col items-center justify-between gap-4 sm:flex-row">
            <p className="text-sm text-muted-foreground">
              &copy; {currentYear} 知识助手. 保留所有权利.
            </p>
            <div className="flex items-center gap-6 text-sm text-muted-foreground">
              <button className="hover:text-foreground transition-colors">隐私政策</button>
              <button className="hover:text-foreground transition-colors">服务条款</button>
            </div>
          </div>
        </div>
      </div>
    </footer>
  )
}
