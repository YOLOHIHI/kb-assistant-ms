'use client'

import { useState, useEffect } from 'react'
import { Button } from '@/components/ui/button'
import { Moon, Sun, Menu, X, Sparkles } from 'lucide-react'
import { cn } from '@/lib/utils'

interface LandingHeaderProps {
  isDark: boolean
  isLoggedIn: boolean
  onToggleTheme: () => void
  onLogin: () => void
  onRegister: () => void
  onLogout: () => void
  onEnterSystem: () => void
}

const navLinks = [
  { href: '#features', label: '功能特性' },
  { href: '#workflow', label: '使用流程' },
  { href: '#cta', label: '立即体验' },
]

export default function LandingHeader({
  isDark,
  isLoggedIn,
  onToggleTheme,
  onLogin,
  onRegister,
  onLogout,
  onEnterSystem,
}: LandingHeaderProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20)
    }
    window.addEventListener('scroll', handleScroll)
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  const scrollToSection = (href: string) => {
    const element = document.querySelector(href)
    if (element) {
      element.scrollIntoView({ behavior: 'smooth' })
    }
    setMenuOpen(false)
  }

  return (
    <header 
      className={cn(
        'fixed top-0 left-0 right-0 z-50 transition-all duration-500',
        scrolled 
          ? 'glass border-b shadow-sm' 
          : 'bg-transparent'
      )}
    >
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        {/* Logo */}
        <div className="flex items-center gap-3 group">
          <div className="relative flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-accent to-accent/70 shadow-lg shadow-accent/20 transition-transform duration-300 group-hover:scale-105">
            <Sparkles className="h-5 w-5 text-accent-foreground" />
            <div className="absolute inset-0 rounded-xl bg-accent/20 blur-xl opacity-0 group-hover:opacity-100 transition-opacity" />
          </div>
          <span className="text-xl font-bold tracking-tight">知识助手</span>
        </div>

        {/* Desktop Nav */}
        <nav className="hidden items-center gap-1 md:flex">
          {navLinks.map((link) => (
            <button
              key={link.href}
              onClick={() => scrollToSection(link.href)}
              className="relative px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground group"
            >
              {link.label}
              <span className="absolute inset-x-4 -bottom-px h-px bg-accent scale-x-0 transition-transform group-hover:scale-x-100" />
            </button>
          ))}
        </nav>

        {/* Desktop Actions */}
        <div className="hidden items-center gap-2 md:flex">
          <Button 
            variant="ghost" 
            size="icon" 
            onClick={onToggleTheme}
            className="rounded-full hover:bg-muted"
          >
            <div className="relative h-5 w-5">
              <Sun className={cn(
                "absolute inset-0 h-5 w-5 transition-all duration-300",
                isDark ? "rotate-0 scale-100 opacity-100" : "rotate-90 scale-0 opacity-0"
              )} />
              <Moon className={cn(
                "absolute inset-0 h-5 w-5 transition-all duration-300",
                isDark ? "-rotate-90 scale-0 opacity-0" : "rotate-0 scale-100 opacity-100"
              )} />
            </div>
          </Button>
          {isLoggedIn ? (
            <>
              <Button variant="ghost" onClick={onLogout} className="rounded-full">
                退出登录
              </Button>
              <Button 
                onClick={onEnterSystem} 
                className="rounded-full bg-accent text-accent-foreground hover:bg-accent/90 shadow-lg shadow-accent/20"
              >
                进入系统
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" onClick={onLogin} className="rounded-full">
                登录
              </Button>
              <Button 
                onClick={onRegister} 
                className="rounded-full bg-accent text-accent-foreground hover:bg-accent/90 shadow-lg shadow-accent/20"
              >
                注册
              </Button>
            </>
          )}
        </div>

        {/* Mobile Menu Button */}
        <div className="flex items-center gap-2 md:hidden">
          <Button 
            variant="ghost" 
            size="icon" 
            onClick={onToggleTheme}
            className="rounded-full"
          >
            {isDark ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
          </Button>
          <Button 
            variant="ghost" 
            size="icon" 
            onClick={() => setMenuOpen(!menuOpen)}
            className="rounded-full"
          >
            <div className="relative h-5 w-5">
              <X className={cn(
                "absolute inset-0 h-5 w-5 transition-all duration-300",
                menuOpen ? "rotate-0 scale-100 opacity-100" : "rotate-90 scale-0 opacity-0"
              )} />
              <Menu className={cn(
                "absolute inset-0 h-5 w-5 transition-all duration-300",
                menuOpen ? "-rotate-90 scale-0 opacity-0" : "rotate-0 scale-100 opacity-100"
              )} />
            </div>
          </Button>
        </div>
      </div>

      {/* Mobile Menu */}
      <div
        className={cn(
          'glass border-t md:hidden overflow-hidden transition-all duration-300',
          menuOpen ? 'max-h-96 opacity-100' : 'max-h-0 opacity-0'
        )}
      >
        <div className="space-y-1 px-4 py-4">
          {navLinks.map((link, index) => (
            <button
              key={link.href}
              onClick={() => scrollToSection(link.href)}
              className={cn(
                "block w-full rounded-xl px-4 py-3 text-left text-base font-medium text-muted-foreground hover:bg-muted hover:text-foreground transition-all",
                menuOpen && "animate-fade-up",
              )}
              style={{ animationDelay: `${index * 50}ms` }}
            >
              {link.label}
            </button>
          ))}
          <div className="flex flex-col gap-2 pt-4">
            {isLoggedIn ? (
              <>
                <Button variant="outline" onClick={onLogout} className="w-full rounded-xl">
                  退出登录
                </Button>
                <Button 
                  onClick={onEnterSystem} 
                  className="w-full rounded-xl bg-accent text-accent-foreground hover:bg-accent/90"
                >
                  进入系统
                </Button>
              </>
            ) : (
              <>
                <Button variant="outline" onClick={onLogin} className="w-full rounded-xl">
                  登录
                </Button>
                <Button 
                  onClick={onRegister} 
                  className="w-full rounded-xl bg-accent text-accent-foreground hover:bg-accent/90"
                >
                  注册
                </Button>
              </>
            )}
          </div>
        </div>
      </div>
    </header>
  )
}
