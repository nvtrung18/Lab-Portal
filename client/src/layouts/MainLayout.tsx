import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';

import { useCurrentUser } from '../modules/user/hooks';
import { clearAuthTokens, getStoredUser, type StoredUser } from '../shared/api';
import { Button } from '../shared/components';
import { ADMIN, LAB_MANAGER, STUDENT } from '../shared/constants/roles';
import { getManagedLabId, hasActiveMembership } from '../shared/utils/membership';

interface NavItem {
  label: string;
  path: string;
}

const studentBaseNavItems: NavItem[] = [
  { label: 'Hồ sơ cá nhân', path: '/app/profile' },
  { label: 'Danh sách phòng thí nghiệm', path: '/app/labs' },
];

const studentActiveMembershipNavItems: NavItem[] = [
  { label: 'Lịch sử sử dụng PTN', path: '/app/my-bookings' },
  { label: 'PTN của tôi', path: '/app/other' },
  { label: 'Nghiên cứu khoa học', path: '/app/research' },
  { label: 'Vi phạm & khiếu nại', path: '/app/penalties' },
];

const managerNavItems: NavItem[] = [
  { label: 'Hồ sơ cá nhân', path: '/app/profile' },
  { label: 'Tổng quan PTN', path: '/app/lab-overview' },
  { label: 'Hồ sơ ứng tuyển', path: '/app/applications' },
  { label: 'Thành viên PTN', path: '/app/lab-members' },
  { label: 'Khung giờ sử dụng', path: '/app/lab-slots' },
  { label: 'Lịch sử dụng PTN', path: '/app/lab-bookings' },
  { label: 'Xác nhận có mặt', path: '/app/checkin-scan' },
  { label: 'Vệ sinh PTN', path: '/app/cleaning' },
  { label: 'Khiếu nại vi phạm', path: '/app/complaints' },
  { label: 'Nghiên cứu khoa học', path: '/app/research' },
];

const managerLabScopedPaths = [
  '/app/lab-overview',
  '/app/applications',
  '/app/lab-members',
  '/app/lab-slots',
  '/app/lab-bookings',
  '/app/checkin-scan',
  '/app/cleaning',
  '/app/complaints',
  '/app/research',
];

function normalizeRole(role: string) {
  return role.replace(/^ROLE_/, '');
}

function buildUser(currentUser: ReturnType<typeof useCurrentUser>['data'], storedUser: StoredUser | null) {
  if (!currentUser) {
    return storedUser;
  }

  return {
    id: currentUser.id,
    fullName: currentUser.fullName,
    email: currentUser.email,
    roles: currentUser.roles.map(normalizeRole),
    managedLab: currentUser.managedLab?.id
      ? {
          id: currentUser.managedLab.id,
          name: currentUser.managedLab.name ?? currentUser.managedLab.labName ?? 'PTN',
        }
      : null,
    managedLabId: currentUser.managedLab?.id ?? currentUser.managedLabId ?? null,
    memberships: currentUser.memberships?.map((membership) => ({
      labId: membership.labId ?? membership.lab?.id ?? membership.id ?? 0,
      labName: membership.labName ?? membership.lab?.name ?? membership.lab?.labName ?? 'PTN',
      role: membership.role,
      status: membership.status,
      joinedAt: membership.joinedAt ?? membership.createdAt,
    })),
    researchGroupMemberships: currentUser.researchGroupMemberships,
    groupMemberships: currentUser.groupMemberships,
    researchGroups: currentUser.researchGroups,
  };
}

function getNavItems(user: ReturnType<typeof buildUser>, isManager: boolean) {
  if (isManager) {
    return managerNavItems;
  }

  return [
    ...studentBaseNavItems,
    ...(hasActiveMembership(user) ? studentActiveMembershipNavItems : []),
  ];
}

function isLabScopedManagerPath(pathname: string) {
  return managerLabScopedPaths.some((path) => pathname === path || pathname.startsWith(`${path}/`));
}

function EmptyManagedLabState() {
  return (
    <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
      Bạn chưa được phân công quản lý phòng thí nghiệm nào.
    </section>
  );
}

