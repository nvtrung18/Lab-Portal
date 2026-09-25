import { createContext, useCallback, useContext, useEffect, useMemo, useState, type PropsWithChildren } from 'react';

export type Theme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'lab-portal.theme';

function normalizeTheme(value: unknown): Theme | null {
  return value === 'dark' || value === 'light' ? value : null;
}

function getDefaultTheme(): Theme {
  return normalizeTheme(import.meta.env.VITE_DEFAULT_THEME) ?? 'light';
}

export function getStoredTheme(): Theme {
  if (typeof window === 'undefined') return getDefaultTheme();
  return normalizeTheme(window.localStorage.getItem(THEME_STORAGE_KEY)) ?? getDefaultTheme();
}

function applyTheme(theme: Theme) {
  document.documentElement.classList.toggle('dark', theme === 'dark');
  document.documentElement.style.colorScheme = theme;
}

interface ThemeContextValue {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: PropsWithChildren) {
  const [theme, setThemeState] = useState<Theme>(getStoredTheme);

  const setTheme = useCallback((nextTheme: Theme) => {
    window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme);
    setThemeState(nextTheme);
  }, []);

  const toggleTheme = useCallback(() => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
  }, [setTheme, theme]);

  useEffect(() => applyTheme(theme), [theme]);

  const value = useMemo(() => ({ theme, setTheme, toggleTheme }), [setTheme, theme, toggleTheme]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be used inside ThemeProvider');
  return context;
}
