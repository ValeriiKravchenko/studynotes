import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { authApi } from '../api/endpoints'
import ThemeSwitcher from './ThemeSwitcher'

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const [logoutError, setLogoutError] = useState<string | null>(null)

  const notesActive =
    location.pathname === '/notes' ||
    (location.pathname.startsWith('/notes/') && location.pathname !== '/notes/new')

  async function handleLogout() {
    setLogoutError(null)
    try {
      await authApi.logout()
      navigate('/login', { replace: true })
    } catch (err) {
      setLogoutError(err instanceof Error ? err.message : 'Не удалось выйти')
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <div className="flex items-center gap-6">
            <Link to="/notes" className="text-lg font-semibold text-slate-900">
              StudyNotes
            </Link>
            <nav className="flex items-center gap-4">
              <Link
                to="/notes"
                className={
                  notesActive
                    ? 'text-sm font-medium text-[var(--accent)]'
                    : 'text-sm text-slate-600 hover:text-slate-900'
                }
              >
                Заметки
              </Link>
              <NavLink
                to="/search"
                className={({ isActive }) =>
                  isActive
                    ? 'text-sm font-medium text-[var(--accent)]'
                    : 'text-sm text-slate-600 hover:text-slate-900'
                }
              >
                Поиск
              </NavLink>
              <NavLink
                to="/notes/new"
                className={({ isActive }) =>
                  isActive
                    ? 'text-sm font-medium text-[var(--accent)]'
                    : 'text-sm text-slate-600 hover:text-slate-900'
                }
              >
                Создать
              </NavLink>
              <NavLink
                to="/import"
                className={({ isActive }) =>
                  isActive
                    ? 'text-sm font-medium text-[var(--accent)]'
                    : 'text-sm text-slate-600 hover:text-slate-900'
                }
              >
                Импорт
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-3">
            <ThemeSwitcher />
            <button
              onClick={handleLogout}
              className="rounded-md px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-900"
            >
              Выйти
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        {logoutError && (
          <p className="mb-4 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{logoutError}</p>
        )}
        <Outlet />
      </main>
    </div>
  )
}