export function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);
  const storedUser = getStoredUser();
  const { data: currentUser } = useCurrentUser();
  const user = useMemo(() => buildUser(currentUser, storedUser), [currentUser, storedUser]);
  const roles = user?.roles.map(normalizeRole) ?? [];
  const isAdmin = roles.includes(ADMIN);
  const isManager = roles.includes(LAB_MANAGER);
  const isStudent = roles.includes(STUDENT);
  const managedLabId = getManagedLabId(user);
  const navItems = getNavItems(user, isManager);
  const portalTitle = isManager ? 'Cổng quản lý PTN' : 'Cổng sinh viên';
  const roleLabel = isManager ? 'Quản lý PTN' : isStudent ? 'Sinh viên' : 'Chưa có vai trò';
  const shouldShowManagedLabEmptyState =
    isManager && !managedLabId && isLabScopedManagerPath(location.pathname);

  const handleLogout = () => {
    clearAuthTokens();
    queryClient.clear();
    navigate('/login', { replace: true });
  };

  if (isAdmin) {
    return <Navigate to="/admin/dashboard" replace />;
  }

  return (
    <div className="min-h-screen overflow-x-hidden bg-slate-100 text-slate-900">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 overflow-y-auto border-r border-slate-200 bg-white px-4 py-6 shadow-sm lg:block">
        <SidebarContent
          navItems={navItems}
          portalTitle={portalTitle}
          roleLabel={roleLabel}
          onNavigate={() => setIsMobileNavOpen(false)}
        />
      </aside>

      {isMobileNavOpen ? (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            aria-label="Đóng menu"
            className="absolute inset-0 bg-slate-950/40"
            type="button"
            onClick={() => setIsMobileNavOpen(false)}
          />
          <aside className="relative h-full w-72 max-w-[85vw] overflow-y-auto bg-white px-4 py-5 shadow-xl">
            <div className="mb-5 flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate text-base font-semibold text-slate-950">{portalTitle}</p>
                <p className="mt-1 text-xs font-medium text-slate-500">{roleLabel}</p>
              </div>
              <Button size="sm" variant="outline" onClick={() => setIsMobileNavOpen(false)}>
                Đóng
              </Button>
            </div>
            <SidebarContent
              navItems={navItems}
              portalTitle={portalTitle}
              roleLabel={roleLabel}
              onNavigate={() => setIsMobileNavOpen(false)}
              compact
            />
          </aside>
        </div>
      ) : null}

      <div className="min-w-0 lg:pl-64">
        <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 px-4 py-3 shadow-sm backdrop-blur lg:px-8">
          <div className="flex min-w-0 items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <Button
                aria-label="Mở menu"
                className="shrink-0 lg:hidden"
                size="sm"
                variant="outline"
                onClick={() => setIsMobileNavOpen(true)}
              >
                Menu
              </Button>
              <div className="min-w-0">
                <p className="text-xs font-medium uppercase text-slate-500">Không gian làm việc</p>
                <h1 className="truncate text-lg font-semibold text-slate-950 sm:text-xl">
                  {portalTitle}
                </h1>
              </div>
            </div>

            <div className="flex min-w-0 shrink-0 items-center justify-end gap-2 sm:gap-3">
              <div className="hidden min-w-0 items-center gap-3 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600 shadow-sm sm:flex">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-semibold text-slate-600">
                  {(user?.fullName || user?.email || 'U').charAt(0).toUpperCase()}
                </span>
                <span className="min-w-0">
                  <span className="block max-w-44 truncate font-medium text-slate-800">
                    {user?.fullName || user?.email || 'Người dùng'}
                  </span>
                  <span className="block text-xs text-slate-500">{roleLabel}</span>
                </span>
              </div>
              <Button onClick={handleLogout} size="sm" variant="outline">
                Đăng xuất
              </Button>
            </div>
          </div>
        </header>

        <main className="min-w-0 max-w-full px-4 py-6 lg:px-8">
          {shouldShowManagedLabEmptyState ? <EmptyManagedLabState /> : <Outlet />}
        </main>
      </div>
    </div>
  );
}

interface SidebarContentProps {
  navItems: NavItem[];
  portalTitle: string;
  roleLabel: string;
  compact?: boolean;
  onNavigate: () => void;
}

function SidebarContent({ navItems, portalTitle, roleLabel, compact = false, onNavigate }: SidebarContentProps) {
  return (
    <>
      {!compact ? (
        <>
          <div className="px-3 text-lg font-semibold tracking-tight text-slate-950">{portalTitle}</div>
          <div className="mt-3 rounded-md bg-slate-100 px-3 py-2 text-xs font-medium text-slate-600">
            {roleLabel}
          </div>
        </>
      ) : null}

      <nav className={compact ? 'space-y-1' : 'mt-8 space-y-1'}>
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
            onClick={onNavigate}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>
    </>
  );
}
