import { useEffect, useState } from 'react'

type BackendStatus = 'checking' | 'online' | 'offline'

/**
 * Phase 0 placeholder page. It verifies the full chain
 * (browser → Vite proxy → backend) by polling /api/health.
 * The real chat UI replaces this in Phase 4.
 */
export default function App() {
  const [status, setStatus] = useState<BackendStatus>('checking')

  useEffect(() => {
    // Respect the OS dark/light preference (full toggle comes in Phase 4).
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    document.documentElement.classList.toggle('dark', prefersDark)
  }, [])

  useEffect(() => {
    let cancelled = false
    const check = async () => {
      try {
        const res = await fetch('/api/health')
        if (!cancelled) setStatus(res.ok ? 'online' : 'offline')
      } catch {
        if (!cancelled) setStatus('offline')
      }
    }
    check()
    const id = setInterval(check, 5000)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [])

  const badge = {
    checking: { text: 'Verbindung wird geprüft …', color: 'bg-amber-400' },
    online: { text: 'Backend verbunden', color: 'bg-emerald-500' },
    offline: { text: 'Backend nicht erreichbar', color: 'bg-rose-500' },
  }[status]

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-6 font-sans text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h1 className="text-2xl font-semibold tracking-tight">MittelstandGPT</h1>
        <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
          Selbst-gehosteter, DSGVO-konformer Wissensassistent
        </p>

        <div className="mt-6 inline-flex items-center gap-2 rounded-full bg-slate-100 px-4 py-2 text-sm dark:bg-slate-800">
          <span className={`h-2.5 w-2.5 rounded-full ${badge.color}`} />
          {badge.text}
        </div>

        <p className="mt-6 text-xs text-slate-400 dark:text-slate-500">
          Phase 0 — Grundgerüst läuft. Chat &amp; Upload folgen in den nächsten Phasen.
        </p>
      </div>
    </div>
  )
}
