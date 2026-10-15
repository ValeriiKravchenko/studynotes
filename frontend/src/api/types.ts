export interface NoteRequest {
  title: string
  content: string
  folderId: number | null
}

export interface NoteResponse {
  id: number
  title: string
  content: string | null
  folderName: string | null
  folderPath: string | null
  folderId: number | null
  createdAt: string
  updatedAt: string | null
}

export interface FolderResponse {
  id: number
  name: string
  parentId: number | null
  noteCount: number
}

export interface HeadingInfo {
  level: number
  text: string
  slug: string
}

export interface HeadingsResult {
  html: string
  headings: HeadingInfo[]
}

export interface ImportResult {
  total: number
  imported: number
  skipped: number
  ignored: number
  errors: string[]
}

export interface LoginRequest {
  username: string
  password: string
}

export interface UserResponse {
  username: string
}

export interface MarkdownPreviewRequest {
  content: string
}

export interface FieldError {
  field: string
  message: string
}

export interface ErrorResponse {
  status: number
  error: string
  message: string
  timestamp: string
  fieldErrors?: FieldError[]
}
