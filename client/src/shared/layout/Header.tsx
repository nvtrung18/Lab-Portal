import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';

import { clearAuthTokens, getStoredRole } from '../api';
import { Button } from '../components';
import { useI18n } from '../i18n';

export function Header() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const role = getStoredRole() ?? 'UNKNOWN';
  const { t } = useI18n();

  const handleLogout = () => {
    clearAuthTokens();
    queryClient.clear();
    navigate('/login', { replace: true });
  };

  return (
    <header className="app-gradient-banner sticky top-0 z-10 border-b border-slate-900 dark:border-slate-800 px-4 py-4 shadow-sm backdrop-blur lg:px-8">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase text-white/75">{t('nav.labOverview')}</p>
          <h1 className="truncate text-lg font-semibold text-white sm:text-xl">{t('common.appTitle')}</h1>
        </div>

        <div className="flex items-center justify-between gap-3 sm:justify-end">
          <div className="app-control-surface flex items-center gap-3 rounded-md border px-3 py-2 text-sm shadow-sm">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-200 text-xs font-semibold text-slate-600">
              {role.charAt(0)}
            </span>
            <span className="hidden sm:inline">{role}</span>
          </div>
          <Button onClick={handleLogout} size="sm" variant="outline">
            {t('common.logout')}
          </Button>
        </div>
      </div>
    </header>
  );
}
