import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';

import { clearAuthTokens, getStoredRole } from '../api';

export function Header() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const role = getStoredRole() ?? 'UNKNOWN';

  const handleLogout = () => {
    clearAuthTokens();
    queryClient.clear();
    navigate('/login', { replace: true });
  };

  return (
    <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/95 px-4 py-4 shadow-sm backdrop-blur lg:px-8">
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="text-xs font-medium uppercase text-slate-500">Dashboard</p>
          <h1 className="text-xl font-semibold text-slate-950">Lab Management</h1>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center gap-3 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 shadow-sm">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-200 text-xs font-semibold text-slate-600">
              {role.charAt(0)}
            </span>
            <span className="hidden sm:inline">{role}</span>
          </div>
          <button
            type="button"
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:bg-slate-50"
            onClick={handleLogout}
          >
            Đăng xuất
          </button>
        </div>
      </div>
    </header>
  );
}
