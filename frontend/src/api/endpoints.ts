import { apiRequest } from './client'
import type {
  FolderResponse,
  HeadingsResult,
  ImportResult,
  LoginRequest,
  MarkdownPreviewRequest,
  NoteRequest,
  NoteResponse,
  UserResponse,
} from './types'

export const authApi = {
  login: (req: LoginRequest) =>
    apiRequest<UserResponse>('/api/auth/login', { method: 'POST', body: req }),
  me: () =>
    apiRequest<UserResponse>('/api/auth/me', { suppressAuthRedirect: true }),
  logout: () => apiRequest<void>('/api/auth/logout', { method: 'POST' }),
}

export const notesApi = {
  list: (folderId?: number) =>
    apiRequest<NoteResponse[]>(
      folderId != null ? `/api/notes?folderId=${folderId}` : '/api/notes',
    ),
  search: (query: string) =>
    apiRequest<NoteResponse[]>(`/api/notes/search?query=${encodeURIComponent(query)}`),
  get: (id: number) => apiRequest<NoteResponse>(`/api/notes/${id}`),
  create: (req: NoteRequest) =>
    apiRequest<NoteResponse>('/api/notes', { method: 'POST', body: req }),
  update: (id: number, req: NoteRequest) =>
    apiRequest<NoteResponse>(`/api/notes/${id}`, { method: 'PUT', body: req }),
  remove: (id: number) => apiRequest<void>(`/api/notes/${id}`, { method: 'DELETE' }),
  render: (id: number) => apiRequest<HeadingsResult>(`/api/notes/${id}/render`),
}

export const foldersApi = {
  list: () => apiRequest<FolderResponse[]>('/api/folders'),
}

export const markdownApi = {
  preview: (req: MarkdownPreviewRequest) =>
    apiRequest<HeadingsResult>('/api/markdown/preview', { method: 'POST', body: req }),
}

export const importApi = {
  importZip: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return apiRequest<ImportResult>('/api/import', { method: 'POST', formData })
  },
}
