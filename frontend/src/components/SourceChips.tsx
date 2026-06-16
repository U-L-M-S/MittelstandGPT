import type { Source } from '../lib/types'
import { FileIcon } from './icons'

interface SourceChipsProps {
  sources: Source[]
  onSelect?: () => void
}

export function SourceChips({ sources, onSelect }: SourceChipsProps) {
  if (!sources.length) return null

  return (
    <div className="mt-3.5 border-t border-slate-200/70 pt-3 dark:border-slate-700/60">
      <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wider text-slate-400 dark:text-slate-500">
        Quellen
      </p>
      <div className="flex flex-wrap gap-1.5">
        {sources.map((source, index) => {
          const label = source.page != null ? `${source.source} · S. ${source.page}` : source.source
          return (
            <button
              key={`${source.source}-${source.page}-${index}`}
              type="button"
              onClick={() => onSelect?.()}
              title={label}
              className="inline-flex max-w-full items-center gap-1.5 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs text-slate-600 transition hover:border-accent hover:text-accent-strong focus:outline-none focus-visible:ring-2 focus-visible:ring-accent dark:border-slate-700 dark:bg-slate-800/60 dark:text-slate-300 dark:hover:text-accent-strong"
            >
              <FileIcon width={13} height={13} className="shrink-0 text-accent" />
              <span className="truncate">{label}</span>
            </button>
          )
        })}
      </div>
    </div>
  )
}
