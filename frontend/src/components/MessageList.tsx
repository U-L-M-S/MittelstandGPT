import { useEffect, useRef } from 'react'
import type { Message } from '../lib/types'
import { MessageBubble } from './MessageBubble'

interface MessageListProps {
  messages: Message[]
  onSelectSource?: () => void
}

export function MessageList({ messages, onSelectSource }: MessageListProps) {
  const endRef = useRef<HTMLDivElement>(null)

  // Keep pinned to the newest content (also follows the streaming answer).
  const lastContent = messages[messages.length - 1]?.content
  useEffect(() => {
    endRef.current?.scrollIntoView({ block: 'end' })
  }, [messages.length, lastContent])

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4 px-4 py-6 sm:px-6">
      {messages.map((message) => (
        <MessageBubble key={message.id} message={message} onSelectSource={onSelectSource} />
      ))}
      <div ref={endRef} />
    </div>
  )
}
