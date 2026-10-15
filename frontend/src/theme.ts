export interface ThemePalette {
  accent: string
  hover: string
  light: string
}

export const THEMES: Record<string, ThemePalette> = {
  sky: { accent: '#38bdf8', hover: '#0ea5e9', light: '#e0f2fe' },
  emerald: { accent: '#34d399', hover: '#10b981', light: '#d1fae5' },
  indigo: { accent: '#818cf8', hover: '#6366f1', light: '#e0e7ff' },
  rose: { accent: '#fb7185', hover: '#f43f5e', light: '#ffe4e6' },
  amber: { accent: '#fbbf24', hover: '#f59e0b', light: '#fef3c7' },
  slate: { accent: '#94a3b8', hover: '#64748b', light: '#f1f5f9' },
  blue: { accent: '#3b82f6', hover: '#2563eb', light: '#dbeafe' },
  red: { accent: '#ef4444', hover: '#dc2626', light: '#fee2e2' },
  yellow: { accent: '#eab308', hover: '#ca8a04', light: '#fef9c3' },
  dark: { accent: '#404040', hover: '#1a1a1a', light: '#f1f5f9' },
}

export const THEME_STORAGE_KEY = 'sn-theme'
export const DEFAULT_THEME = 'sky'

export function themeNames(): string[] {
  return Object.keys(THEMES)
}

export function getStoredTheme(): string {
  if (typeof localStorage === 'undefined') return DEFAULT_THEME
  const stored = localStorage.getItem(THEME_STORAGE_KEY)
  return stored && THEMES[stored] ? stored : DEFAULT_THEME
}

export function applyTheme(name: string): void {
  const palette = THEMES[name] ?? THEMES[DEFAULT_THEME]
  const root = document.documentElement
  root.style.setProperty('--accent', palette.accent)
  root.style.setProperty('--accent-hover', palette.hover)
  root.style.setProperty('--accent-light', palette.light)
  localStorage.setItem(THEME_STORAGE_KEY, name)
}
