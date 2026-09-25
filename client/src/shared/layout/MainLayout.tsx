import { NavLink, Outlet } from 'react-router-dom';

import { getStoredRole } from '../api';
import { useI18n } from '../i18n';
import { Header } from './Header';

type Role = 'USER' | 'MANAGER';

const navigationByRole: Record<Role, Array<{ labelKey: string; path: string }>> = {
  USER: [
    { labelKey: 'nav.studentDashboard', path: '/' },
    { labelKey: 'nav.labs', path: '/labs' },
    { labelKey: 'nav.myApplications', path: '/my-applications' },
  ],
  MANAGER: [
    { labelKey: 'nav.labOverview', path: '/' },
    { labelKey: 'nav.labMembers', path: '/manager/labs' },
    { labelKey: 'nav.applications', path: '/manager/applications' },
  ],
};

export function MainLayout() {
  const currentRole = (getStoredRole() as Role | null) ?? 'USER';
  const navigationItems = navigationByRole[currentRole];
  const { t } = useI18n();

  return (
    <div className="app-shell min-h-screen overflow-x-hidden text-slate-900">
      <aside className="app-surface fixed inset-y-0 left-0 hidden w-64 overflow-y-auto border-r border-slate-200 px-4 py-6 shadow-sm lg:block">
        <div className="px-3 text-lg font-semibold tracking-tight text-slate-950">
          {t('common.appTitle')}
        </div>
        <div className="mt-3 rounded-md bg-slate-100 px-3 py-2 text-xs font-medium text-slate-600">
          {currentRole}
        </div>

        <nav className="mt-8 space-y-1">
          {navigationItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                [
                  'block rounded-md px-3 py-2 text-sm font-medium transition',
                  isActive
                    ? 'bg-slate-900 text-white'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950',
                ].join(' ')
              }
            >
              {t(item.labelKey)}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="min-w-0 lg:pl-64">
        <Header />

        <main className="min-w-0 max-w-full px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
