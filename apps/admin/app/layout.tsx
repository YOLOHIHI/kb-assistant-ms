import type { Metadata } from 'next'
import localFont from 'next/font/local'
import './globals.css'

const geistSans = localFont({
  src: [
    {
      path: '../public/fonts/geist-latin.woff2',
      style: 'normal',
      weight: '100 900',
    },
    {
      path: '../public/fonts/geist-latin-ext.woff2',
      style: 'normal',
      weight: '100 900',
    },
  ],
  display: 'swap',
  variable: '--font-geist-sans',
})

const geistMono = localFont({
  src: [
    {
      path: '../public/fonts/geist-mono-latin.woff2',
      style: 'normal',
      weight: '100 900',
    },
    {
      path: '../public/fonts/geist-mono-latin-ext.woff2',
      style: 'normal',
      weight: '100 900',
    },
  ],
  display: 'swap',
  variable: '--font-geist-mono',
})

export const metadata: Metadata = {
  title: '管理后台 - 系统运营与配置中心',
  description: '管理系统用户、AI模型配置、知识库和租户',
  icons: {
    icon: [
      {
        url: '/icon-light-32x32.png',
        media: '(prefers-color-scheme: light)',
      },
      {
        url: '/icon-dark-32x32.png',
        media: '(prefers-color-scheme: dark)',
      },
      {
        url: '/icon.svg',
        type: 'image/svg+xml',
      },
    ],
    apple: '/apple-icon.png',
  },
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="zh-CN">
      <body className={`${geistSans.variable} ${geistMono.variable} font-sans antialiased`}>
        {children}
      </body>
    </html>
  )
}
