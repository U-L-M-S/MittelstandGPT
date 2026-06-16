import type { Message } from '../lib/types'
import { SourceChips } from './SourceChips'

interface MessageBubbleProps {
  message: Message
  onSelectSource?: () => void
}

function TypingDots() {
  return (
    <span className="flex items-center gap-1 py-1" aria-label="Antwort wird erstellt">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="h-1.5 w-1.5 animate-bounce-dot rounded-full bg-slate-400 dark:bg-slate-500"
          style={{ animationDelay: `${i * 0.16}s` }}
        />
      ))}
    </span>
  )
}

export function MessageBubble({ message, onSelectSource }: MessageBubbleProps) {
  if (message.role === 'user') {
    return (
      <div className="flex animate-fade-in-up justify-end">
        <div className="max-w-[85%] rounded-2xl rounded-br-md bg-accent-solid px-4 py-2.5 text-[15px] leading-relaxed text-white shadow-sm">
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
        </div>
      </div>
    )
  }

  const showDots = message.streaming && message.content.length === 0

  return (
    <div className="flex animate-fade-in-up justify-start">
      <div className="max-w-[90%] rounded-2xl rounded-bl-md border border-slate-200/80 bg-white px-4 py-3 text-[15px] leading-relaxed shadow-sm dark:border-slate-800 dark:bg-slate-900">
        {showDots ? (
          <TypingDots />
        ) : (
          <p
            className={`whitespace-pre-wrap break-words ${
              message.error
                ? 'text-rose-600 dark:text-rose-400'
                : 'text-slate-800 dark:text-slate-100'
            }`}
          >
            {message.content}
            {message.streaming && (
              <span className="ml-0.5 inline-block h-4 w-[2px] translate-y-0.5 animate-blink bg-accent align-middle" />
            )}
          </p>
        )}

        {message.sources && message.sources.length > 0 && (
          <SourceChips sources={message.sources} onSelect={onSelectSource} />
        )}
      </div>
    </div>
  )
}
