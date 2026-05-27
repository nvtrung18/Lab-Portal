import { NavLink, Outlet } from 'react-router-dom';

import { getStoredRole } from '../api';
import { Header } from './Header';

type Role = 'USER' | 'MANAGER';

const navigationByRole: Record<Role, Array<{ label: string; path: string }>> = {
  USER: [
    { label: 'Tổng quan', path: '/' },
    { label: 'Phòng thí nghiệm', path: '/labs' },
    { label: 'Đơn ứng tuyển của tôi', path: '/my-applications' },
  ],
  MANAGER: [
    { label: 'Tổng quan', path: '/' },
    { label: 'PTN quản lý', path: '/manager/labs' },
    { label: 'Đơn ứng tuyển', path: '/manager/applications' },
  ],
};

export function MainLayout() {
  const currentRole = (getStoredRole() as Role | null) ?? 'USER';
  const navigationItems = navigationByRole[currentRole];

  return (
    <div className="min-h-screen overflow-x-hidden bg-slate-100 text-slate-900">
      <aside className="fixed inset-y-0 left-0 hidden w-64 overflow-y-auto border-r border-slate-200 bg-white px-4 py-6 shadow-sm lg:block">
        <div className="px-3 text-lg font-semibold tracking-tight text-slate-950">
          Cổng phòng thí nghiệm
        </div>
        <div className="mt-3 rounded-md bg-slate-100 px-3 py-2 text-xs font-medium text-slate-600">
          Vai trò thử nghiệm: {currentRole}
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

      <div className="min-w-0 lg:pl-64">
        <Header />

        <main className="min-w-0 max-w-full px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
