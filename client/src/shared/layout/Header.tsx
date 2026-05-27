import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';

import { clearAuthTokens, getStoredRole } from '../api';
import { Button } from '../components';

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
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase text-slate-500">Tổng quan</p>
          <h1 className="truncate text-lg font-semibold text-slate-950 sm:text-xl">Quản lý phòng thí nghiệm</h1>
        </div>

        <div className="flex items-center justify-between gap-3 sm:justify-end">
          <div className="flex items-center gap-3 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 shadow-sm">
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-200 text-xs font-semibold text-slate-600">
              {role.charAt(0)}
            </span>
            <span className="hidden sm:inline">{role}</span>
          </div>
          <Button onClick={handleLogout} size="sm" variant="outline">
            Đăng xuất
          </Button>
        </div>
      </div>
    </header>
  );
}
