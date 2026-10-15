import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { foldersApi, notesApi } from '../api/endpoints'
import type { FolderResponse, HeadingInfo, NoteResponse } from '../api/types'
import { formatDate } from '../lib/date'

interface FolderNode {
  folder: FolderResponse
  children: FolderNode[]
}

function buildTree(folders: FolderResponse[]): FolderNode[] {
  const byParent = new Map<number | null, FolderResponse[]>()
  for (const folder of folders) {
    const list = byParent.get(folder.parentId) ?? []
    list.push(folder)
    byParent.set(folder.parentId, list)
  }
  const sortByName = (list: FolderResponse[]) =>
    list.sort((a, b) => a.name.localeCompare(b.name, 'ru'))
  const build = (parentId: number | null): FolderNode[] =>
    sortByName(byParent.get(parentId) ?? []).map((folder) => ({
      folder,
      children: build(folder.id),
    }))
  return build(null)
}

function FolderBranch({
  node,
  depth,
  selectedId,
  onSelect,
}: {
  node: FolderNode
  depth: number
  selectedId: number | null
  onSelect: (id: number) => void
}) {
  const isSelected = selectedId === node.folder.id
  return (
    <div>
      <button
        type="button"
        onClick={() => onSelect(node.folder.id)}
        className={`flex w-full items-center justify-between rounded-md px-3 py-1.5 text-left text-sm ${
          isSelected
            ? 'bg-[var(--accent-light)] font-medium text-[var(--accent-hover)]'
            : 'text-slate-700 hover:bg-slate-100'
        }`}
        style={{ paddingLeft: 12 + depth * 16 }}
      >
        <span className="truncate">
          {depth > 0 ? '↳ ' : ''}
          {node.folder.name}
        </span>
        <span className="ml-2 text-xs text-slate-400">{node.folder.noteCount}</span>
      </button>
      {node.children.map((child) => (
        <FolderBranch
          key={child.folder.id}
          node={child}
          depth={depth + 1}
          selectedId={selectedId}
          onSelect={onSelect}
        />
      ))}
    </div>
  )
}

