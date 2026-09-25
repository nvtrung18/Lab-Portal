import { Moon, Sun } from 'lucide-react';

import { useI18n } from '../i18n';
import { useTheme } from '../theme';

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const { t } = useI18n();
  const isDark = theme === 'dark';

  return (
    <button
      aria-label={isDark ? t('common.switchToLight') : t('common.switchToDark')}
      className="app-theme-toggle app-control-surface inline-flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border text-sm font-semibold shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
      title={isDark ? t('common.switchToLight') : t('common.switchToDark')}
      type="button"
      onClick={toggleTheme}
    >
      {isDark ? <Sun aria-hidden="true" className="h-4 w-4" /> : <Moon aria-hidden="true" className="h-4 w-4" />}
    </button>
  );
}
