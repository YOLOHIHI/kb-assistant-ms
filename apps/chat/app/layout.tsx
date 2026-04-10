import type { Metadata } from 'next'
import { frontendConfig } from '../lib/app-config'
import './globals.css'
import 'katex/dist/katex.min.css'

export const metadata: Metadata = {
  title: `${frontendConfig.brandName} - 智能问答`,
  description: frontendConfig.description,
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="zh-CN">
      <body className="font-sans antialiased">
        {children}
      </body>
    </html>
  )
}