export default function NotesListPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [folders, setFolders] = useState<FolderResponse[]>([])
  const [notes, setNotes] = useState<NoteResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [headings, setHeadings] = useState<Record<number, HeadingInfo[]>>({})
  const [loadingHeadings, setLoadingHeadings] = useState<Set<number>>(new Set())
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [showAll, setShowAll] = useState(false)

  const folderParam = searchParams.get('folderId')
  const parsed = folderParam == null ? null : Number(folderParam)
  const selectedFolderId = parsed != null && Number.isFinite(parsed) ? parsed : null

  const tree = useMemo(() => buildTree(folders), [folders])

  useEffect(() => {
    let active = true
    foldersApi.list().then((data) => {
      if (active) setFolders(data)
    })
    return () => {
      active = false
    }
  }, [])

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    setExpanded(new Set())
    setShowAll(false)
    notesApi
      .list(selectedFolderId ?? undefined)
      .then((data) => {
        if (active) setNotes(data)
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Не удалось загрузить заметки')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [selectedFolderId])

  useEffect(() => {
    if (!showAll) return
    const missing = notes.filter((note) => !(note.id in headings)).map((note) => note.id)
    if (missing.length === 0) return
    let active = true
    Promise.all(
      missing.map((id) =>
        notesApi
          .render(id)
          .then((res) => ({ id, headings: res.headings }))
          .catch(() => ({ id, headings: [] as HeadingInfo[] })),
      ),
    ).then((results) => {
      if (!active) return
      setHeadings((prev) => {
        const next = { ...prev }
        for (const res of results) next[res.id] = res.headings
        return next
      })
    })
    return () => {
      active = false
    }
  }, [showAll, notes, headings])

  async function toggleOutline(id: number) {
    if (expanded.has(id)) {
      setExpanded((prev) => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
      return
    }
    setExpanded((prev) => new Set(prev).add(id))
    if (id in headings) return
    setLoadingHeadings((prev) => new Set(prev).add(id))
    try {
      const res = await notesApi.render(id)
      setHeadings((prev) => ({ ...prev, [id]: res.headings }))
    } catch {
      setHeadings((prev) => ({ ...prev, [id]: [] }))
    } finally {
      setLoadingHeadings((prev) => {
        const next = new Set(prev)
        next.delete(id)
        return next
      })
    }
  }

  async function handleDelete(note: NoteResponse) {
    if (!window.confirm('Удалить заметку?')) return
    await notesApi.remove(note.id)
    setNotes((prev) => prev.filter((n) => n.id !== note.id))
    setHeadings((prev) => {
      const next = { ...prev }
      delete next[note.id]
      return next
    })
  }

  function selectFolder(id: number | null) {
    setSearchParams(id == null ? {} : { folderId: String(id) })
  }

  return (
    <div className="flex flex-col gap-6 lg:flex-row">
      <aside className="shrink-0 lg:w-64">
        <h2 className="mb-3 text-xs font-semibold uppercase tracking-wide text-slate-400">Папки</h2>
        <div className="space-y-0.5">
          <button
            type="button"
            onClick={() => selectFolder(null)}
            className={`flex w-full items-center justify-between rounded-md px-3 py-1.5 text-left text-sm ${
              selectedFolderId == null
                ? 'bg-[var(--accent-light)] font-medium text-[var(--accent-hover)]'
                : 'text-slate-700 hover:bg-slate-100'
            }`}
          >
            <span>Все заметки</span>
          </button>
          {tree.map((node) => (
            <FolderBranch
              key={node.folder.id}
              node={node}
              depth={0}
              selectedId={selectedFolderId}
              onSelect={selectFolder}
            />
          ))}
        </div>
      </aside>

      <section className="min-w-0 flex-1">
        <div className="mb-4 flex items-center justify-between gap-2">
          <h1 className="text-xl font-semibold text-slate-900">Мои заметки</h1>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setShowAll((prev) => !prev)}
              className="rounded-md px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100"
            >
              {showAll ? 'Скрыть оглавления' : 'Показать оглавления'}
            </button>
            <Link
              to="/notes/new"
              className="rounded-md bg-[var(--accent)] px-3 py-1.5 text-sm font-medium text-white hover:bg-[var(--accent-hover)]"
            >
              Создать заметку
            </Link>
          </div>
        </div>

        {loading && <p className="text-slate-500">Загрузка…</p>}

        {!loading && error && (
          <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
        )}

        {!loading && !error && notes.length === 0 && (
          <p className="italic text-slate-500">Заметок пока нет. Создайте первую!</p>
        )}

        {!loading && !error && notes.length > 0 && (
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Заголовок</th>
                  <th className="px-4 py-3 font-medium">Папка</th>
                  <th className="px-4 py-3 font-medium">Создано</th>
                  <th className="px-4 py-3 font-medium">Действия</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {notes.map((note) => {
                  const outline = headings[note.id]
                  const isVisible = showAll || expanded.has(note.id)
                  const hasHeadings = outline === undefined || outline.length > 0
                  return (
                    <tr key={note.id} className="hover:bg-slate-50">
                      <td className="px-4 py-3 align-top">
                        <div className="flex items-start gap-1">
                          <Link
                            to={`/notes/${note.id}`}
                            className="font-medium text-slate-900 hover:text-[var(--accent-hover)]"
                          >
                            {note.title}
                          </Link>
                          {hasHeadings && (
                            <button
                              type="button"
                              onClick={() => toggleOutline(note.id)}
                              title="Показать/скрыть оглавление"
                              className="mt-0.5 leading-none text-slate-400 hover:text-slate-600"
                            >
                              <span
                                className={`inline-block transition-transform ${isVisible ? 'rotate-90' : ''}`}
                              >
                                ›
                              </span>
                            </button>
                          )}
                        </div>
                        {isVisible && loadingHeadings.has(note.id) && (
                          <p className="mt-1 text-xs text-slate-400">Загрузка…</p>
                        )}
                        {isVisible && outline && outline.length > 0 && (
                          <ul className="mt-1 space-y-0.5 text-sm text-slate-500">
                            {outline.map((heading) => (
                              <li
                                key={heading.slug}
                                style={{ paddingLeft: Math.max(0, heading.level - 2) * 12 }}
                              >
                                <Link
                                  to={`/notes/${note.id}#${heading.slug}`}
                                  className="hover:text-[var(--accent-hover)]"
                                >
                                  {heading.text}
                                </Link>
                              </li>
                            ))}
                          </ul>
                        )}
                      </td>
                      <td className="px-4 py-3 align-top">
                        <span className="inline-block rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                          {note.folderPath ?? 'Корень'}
                        </span>
                      </td>
                      <td className="px-4 py-3 align-top whitespace-nowrap text-slate-500">
                        {formatDate(note.createdAt)}
                      </td>
                      <td className="px-4 py-3 align-top">
                        <div className="flex items-center gap-3">
                          <Link
                            to={`/notes/${note.id}/edit`}
                            className="text-sm text-slate-600 hover:text-[var(--accent-hover)]"
                          >
                            Редактировать
                          </Link>
                          <button
                            type="button"
                            onClick={() => handleDelete(note)}
                            className="text-sm text-red-500 hover:text-red-700"
                          >
                            Удалить
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
