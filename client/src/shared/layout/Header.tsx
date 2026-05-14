import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';

import { useProfile } from '../../modules/user/hooks';
import { clearAuthTokens } from '../api';

function getInitial(name: string) {
  return name.trim().charAt(0).toUpperCase() || 'U';
}

export function Header() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: profile, isLoading, isSuccess } = useProfile();
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  const displayName = profile?.fullName || profile?.username || profile?.email || 'User';

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

        {isLoading ? (
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 animate-pulse rounded-full bg-slate-200" />
            <div className="h-4 w-24 animate-pulse rounded bg-slate-200" />
          </div>
        ) : null}

        {isSuccess ? (
          <div className="relative">
            <button
              type="button"
              className="flex items-center gap-3 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 shadow-sm transition hover:bg-slate-50"
              onClick={() => setIsMenuOpen((current) => !current)}
            >
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-900 text-xs font-semibold text-white">
                {getInitial(displayName)}
              </span>
              <span className="hidden max-w-40 truncate font-medium sm:inline">
                {displayName}
              </span>
            </button>

            {isMenuOpen ? (
              <div className="absolute right-0 mt-2 w-48 rounded-md border border-slate-200 bg-white py-1 shadow-lg">
                <Link
                  className="block px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
                  to="/profile"
                  onClick={() => setIsMenuOpen(false)}
                >
                  Trang cá nhân
                </Link>
                <button
                  type="button"
                  className="block w-full px-4 py-2 text-left text-sm text-red-600 hover:bg-red-50"
                  onClick={handleLogout}
                >
                  Đăng xuất
                </button>
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </header>
  );
}
