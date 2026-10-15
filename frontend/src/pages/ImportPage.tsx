import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { importApi } from '../api/endpoints'
import type { ImportResult } from '../api/types'

export default function ImportPage() {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [fileName, setFileName] = useState('')
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)

  function handleFileChange(file: File | null) {
    setResult(null)
    setError(null)
    setFileName(file ? file.name : '')
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const file = fileInputRef.current?.files?.[0]
    if (!file) {
      setError('Выберите zip-архив')
      return
    }
    setUploading(true)
    setError(null)
    setResult(null)
    try {
      setResult(await importApi.importZip(file))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось импортировать архив')
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-2 text-xl font-semibold text-slate-900">Импорт из Obsidian</h1>
      <p className="mb-6 text-sm text-slate-500">
        Загрузите zip-архив с <code className="rounded bg-slate-100 px-1">.md</code> файлами.
        Структура папок сохранится, дубликаты по заголовку будут пропущены.
      </p>

      <form onSubmit={handleSubmit} className="space-y-4">
        <label className="block">
          <span className="block text-sm font-medium text-slate-700">Архив (.zip)</span>
          <input
            ref={fileInputRef}
            type="file"
            accept=".zip,application/zip"
            onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
            className="mt-1 block w-full text-sm text-slate-600 file:mr-4 file:rounded-md file:border-0 file:bg-[var(--accent)] file:px-4 file:py-2 file:text-sm file:font-medium file:text-white hover:file:bg-[var(--accent-hover)]"
          />
        </label>

        {error && (
          <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
        )}

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={uploading || !fileName}
            className="rounded-md bg-[var(--accent)] px-4 py-1.5 text-sm font-medium text-white hover:bg-[var(--accent-hover)] disabled:opacity-60"
          >
            {uploading ? 'Импорт…' : 'Импортировать'}
          </button>
        </div>
      </form>

      {result && (
        <div className="mt-6 space-y-4">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
            Результат импорта
          </h2>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div className="rounded-lg border border-slate-200 bg-white p-4 text-center">
              <div className="text-2xl font-semibold text-slate-900">{result.total}</div>
              <div className="text-xs text-slate-500">Всего</div>
            </div>
            <div className="rounded-lg border border-slate-200 bg-white p-4 text-center">
              <div className="text-2xl font-semibold text-emerald-600">{result.imported}</div>
              <div className="text-xs text-slate-500">Импортировано</div>
            </div>
            <div className="rounded-lg border border-slate-200 bg-white p-4 text-center">
              <div className="text-2xl font-semibold text-amber-600">{result.skipped}</div>
              <div className="text-xs text-slate-500">Пропущено</div>
            </div>
            <div className="rounded-lg border border-slate-200 bg-white p-4 text-center">
              <div className="text-2xl font-semibold text-slate-500">{result.ignored}</div>
              <div className="text-xs text-slate-500">Игнорировано</div>
            </div>
          </div>

          {result.errors.length > 0 && (
            <div className="rounded-lg border border-slate-200 bg-white p-4">
              <h3 className="mb-2 text-sm font-medium text-slate-700">Ошибки</h3>
              <ul className="list-disc space-y-1 pl-5 text-sm text-red-600">
                {result.errors.map((message, index) => (
                  <li key={index}>{message}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
