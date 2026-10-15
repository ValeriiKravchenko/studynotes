import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import ImportPage from './pages/ImportPage'
import NotesListPage from './pages/NotesListPage'
import NoteFormPage from './pages/NoteFormPage'
import NoteViewPage from './pages/NoteViewPage'
import SearchPage from './pages/SearchPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/notes" element={<NotesListPage />} />
            <Route path="/notes/new" element={<NoteFormPage />} />
            <Route path="/notes/:id" element={<NoteViewPage />} />
            <Route path="/notes/:id/edit" element={<NoteFormPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/import" element={<ImportPage />} />
          </Route>
        </Route>
        <Route path="/" element={<Navigate to="/notes" replace />} />
        <Route path="*" element={<Navigate to="/notes" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
