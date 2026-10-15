import type { ErrorResponse, FieldError } from './types'

const UNSAFE_METHODS = new Set(['POST', 'PUT', 'DELETE', 'PATCH'])

export class ApiError extends Error {
  status: number
  error?: string
  fieldErrors?: FieldError[]

  constructor(status: number, message: string, error?: string, fieldErrors?: FieldError[]) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.error = error
    this.fieldErrors = fieldErrors
  }
}

export function getCookie(name: string): string | undefined {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'))
  return match ? decodeURIComponent(match[1]) : undefined
}

export function getCsrfToken(): string | undefined {
  return getCookie('XSRF-TOKEN')
}

interface RequestOptions {
  method?: string
  body?: unknown
  formData?: FormData
  suppressAuthRedirect?: boolean
}

function isErrorResponse(value: unknown): value is ErrorResponse {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ErrorResponse).status === 'number' &&
    typeof (value as ErrorResponse).message === 'string'
  )
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase()

  const headers: Record<string, string> = {}

  let body: BodyInit | undefined
  if (options.formData !== undefined) {
    body = options.formData
  } else if (options.body !== undefined) {
    body = JSON.stringify(options.body)
    headers['Content-Type'] = 'application/json'
  }

  if (UNSAFE_METHODS.has(method)) {
    const token = getCsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const response = await fetch(path, {
    method,
    headers,
    body,
    credentials: 'include',
  })

  if (response.status === 401 && !options.suppressAuthRedirect && !path.startsWith('/api/auth/login')) {
    const current = window.location.pathname + window.location.search + window.location.hash
    window.location.assign('/login?redirect=' + encodeURIComponent(current))
    throw new ApiError(401, 'Требуется аутентификация')
  }

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  let data: unknown
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }

  if (!response.ok) {
    if (isErrorResponse(data)) {
      throw new ApiError(data.status, data.message, data.error, data.fieldErrors)
    }
    throw new ApiError(response.status, response.statusText)
  }

  return data as T
}
