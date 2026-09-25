import { useNavigate } from 'react-router-dom';

import { useI18n } from '../i18n';
import { Button } from './Button';
import { getHomePath } from './errorNavigation';

export function NotFoundPage() {
  const navigate = useNavigate();
  const { t } = useI18n();

  return (
    <main className="app-shell flex min-h-screen items-center justify-center px-4 py-10 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <section className="w-full max-w-lg rounded-lg border border-slate-200 bg-white p-6 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('common.notFoundTitle')}</h1>
        <div className="mt-6">
          <Button onClick={() => navigate(getHomePath(), { replace: true })}>{t('common.goHome')}</Button>
        </div>
      </section>
    </main>
  );
}
