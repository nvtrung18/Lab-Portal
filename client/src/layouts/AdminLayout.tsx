import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';

import { clearAuthTokens, getStoredUser } from '../shared/api';

const adminNavItems = [
  { label: 'Dashboard', path: '/admin/dashboard' },
  { label: 'Users', path: '/admin/users' },
  { label: 'Labs', path: '/admin/labs' },
  { label: 'System Config', path: '/admin/system-config' },
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
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-slate-800 bg-slate-950 px-4 py-6 lg:block">
        <div className="px-3 text-lg font-semibold tracking-tight">Admin Portal</div>
        <div className="mt-3 rounded-md bg-slate-900 px-3 py-2 text-xs font-medium text-slate-300">
          ADMIN
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

      <div className="lg:pl-64">
        <header className="sticky top-0 z-10 border-b border-slate-800 bg-slate-950/95 px-4 py-4 backdrop-blur lg:px-8">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-xs font-medium uppercase text-slate-400">Admin</p>
              <h1 className="text-xl font-semibold text-white">System Management</h1>
            </div>
            <div className="flex items-center gap-3">
              <div className="rounded-md border border-slate-700 px-3 py-2 text-sm text-slate-300">
                {user?.fullName || user?.email || 'Admin'}
              </div>
              <button
                type="button"
                className="rounded-md bg-white px-3 py-2 text-sm font-semibold text-slate-950 transition hover:bg-slate-200"
                onClick={handleLogout}
              >
                Đăng xuất
              </button>
            </div>
          </div>
        </header>

        <main className="px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
