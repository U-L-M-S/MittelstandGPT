import { type KeyboardEvent, useEffect, useRef, useState } from 'react'
import { SendIcon, StopIcon } from './icons'

interface ComposerProps {
  onSend: (text: string) => void
  onStop: () => void
  busy: boolean
}

export function Composer({ onSend, onStop, busy }: ComposerProps) {
  const [value, setValue] = useState('')
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 200)}px`
  }, [value])

  const submit = () => {
    const text = value.trim()
    if (!text || busy) return
    onSend(text)
    setValue('')
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <div className="border-t border-slate-200/70 bg-white/80 backdrop-blur-md dark:border-slate-800/70 dark:bg-slate-950/80">
      <div className="mx-auto max-w-3xl px-4 py-3 sm:px-6">
        <div className="flex items-end gap-2 rounded-2xl border border-slate-200 bg-white p-2 shadow-sm transition focus-within:border-accent focus-within:ring-2 focus-within:ring-accent/30 dark:border-slate-700 dark:bg-slate-900">
          <textarea
            ref={textareaRef}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            onKeyDown={handleKeyDown}
            rows={1}
            placeholder="Frage zu Ihren Dokumenten eingeben …"
            className="max-h-[200px] flex-1 resize-none bg-transparent px-2 py-1.5 text-[15px] leading-relaxed text-slate-800 placeholder:text-slate-400 focus:outline-none dark:text-slate-100 dark:placeholder:text-slate-500"
          />
          {busy ? (
            <button
              type="button"
              onClick={onStop}
              aria-label="Antwort stoppen"
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-slate-200 text-slate-600 transition hover:bg-slate-300 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent dark:bg-slate-700 dark:text-slate-200 dark:hover:bg-slate-600"
            >
              <StopIcon width={16} height={16} />
            </button>
          ) : (
            <button
              type="button"
              onClick={submit}
              disabled={!value.trim()}
              aria-label="Frage senden"
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-accent-solid text-white shadow-sm transition hover:opacity-90 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed disabled:opacity-40"
            >
              <SendIcon width={18} height={18} />
            </button>
          )}
        </div>
        <p className="mt-1.5 text-center text-[11px] text-slate-400 dark:text-slate-500">
          <kbd className="font-sans">Enter</kbd> zum Senden ·{' '}
          <kbd className="font-sans">Umschalt</kbd>+<kbd className="font-sans">Enter</kbd> für neue Zeile
        </p>
      </div>
    </div>
  )
}
