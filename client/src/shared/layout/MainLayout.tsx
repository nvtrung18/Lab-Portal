import { NavLink, Outlet } from 'react-router-dom';

import { getStoredRole } from '../api';
import { Header } from './Header';

type Role = 'USER' | 'MANAGER';

const navigationByRole: Record<Role, Array<{ label: string; path: string }>> = {
  USER: [
    { label: 'Dashboard', path: '/' },
    { label: 'Labs', path: '/labs' },
    { label: 'My Applications', path: '/my-applications' },
  ],
  MANAGER: [
    { label: 'Dashboard', path: '/' },
    { label: 'Managed Labs', path: '/manager/labs' },
    { label: 'Applications', path: '/manager/applications' },
  ],
};

export function MainLayout() {
  const currentRole = (getStoredRole() as Role | null) ?? 'USER';
  const navigationItems = navigationByRole[currentRole];

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-slate-200 bg-white px-4 py-6 shadow-sm lg:block">
        <div className="px-3 text-lg font-semibold tracking-tight text-slate-950">
          Lab Portal
        </div>
        <div className="mt-3 rounded-md bg-slate-100 px-3 py-2 text-xs font-medium text-slate-600">
          Mock role: {currentRole}
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
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="lg:pl-64">
        <Header />

        <main className="px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
