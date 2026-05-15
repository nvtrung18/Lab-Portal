import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';

import { clearAuthTokens, getStoredUser } from '../shared/api';
import { LAB_MANAGER, STUDENT } from '../shared/constants/roles';
import { hasActiveMembership } from '../shared/utils/membership';
import { useCurrentUser } from '../modules/user/hooks';

const studentNavItems = [
  { label: 'Profile', path: '/app/profile' },
  { label: 'Labs', path: '/app/labs' },
];

const studentMembershipNavItems = [
  { label: 'My Bookings', path: '/app/my-bookings' },
  { label: 'Research', path: '/app/research' },
  { label: 'Other', path: '/app/other' },
];

const managerNavItems = [
  { label: 'Profile', path: '/app/profile' },
  { label: 'Lab Overview', path: '/app/lab-overview' },
  { label: 'Applications', path: '/app/applications' },
  { label: 'Lab Members', path: '/app/lab-members' },
  { label: 'Lab Slots', path: '/app/lab-slots' },
  { label: 'Lab Bookings', path: '/app/lab-bookings' },
  { label: 'Cleaning', path: '/app/cleaning' },
  { label: 'Research', path: '/app/research' },
];

export function MainLayout() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const storedUser = getStoredUser();
  const { data: currentUser } = useCurrentUser();
  const user = currentUser
    ? {
        id: currentUser.id,
        fullName: currentUser.fullName,
        email: currentUser.email,
        roles: currentUser.roles.map((role) => role.replace(/^ROLE_/, '')),
        managedLab: currentUser.managedLab?.id
          ? {
              id: currentUser.managedLab.id,
              name: currentUser.managedLab.name ?? currentUser.managedLab.labName ?? 'Lab',
            }
          : null,
        managedLabId: currentUser.managedLab?.id ?? currentUser.managedLabId ?? null,
        memberships: currentUser.memberships?.map((membership) => ({
          labId: membership.labId ?? membership.lab?.id ?? membership.id ?? 0,
          labName: membership.labName ?? membership.lab?.name ?? membership.lab?.labName ?? 'Lab',
          role: membership.role,
          status: membership.status,
          joinedAt: membership.joinedAt ?? membership.createdAt,
        })),
      }
    : storedUser;
  const isManager = Boolean(user?.roles.includes(LAB_MANAGER));
  const isStudent = Boolean(user?.roles.includes(STUDENT));
  const portalTitle = isManager ? 'Manager Portal' : 'Student Portal';
  const roleLabel = isManager ? LAB_MANAGER : isStudent ? STUDENT : 'No role';
  const navItems = isManager
    ? managerNavItems
    : [
        ...studentNavItems,
        ...(hasActiveMembership(user) ? studentMembershipNavItems : []),
      ];

  const handleLogout = () => {
    clearAuthTokens();
    queryClient.clear();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-slate-100 text-slate-900">
      <aside className="fixed inset-y-0 left-0 hidden w-64 border-r border-slate-200 bg-white px-4 py-6 shadow-sm lg:block">
        <div className="px-3 text-lg font-semibold tracking-tight text-slate-950">
          {portalTitle}
        </div>
        <div className="mt-3 rounded-md bg-slate-100 px-3 py-2 text-xs font-medium text-slate-600">
          {roleLabel}
        </div>

        <nav className="mt-8 space-y-1">
          {navItems.map((item) => (
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
        <header className="sticky top-0 z-10 border-b border-slate-200 bg-white/95 px-4 py-4 shadow-sm backdrop-blur lg:px-8">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-xs font-medium uppercase text-slate-500">Workspace</p>
              <h1 className="text-xl font-semibold text-slate-950">Lab Management</h1>
            </div>
            <div className="flex items-center gap-3">
              <div className="flex items-center gap-3 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 shadow-sm">
                <span className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-200 text-xs font-semibold text-slate-600">
                  {(user?.fullName || user?.email || 'U').charAt(0).toUpperCase()}
                </span>
                <span className="hidden sm:block">
                  <span className="block font-medium text-slate-800">{user?.fullName || user?.email || 'User'}</span>
                  <span className="block text-xs text-slate-500">{roleLabel}</span>
                </span>
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
          <nav className="mt-4 flex gap-2 overflow-x-auto pb-1 lg:hidden">
            {navItems.map((item) => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  [
                    'whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition',
                    isActive
                      ? 'bg-slate-900 text-white'
                      : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-100 hover:text-slate-950',
                  ].join(' ')
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </header>

        <main className="px-4 py-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
