import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { notesApi } from '../api/endpoints'
import type { HeadingInfo, NoteResponse } from '../api/types'
import NoteContent from '../components/NoteContent'
import { formatDateTime } from '../lib/date'

export default function NoteViewPage() {
  const { id } = useParams()
  const noteId = Number(id)
  const navigate = useNavigate()

  const [note, setNote] = useState<NoteResponse | null>(null)
  const [html, setHtml] = useState('')
  const [headings, setHeadings] = useState<HeadingInfo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!Number.isFinite(noteId)) {
      setError('Некорректный идентификатор заметки')
      setLoading(false)
      return
    }
    let active = true
    setLoading(true)
    setError(null)
    Promise.all([notesApi.get(noteId), notesApi.render(noteId)])
      .then(([noteData, rendered]) => {
        if (!active) return
        setNote(noteData)
        setHtml(rendered.html)
        setHeadings(rendered.headings)
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Не удалось загрузить заметку')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [noteId])

  async function handleDelete() {
    if (!window.confirm('Удалить заметку? Это действие нельзя отменить.')) return
    await notesApi.remove(noteId)
    navigate('/notes', { replace: true })
  }

  if (loading) {
    return <p className="text-slate-500">Загрузка…</p>
  }

  if (error) {
    return <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
  }

  if (!note) {
    return null
  }

  return (
    <div className="mx-auto max-w-3xl">
      <nav aria-label="breadcrumb" className="mb-4 text-sm text-slate-500">
        <ol className="flex flex-wrap items-center gap-1.5">
          <li>
            <Link to="/notes" className="hover:text-[var(--accent-hover)]">
              Заметки
            </Link>
          </li>
          {note.folderPath != null && (
            <>
              <li aria-hidden="true">/</li>
              <li>{note.folderPath}</li>
            </>
          )}
          <li aria-hidden="true">/</li>
          <li className="truncate text-slate-700">{note.title}</li>
        </ol>
      </nav>

      <h1 className="mb-3 text-2xl font-semibold text-slate-900">{note.title}</h1>

      <div className="mb-4 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-slate-500">
        {note.folderPath != null && (
          <span className="rounded-full bg-[var(--accent-light)] px-2 py-0.5 text-xs font-medium text-[var(--accent-hover)]">
            {note.folderPath}
          </span>
        )}
        <span>Создано: {formatDateTime(note.createdAt)}</span>
        {note.updatedAt != null && <span>· Обновлено: {formatDateTime(note.updatedAt)}</span>}
      </div>

      {headings.length > 0 && (
        <details className="mb-4 rounded-lg border border-slate-200 bg-white px-4 py-2">
          <summary className="cursor-pointer select-none text-sm text-slate-600">
            Содержание
          </summary>
          <nav className="mt-2 border-l border-slate-200 pl-3">
            {headings.map((heading) => (
              <a
                key={heading.slug}
                href={`#${heading.slug}`}
                className={`mb-1 block text-sm text-slate-500 hover:text-[var(--accent-hover)] ${
                  heading.level >= 3 ? 'pl-3' : ''
                }`}
              >
                {heading.text}
              </a>
            ))}
          </nav>
        </details>
      )}

      <NoteContent html={html} />

      <div className="mt-6 mb-3 flex items-center gap-2">
        <Link
          to={`/notes/${note.id}/edit`}
          className="rounded-md border border-[var(--accent)] px-4 py-1.5 text-sm text-[var(--accent-hover)] hover:bg-[var(--accent-light)]"
        >
          Редактировать
        </Link>
        <button
          type="button"
          onClick={handleDelete}
          className="rounded-md bg-red-100 px-4 py-1.5 text-sm text-red-700 hover:bg-red-200"
        >
          Удалить
        </button>
      </div>
    </div>
  )
}
