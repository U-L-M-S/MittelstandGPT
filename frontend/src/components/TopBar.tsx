import type { Theme } from '../hooks/useTheme'
import { DocsIcon, MoonIcon, ShieldIcon, SunIcon } from './icons'

interface TopBarProps {
  theme: Theme
  onToggleTheme: () => void
  onOpenDocs: () => void
  docCount: number
}

export function TopBar({ theme, onToggleTheme, onOpenDocs, docCount }: TopBarProps) {
  const iconButton =
    'flex h-9 items-center justify-center rounded-lg text-slate-500 transition ' +
    'hover:bg-slate-100 hover:text-slate-800 focus:outline-none focus-visible:ring-2 ' +
    'focus-visible:ring-accent dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100'

  return (
    <header className="sticky top-0 z-20 border-b border-slate-200/70 bg-white/80 backdrop-blur-md dark:border-slate-800/70 dark:bg-slate-950/80">
      <div className="mx-auto flex h-14 max-w-3xl items-center justify-between px-4 sm:px-6">
        <div className="flex items-center gap-2.5">
          <span className="flex h-8 w-8 items-center justify-center rounded-xl bg-accent-solid text-white shadow-sm">
            <ShieldIcon width={18} height={18} />
          </span>
          <div className="leading-tight">
            <h1 className="text-[15px] font-semibold tracking-tight text-slate-900 dark:text-slate-100">
              MittelstandGPT
            </h1>
            <p className="text-[11px] text-slate-400 dark:text-slate-500">Lokal · DSGVO-konform</p>
          </div>
        </div>

        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={onOpenDocs}
            className={`${iconButton} gap-2 px-3`}
            aria-label="Dokumente verwalten"
          >
            <DocsIcon width={18} height={18} />
            <span className="hidden text-sm font-medium sm:inline">Dokumente</span>
            {docCount > 0 && (
              <span className="rounded-full bg-accent-solid px-1.5 text-[11px] font-semibold text-white">
                {docCount}
              </span>
            )}
          </button>
          <button
            type="button"
            onClick={onToggleTheme}
            className={`${iconButton} w-9`}
            aria-label={theme === 'dark' ? 'Zum hellen Modus wechseln' : 'Zum dunklen Modus wechseln'}
          >
            {theme === 'dark' ? <SunIcon width={18} height={18} /> : <MoonIcon width={18} height={18} />}
          </button>
        </div>
      </div>
    </header>
  )
}
