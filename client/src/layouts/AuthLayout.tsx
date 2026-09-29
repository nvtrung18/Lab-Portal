import { Outlet } from 'react-router-dom';

import { LanguageSwitcher, ThemeToggle } from '../shared/components';

export function AuthLayout() {
  return (
    <main className="app-shell relative flex min-h-screen items-center justify-center overflow-x-hidden px-4 pb-8 pt-20 text-slate-900 dark:bg-slate-950 dark:text-slate-100 sm:px-6 sm:py-10">
      <div className="absolute right-4 top-4 flex items-center gap-2 sm:right-6 sm:top-6">
        <LanguageSwitcher />
        <ThemeToggle />
      </div>
      <div className="min-w-0 w-[calc(100vw-2rem)] max-w-full sm:w-full sm:max-w-[420px]">
        <Outlet />
      </div>
    </main>
  );
}
