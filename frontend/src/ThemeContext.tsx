import { createContext, useContext, useState, type ReactNode } from 'react'

export type Theme = 'light' | 'dark'
const THEME_KEY = 'swipeauctions_theme'

function getInitialTheme(): Theme {
  try {
    const stored = localStorage.getItem(THEME_KEY)
    if (stored === 'light' || stored === 'dark') return stored
  } catch { /* localStorage unavailable (private mode, etc.) — fall back to default */ }
  return 'light'
}

interface ThemeState {
  theme: Theme
  toggleTheme: () => void
}

const ThemeContext = createContext<ThemeState | undefined>(undefined)

export function ThemeProvider({ children }: { children: ReactNode }) {
  // index.html already set the `data-theme` attribute before React mounted (avoids a flash of
  // the wrong theme) — this just keeps React's own state in sync with that starting point.
  const [theme, setTheme] = useState<Theme>(getInitialTheme)

  const apply = (next: Theme) => {
    setTheme(next)
    document.documentElement.setAttribute('data-theme', next)
    try { localStorage.setItem(THEME_KEY, next) } catch { /* ignore */ }
  }

  const toggleTheme = () => apply(theme === 'light' ? 'dark' : 'light')

  return <ThemeContext.Provider value={{ theme, toggleTheme }}>{children}</ThemeContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useTheme(): ThemeState {
  const ctx = useContext(ThemeContext)
  if (!ctx) throw new Error('useTheme must be used within ThemeProvider')
  return ctx
}
