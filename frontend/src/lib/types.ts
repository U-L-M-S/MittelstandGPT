export interface Source {
  source: string
  page: number | null
}

export type Role = 'user' | 'assistant'

export interface Message {
  id: string
  role: Role
  content: string
  sources?: Source[]
  streaming?: boolean
  error?: boolean
}

export interface DocumentInfo {
  filename: string
  contentType: string | null
  sizeBytes: number
  chunks: number
  uploadedAt: string
}
