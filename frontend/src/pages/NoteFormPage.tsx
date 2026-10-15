import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { markdownApi, notesApi } from '../api/endpoints'
import type { FieldError, NoteResponse } from '../api/types'
import MarkdownToolbar from '../components/MarkdownToolbar'
import NoteContent from '../components/NoteContent'

export default function NoteFormPage() {
  const { id } = useParams()
  return <NoteForm key={id ?? 'new'} />
}

function NoteForm() {
  const { id } = useParams()
  const noteId = id == null ? null : Number(id)
  const isEdit = noteId != null
  const navigate = useNavigate()

  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [folderId, setFolderId] = useState<number | null>(null)
  const contentRef = useRef<HTMLTextAreaElement>(null)
  const pendingSelection = useRef<{ start: number; end: number } | null>(null)

  const [loading, setLoading] = useState(isEdit)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([])

  const [showPreview, setShowPreview] = useState(false)
  const [previewHtml, setPreviewHtml] = useState('')
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewError, setPreviewError] = useState<string | null>(null)

  useEffect(() => {
    if (noteId == null) return
    if (!Number.isFinite(noteId)) {
      setLoadError('Некорректный идентификатор заметки')
      setLoading(false)
      return
    }
    let active = true
    notesApi
      .get(noteId)
      .then((note) => {
        if (!active) return
        setTitle(note.title)
        setContent(note.content ?? '')
        setFolderId(note.folderId)
      })
      .catch((err) => {
        if (active)
          setLoadError(err instanceof Error ? err.message : 'Не удалось загрузить заметку')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [noteId])

  useEffect(() => {
    const pending = pendingSelection.current
    if (!pending || !contentRef.current) return
    contentRef.current.focus()
    contentRef.current.setSelectionRange(pending.start, pending.end)
    pendingSelection.current = null
  }, [content])

  function applyEdit(value: string, start: number, end: number) {
    pendingSelection.current = { start, end }
    setContent(value)
  }

  async function togglePreview() {
    if (showPreview) {
      setShowPreview(false)
      return
    }
    setShowPreview(true)
    setPreviewError(null)
    setPreviewLoading(true)
    try {
      const res = await markdownApi.preview({ content })
      setPreviewHtml(res.html)
    } catch (err) {
      setPreviewError(err instanceof Error ? err.message : 'Не удалось выполнить предпросмотр')
    } finally {
      setPreviewLoading(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setSubmitError(null)
    setFieldErrors([])
    try {
      const request = { title, content, folderId }
      const saved: NoteResponse = isEdit
        ? await notesApi.update(noteId, request)
        : await notesApi.create(request)
      navigate(isEdit ? `/notes/${saved.id}` : '/notes', { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        setSubmitError(err.message)
        setFieldErrors(err.fieldErrors ?? [])
      } else {
        setSubmitError('Не удалось сохранить заметку')
      }
      setSaving(false)
    }
  }

  function errorFor(field: string): string | undefined {
    return fieldErrors.find((e) => e.field === field)?.message
  }

  if (loading) {
    return <p className="text-slate-500">Загрузка…</p>
  }

  if (loadError) {
    return <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{loadError}</p>
  }

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="mb-4 text-xl font-semibold text-slate-900">
        {isEdit ? 'Редактировать заметку' : 'Новая заметка'}
      </h1>
      <p className="mb-4 text-sm text-slate-500">Markdown поддерживается</p>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="title" className="block text-sm font-medium text-slate-700">
            Заголовок
          </label>
          <input
            id="title"
            type="text"
            required
            maxLength={255}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Введите заголовок"
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-[var(--accent)] focus:outline-none focus:ring-2 focus:ring-[var(--accent-light)]"
          />
          {errorFor('title') && (
            <p className="mt-1 text-sm text-red-600">{errorFor('title')}</p>
          )}
        </div>

        <div>
          <label htmlFor="content" className="block text-sm font-medium text-slate-700">
            Содержимое
          </label>
          <div className="mt-1">
            <MarkdownToolbar textareaRef={contentRef} onApply={applyEdit} />
            <textarea
              ref={contentRef}
              id="content"
              rows={15}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="Введите содержимое в формате Markdown"
              className="md-textarea w-full border border-slate-300 px-3 py-2 text-sm focus:border-[var(--accent)] focus:outline-none"
            />
          </div>
          {errorFor('content') && (
            <p className="mt-1 text-sm text-red-600">{errorFor('content')}</p>
          )}
        </div>

        {submitError && (
          <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{submitError}</p>
        )}

        <div className="flex items-center justify-end gap-2">
          <button
            type="button"
            onClick={togglePreview}
            disabled={previewLoading}
            className="rounded-md border border-slate-300 px-4 py-1.5 text-sm text-slate-600 hover:bg-slate-100 disabled:opacity-60"
          >
            {showPreview ? 'Редактор' : 'Предпросмотр'}
          </button>
          <Link
            to={isEdit ? `/notes/${noteId}` : '/notes'}
            className="rounded-md px-4 py-1.5 text-sm text-slate-600 hover:bg-slate-100"
          >
            Отмена
          </Link>
          <button
            type="submit"
            disabled={saving}
            className="rounded-md bg-[var(--accent)] px-4 py-1.5 text-sm font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-60"
          >
            {saving ? 'Сохранение…' : 'Сохранить'}
          </button>
        </div>
      </form>

      {showPreview && (
        <div className="mt-6 rounded-lg border border-slate-200 bg-white p-4">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-400">
            Предпросмотр
          </h2>
          {previewLoading && <p className="text-slate-500">Загрузка…</p>}
          {previewError && (
            <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{previewError}</p>
          )}
          {!previewLoading && !previewError && <NoteContent html={previewHtml} />}
        </div>
      )}
    </div>
  )
}
