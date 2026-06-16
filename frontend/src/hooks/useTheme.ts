import { useEffect, useState } from 'react'

export type Theme = 'light' | 'dark'

function systemPrefersDark(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
  )
}

/**
 * Theme state: defaults to the OS preference, toggled per session.
 * Intentionally not persisted to localStorage (see project UI spec).
 */
export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() => (systemPrefersDark() ? 'dark' : 'light'))

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
  }, [theme])

  const toggle = () => setTheme((current) => (current === 'dark' ? 'light' : 'dark'))

  return { theme, toggle }
}
