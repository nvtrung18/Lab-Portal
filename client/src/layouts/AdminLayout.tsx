import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';

import { clearAuthTokens, getStoredUser } from '../shared/api';
import { Button } from '../shared/components';

const adminNavItems = [
  { label: 'Tổng quan', path: '/admin/dashboard' },
  { label: 'Người dùng', path: '/admin/users' },
  { label: 'Phòng thí nghiệm', path: '/admin/labs' },
  { label: 'Cấu hình hệ thống', path: '/admin/system-config' },
];

export function AdminLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const user = getStoredUser();

  const handleLogout = () => {
    clearAuthTokens();
    queryClient.clear();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen overflow-x-hidden bg-slate-950 text-slate-100">
      <aside className="fixed inset-y-0 left-0 hidden w-64 overflow-y-auto border-r border-slate-800 bg-slate-950 px-4 py-6 lg:block">
        <div className="px-3 text-lg font-semibold tracking-tight">Cổng quản trị</div>
        <div className="mt-3 rounded-md bg-slate-900 px-3 py-2 text-xs font-medium text-slate-300">
          Quản trị viên
        </div>
        <nav className="mt-8 space-y-1">
          {adminNavItems.map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              className={({ isActive }) =>
                [
                  'block rounded-md px-3 py-2 text-sm font-medium transition',
                  isActive
                    ? 'bg-white text-slate-950'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white',
                ].join(' ')
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="min-w-0 lg:pl-64">
        <header className="sticky top-0 z-10 border-b border-slate-800 bg-slate-950/95 px-4 py-4 backdrop-blur lg:px-8">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
            <div className="min-w-0">
              <p className="text-xs font-medium uppercase text-slate-400">Quản trị viên</p>
              <h1 className="truncate text-lg font-semibold text-white sm:text-xl">Quản trị hệ thống</h1>
            </div>
            <div className="flex min-w-0 items-center justify-between gap-2 sm:justify-end sm:gap-3">
              <div className="max-w-48 truncate rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300">
                {user?.fullName || user?.email || 'Quản trị viên'}
              </div>
              <Button className="bg-white text-slate-950 hover:bg-slate-200" onClick={handleLogout} size="sm">
                Đăng xuất
              </Button>
            </div>
          </div>
          <nav className="mt-4 flex max-w-full gap-2 overscroll-x-contain overflow-x-auto pb-1 lg:hidden">
            {adminNavItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  [
                    'whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition',
                    isActive
                      ? 'bg-white text-slate-950'
                      : 'border border-slate-700 text-slate-300 hover:bg-slate-800 hover:text-white',
                  ].join(' ')
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </header>

        <main className="min-w-0 max-w-full px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
