import { type DragEvent, useEffect, useRef, useState } from 'react'
import type { DocumentInfo } from '../lib/types'
import { CloseIcon, DocsIcon, FileIcon, UploadIcon } from './icons'

interface DocsDrawerProps {
  open: boolean
  onClose: () => void
  documents: DocumentInfo[]
  loading: boolean
  error: string | null
  onUpload: (files: File[]) => void
  uploadStatus: string | null
  uploadError: string | null
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export function DocsDrawer({
  open,
  onClose,
  documents,
  loading,
  error,
  onUpload,
  uploadStatus,
  uploadError,
}: DocsDrawerProps) {
  const asideRef = useRef<HTMLElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const [dragging, setDragging] = useState(false)
  const uploading = uploadStatus !== null

  // When closed, the panel is off-screen but still in the DOM — `inert` removes
  // its controls from the tab order and hides them from assistive tech.
  // (Focus is moved into the panel on open and restored on close from App.)
  useEffect(() => {
    if (asideRef.current) asideRef.current.inert = !open
  }, [open])

  const handleDrop = (event: DragEvent<HTMLButtonElement>) => {
    event.preventDefault()
    setDragging(false)
    if (uploading) return
    const files = Array.from(event.dataTransfer.files)
    if (files.length) onUpload(files)
  }

  return (
    <>
      <div
        onClick={onClose}
        aria-hidden
        className={`fixed inset-0 z-30 bg-slate-900/40 backdrop-blur-sm transition-opacity duration-300 ${
          open ? 'opacity-100' : 'pointer-events-none opacity-0'
        }`}
      />
      <aside
        ref={asideRef}
        id="docs-drawer"
        tabIndex={-1}
        role="dialog"
        aria-label="Dokumente"
        aria-modal="true"
        className={`fixed inset-y-0 right-0 z-40 flex w-full max-w-sm flex-col border-l border-slate-200 bg-white shadow-2xl transition-transform duration-300 ease-out focus:outline-none dark:border-slate-800 dark:bg-slate-950 ${
          open ? 'translate-x-0' : 'translate-x-full'
        }`}
      >
        <header className="flex items-center justify-between border-b border-slate-200/70 px-5 py-4 dark:border-slate-800/70">
          <h2 className="text-base font-semibold tracking-tight text-slate-900 dark:text-slate-100">
            Dokumente
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Schließen"
            className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-slate-800 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100"
          >
            <CloseIcon width={18} height={18} />
          </button>
        </header>

        <div className="scrollbar-soft flex-1 overflow-y-auto px-5 py-4">
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            onDragOver={(e) => {
              e.preventDefault()
              if (!uploading) setDragging(true)
            }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            disabled={uploading}
            className={`flex w-full flex-col items-center gap-2 rounded-xl border border-dashed px-4 py-6 text-center transition focus:outline-none focus-visible:ring-2 focus-visible:ring-accent disabled:cursor-not-allowed disabled:opacity-60 ${
              dragging
                ? 'border-accent bg-accent/5'
                : 'border-slate-300 hover:border-accent hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-900'
            }`}
          >
            <span className="text-accent">
              <UploadIcon width={22} height={22} />
            </span>
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
              Dateien hierher ziehen oder <span className="text-accent-strong">durchsuchen</span>
            </span>
            <span className="text-xs text-slate-400 dark:text-slate-500">
              PDF, DOCX oder TXT · max. 50 MB
            </span>
          </button>
          <input
            ref={inputRef}
            type="file"
            multiple
            accept=".pdf,.docx,.doc,.txt"
            className="hidden"
            onChange={(e) => {
              if (e.target.files) onUpload(Array.from(e.target.files))
              e.target.value = ''
            }}
          />

          {uploading && (
            <div className="mt-3 flex items-center gap-2 rounded-lg bg-accent/10 px-3 py-2 text-sm text-accent-strong">
              <span className="h-3.5 w-3.5 shrink-0 animate-spin rounded-full border-2 border-accent/30 border-t-accent" />
              <span className="truncate">{uploadStatus}</span>
            </div>
          )}
          {uploadError && (
            <p className="mt-3 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600 dark:bg-rose-950/40 dark:text-rose-400">
              {uploadError}
            </p>
          )}

          <div className="mt-6">
          {loading ? (
            <p className="text-sm text-slate-500 dark:text-slate-400">Dokumente werden geladen …</p>
          ) : error ? (
            <p className="text-sm text-rose-600 dark:text-rose-400">{error}</p>
          ) : documents.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <span className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500">
                <DocsIcon width={24} height={24} />
              </span>
              <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
                Noch keine Dokumente
              </p>
              <p className="mt-1 max-w-[16rem] text-xs text-slate-400 dark:text-slate-500">
                Laden Sie PDF-, DOCX- oder TXT-Dateien hoch, um Fragen dazu zu stellen.
              </p>
            </div>
          ) : (
            <ul className="flex flex-col gap-2">
              {documents.map((doc) => (
                <li
                  key={doc.filename}
                  className="flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50/60 px-3 py-2.5 dark:border-slate-800 dark:bg-slate-900/60"
                >
                  <span className="mt-0.5 text-accent">
                    <FileIcon width={18} height={18} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-slate-800 dark:text-slate-100">
                      {doc.filename}
                    </p>
                    <p className="text-xs text-slate-400 dark:text-slate-500">
                      {doc.chunks} Abschnitt{doc.chunks === 1 ? '' : 'e'} · {formatSize(doc.sizeBytes)}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
          </div>
        </div>
      </aside>
    </>
  )
}
