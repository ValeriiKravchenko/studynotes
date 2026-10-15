import { useState } from 'react'
import type { ChangeEvent } from 'react'
import { applyTheme, getStoredTheme, themeNames } from '../theme'

export default function ThemeSwitcher() {
  const [theme, setTheme] = useState<string>(getStoredTheme)

  function handleChange(event: ChangeEvent<HTMLSelectElement>) {
    const next = event.target.value
    applyTheme(next)
    setTheme(next)
  }

  return (
    <select
      value={theme}
      onChange={handleChange}
      aria-label="Тема оформления"
      className="rounded-md border border-slate-300 bg-white px-2 py-1 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-[var(--accent-light)]"
    >
      {themeNames().map((name) => (
        <option key={name} value={name}>
          {name}
        </option>
      ))}
    </select>
  )
}
