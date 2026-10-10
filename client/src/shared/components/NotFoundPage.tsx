import { useNavigate } from 'react-router-dom';

import { useI18n } from '../i18n';
import { Button } from './Button';
import { getHomePath } from './errorNavigation';

export function NotFoundPage() {
  const navigate = useNavigate();
  const { t } = useI18n();

  return (
    <main className="app-shell flex min-h-dvh items-center justify-center px-4 py-10 text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <section className="app-error-page app-surface w-full max-w-lg rounded-lg text-center">
        <p className="app-error-code" aria-hidden="true">404</p>
        <h1 className="text-xl font-semibold text-slate-950 dark:text-white">{t('common.notFoundTitle')}</h1>
        <div className="mt-6">
          <Button onClick={() => navigate(getHomePath(), { replace: true })}>{t('common.goHome')}</Button>
        </div>
      </section>
    </main>
  );
}
