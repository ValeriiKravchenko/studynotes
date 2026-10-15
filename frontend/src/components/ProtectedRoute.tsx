import { useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { authApi } from '../api/endpoints'

type AuthStatus = 'loading' | 'authed' | 'unauthed'

export default function ProtectedRoute() {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const location = useLocation()

  useEffect(() => {
    let active = true
    authApi
      .me()
      .then(() => {
        if (active) setStatus('authed')
      })
      .catch(() => {
        if (active) setStatus('unauthed')
      })
    return () => {
      active = false
    }
  }, [])

  if (status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 text-slate-600">
        Загрузка…
      </div>
    )
  }

  if (status === 'unauthed') {
    return <Navigate to="/login" state={{ from: location }} replace />
  }

  return <Outlet />
}
