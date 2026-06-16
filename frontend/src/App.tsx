import { useCallback, useEffect, useRef, useState } from 'react'
import { Composer } from './components/Composer'
import { DocsDrawer } from './components/DocsDrawer'
import { EmptyState } from './components/EmptyState'
import { MessageList } from './components/MessageList'
import { TopBar } from './components/TopBar'
import { useTheme } from './hooks/useTheme'
import { fetchDocuments, streamChat, uploadDocument } from './lib/api'
import type { DocumentInfo, Message } from './lib/types'

let idCounter = 0
const nextId = () => `m${Date.now()}-${idCounter++}`

const ACCEPTED_EXTENSIONS = ['.pdf', '.docx', '.doc', '.txt']
const MAX_FILE_BYTES = 50 * 1024 * 1024 // matches the backend multipart limit

export default function App() {
  const { theme, toggle } = useTheme()
  const [messages, setMessages] = useState<Message[]>([])
  const [busy, setBusy] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const [docsOpen, setDocsOpen] = useState(false)
  const [documents, setDocuments] = useState<DocumentInfo[]>([])
  const [docsLoading, setDocsLoading] = useState(false)
  const [docsError, setDocsError] = useState<string | null>(null)
  const [uploadStatus, setUploadStatus] = useState<string | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const shellRef = useRef<HTMLDivElement>(null)
  const restoreFocusRef = useRef<HTMLElement | null>(null)

  const loadDocuments = useCallback(async () => {
    setDocsLoading(true)
    setDocsError(null)
    try {
      setDocuments(await fetchDocuments())
    } catch (error) {
      setDocsError((error as Error).message)
    } finally {
      setDocsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadDocuments()
  }, [loadDocuments])

  // Abort any in-flight stream if the app unmounts.
  useEffect(() => () => abortRef.current?.abort(), [])

  // Esc closes the documents drawer.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setDocsOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  // Dialog behaviour: make the background inert while the drawer is open, move
  // focus into the drawer, and restore it to the trigger when it closes.
  useEffect(() => {
    const shell = shellRef.current
    if (docsOpen) {
      restoreFocusRef.current = (document.activeElement as HTMLElement) ?? null
      if (shell) shell.inert = true
      document.getElementById('docs-drawer')?.focus()
    } else {
      if (shell) shell.inert = false
      restoreFocusRef.current?.focus()
      restoreFocusRef.current = null
    }
  }, [docsOpen])

  const openDocs = () => {
    setDocsOpen(true)
    loadDocuments()
  }

  const handleUpload = useCallback(
    async (files: File[]) => {
      setUploadError(null)
      const accepted: File[] = []
      const rejected: string[] = []
      for (const file of files) {
        const okType = ACCEPTED_EXTENSIONS.some((ext) => file.name.toLowerCase().endsWith(ext))
        if (!okType) rejected.push(`${file.name} (Format)`)
        else if (file.size > MAX_FILE_BYTES) rejected.push(`${file.name} (zu groß, max. 50 MB)`)
        else accepted.push(file)
      }
      if (rejected.length) {
        setUploadError(
          `Übersprungen: ${rejected.join(', ')}. Erlaubt sind PDF, DOCX und TXT (max. 50 MB).`,
        )
      }
      if (accepted.length === 0) return

      try {
        for (const file of accepted) {
          setUploadStatus(`„${file.name}“ wird analysiert …`)
          await uploadDocument(file)
        }
        await loadDocuments()
      } catch (error) {
        setUploadError((error as Error).message)
      } finally {
        setUploadStatus(null)
      }
    },
    [loadDocuments],
  )

  const patchMessage = (id: string, patch: Partial<Message>) =>
    setMessages((prev) => prev.map((m) => (m.id === id ? { ...m, ...patch } : m)))

  const send = useCallback(
    async (text: string) => {
      if (busy) return
      const assistantId = nextId()
      setMessages((prev) => [
        ...prev,
        { id: nextId(), role: 'user', content: text },
        { id: assistantId, role: 'assistant', content: '', streaming: true },
      ])
      setBusy(true)
      setAnnouncement('Antwort wird erstellt …')

      const controller = new AbortController()
      abortRef.current = controller
      let answer = ''

      await streamChat(text, {
        signal: controller.signal,
        onToken: (token) => {
          answer += token
          patchMessage(assistantId, { content: answer })
        },
        onSources: (sources) => patchMessage(assistantId, { sources }),
        onError: (message) => {
          if (answer.length === 0) answer = message
          patchMessage(assistantId, { content: answer, error: answer === message })
        },
      })

      const wasAborted = controller.signal.aborted
      patchMessage(assistantId, { streaming: false, aborted: wasAborted })
      setAnnouncement(wasAborted ? 'Antwort abgebrochen.' : answer)
      setBusy(false)
      abortRef.current = null
    },
    [busy],
  )

  const stop = () => abortRef.current?.abort()

  return (
    <div className="h-full">
      <div
        ref={shellRef}
        className="flex h-full flex-col bg-slate-50 font-sans text-slate-900 transition-colors dark:bg-slate-950 dark:text-slate-100"
      >
        <TopBar theme={theme} onToggleTheme={toggle} onOpenDocs={openDocs} docCount={documents.length} />

        <main className="scrollbar-soft flex-1 overflow-y-auto">
          {messages.length === 0 ? (
            <EmptyState onPick={send} docCount={documents.length} />
          ) : (
            <MessageList messages={messages} onSelectSource={openDocs} />
          )}
        </main>

        <Composer onSend={send} onStop={stop} busy={busy} />
      </div>

      {/* Announces the completed answer to screen readers (not per token). */}
      <div aria-live="polite" role="status" className="sr-only">
        {announcement}
      </div>

      <DocsDrawer
        open={docsOpen}
        onClose={() => setDocsOpen(false)}
        documents={documents}
        loading={docsLoading}
        error={docsError}
        onUpload={handleUpload}
        uploadStatus={uploadStatus}
        uploadError={uploadError}
      />
    </div>
  )
}
