"use client"

import dynamic from 'next/dynamic'

const AIAssistantUI = dynamic(() => import('../components/AIAssistantUI'), { ssr: false })

export default function Page() {
  return <AIAssistantUI />
}
