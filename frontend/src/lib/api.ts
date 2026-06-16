import type { DocumentInfo, Source } from './types'

interface StreamHandlers {
  onToken: (token: string) => void
  onSources: (sources: Source[]) => void
  onError: (message: string) => void
  signal?: AbortSignal
}

/**
 * Streams an answer from POST /api/chat/stream and parses the Server-Sent
 * Events (token / sources / done) manually — EventSource only supports GET.
 */
export async function streamChat(question: string, handlers: StreamHandlers): Promise<void> {
  let response: Response
  try {
    response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ question }),
      signal: handlers.signal,
    })
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      handlers.onError('Verbindung zum Server fehlgeschlagen. Läuft das Backend?')
    }
    return
  }

  if (!response.ok || !response.body) {
    handlers.onError('Der Server konnte die Anfrage nicht verarbeiten.')
    return
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })

      let separator: number
      while ((separator = buffer.indexOf('\n\n')) !== -1) {
        const rawEvent = buffer.slice(0, separator)
        buffer = buffer.slice(separator + 2)
        dispatch(rawEvent, handlers)
      }
    }
  } catch (error) {
    if ((error as Error).name !== 'AbortError') {
      handlers.onError('Die Übertragung wurde unterbrochen.')
    }
  }
}

function dispatch(rawEvent: string, handlers: StreamHandlers): void {
  let event = 'message'
  const dataLines: string[] = []

  for (const line of rawEvent.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      // Keep the value verbatim (incl. leading spaces in tokens like " GmbH").
      dataLines.push(line.slice(5))
    }
  }

  const data = dataLines.join('\n')
  if (event === 'token') {
    handlers.onToken(data)
  } else if (event === 'sources') {
    try {
      handlers.onSources(JSON.parse(data) as Source[])
    } catch {
      /* malformed sources payload — ignore */
    }
  } else if (event === 'error') {
    handlers.onError(data || 'Unbekannter Fehler.')
  }
  // 'done' → end of stream, nothing to do.
}

export async function fetchDocuments(): Promise<DocumentInfo[]> {
  const response = await fetch('/api/documents')
  if (!response.ok) {
    throw new Error('Dokumente konnten nicht geladen werden.')
  }
  return response.json()
}

export async function uploadDocument(file: File): Promise<DocumentInfo> {
  const form = new FormData()
  form.append('file', file)

  const response = await fetch('/api/documents', { method: 'POST', body: form })
  if (!response.ok) {
    let message = 'Die Datei konnte nicht verarbeitet werden.'
    try {
      const body = await response.json()
      if (body?.error) message = body.error
    } catch {
      /* keep default message */
    }
    throw new Error(message)
  }
  return response.json()
}
