import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { notesApi } from '../api/endpoints'
import type { NoteResponse } from '../api/types'
import { formatDate } from '../lib/date'

function preview(content: string | null): string {
  const text = content ?? ''
  return text.length > 200 ? `${text.substring(0, 200)}...` : text
}

export default function SearchPage() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<NoteResponse[] | null>(null)
  const [searchedQuery, setSearchedQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const debounceRef = useRef<number | undefined>(undefined)

  useEffect(() => {
    window.clearTimeout(debounceRef.current)
    const trimmed = query.trim()

    if (!trimmed) {
      setResults(null)
      setError(null)
      setLoading(false)
      return
    }

  setLoading(true)
  setError(null)
  let active = true
  debounceRef.current = window.setTimeout(() => {
    notesApi
      .search(trimmed)
      .then((data) => {
        if (!active) return
        setResults(data)
        setSearchedQuery(trimmed)
      })
      .catch((err) => {
        if (!active) return
        setResults(null)
        setError(err instanceof Error ? err.message : 'Не удалось выполнить поиск')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
  }, 300)

  return () => {
    active = false
    window.clearTimeout(debounceRef.current)
  }
}, [query])

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-4 text-center text-xl font-semibold text-slate-900">Поиск по заметкам</h1>

      <div className="mb-4 flex overflow-hidden rounded-full border-2 border-[var(--accent)] bg-white">
        <input
          type="text"
          autoFocus
          maxLength={200}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Введите запрос..."
          className="flex-1 bg-transparent px-5 py-2.5 text-sm outline-none"
        />
      </div>

      {loading && <p className="text-slate-500">Поиск…</p>}

      {error && (
        <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
      )}

      {!loading && !error && results === null && (
        <p className="italic text-slate-500">Введите запрос для поиска.</p>
      )}

      {!loading && !error && results !== null && results.length === 0 && (
        <div className="italic text-slate-500">
          <p>
            По запросу «<strong>{searchedQuery}</strong>» ничего не найдено.
          </p>
          <p>Попробуйте другие ключевые слова.</p>
        </div>
      )}

      {!loading && !error && results !== null && results.length > 0 && (
        <>
          <p className="mb-3 text-slate-500">
            Найдено: <strong>{results.length}</strong> заметок по запросу «
            <strong>{searchedQuery}</strong>»
          </p>
          <div className="space-y-3">
            {results.map((note) => (
              <div
                key={note.id}
                className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm"
              >
                <Link
                  to={`/notes/${note.id}`}
                  className="text-lg font-medium text-slate-900 hover:text-[var(--accent-hover)]"
                >
                  {note.title}
                </Link>
                <div className="mt-1 flex items-center gap-2 text-sm text-slate-500">
                  {note.folderPath != null && (
                    <span className="rounded-full bg-[var(--accent-light)] px-2 py-0.5 text-xs font-medium text-[var(--accent-hover)]">
                      {note.folderPath}
                    </span>
                  )}
                  <span>{formatDate(note.createdAt)}</span>
                </div>
                {note.content != null && note.content.length > 0 && (
                  <p className="mt-2 whitespace-pre-wrap text-sm text-slate-500">
                    {preview(note.content)}
                  </p>
                )}
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}
