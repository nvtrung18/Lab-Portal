import { useQueryClient } from '@tanstack/react-query';
import {
  Activity,
  Bell,
  BookOpenCheck,
  Bot,
  CalendarDays,
  ClipboardList,
  CircleUserRound,
  FlaskConical,
  LayoutDashboard,
  Menu,
  MessageSquareWarning,
  Microscope,
  ScanFace,
  Scale,
  Sparkles,
  UserRoundCheck,
  UsersRound,
  type LucideIcon,
} from 'lucide-react';
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
  group?: string;
  icon?: LucideIcon;
}

const studentCoreNavItems: NavItem[] = [
  { label: 'Tổng quan', path: '/app/dashboard', group: 'Bắt đầu', icon: LayoutDashboard },
  { label: 'Khám phá PTN', path: '/app/labs', group: 'Bắt đầu', icon: FlaskConical },
];

const studentActiveMembershipNavItems: NavItem[] = [
  { label: 'PTN của tôi', path: '/app/other', group: 'Hoạt động PTN', icon: UsersRound },
  { label: 'Ca sử dụng', path: '/app/my-bookings', group: 'Hoạt động PTN', icon: CalendarDays },
  { label: 'Nghiên cứu khoa học', path: '/app/research', group: 'Hoạt động PTN', icon: Microscope },
  { label: 'Vi phạm & khiếu nại', path: '/app/penalties', group: 'Hoạt động PTN', icon: Scale },
];

const studentSupportNavItems: NavItem[] = [
  { label: 'Trợ lý AI', path: '/app/assistant', group: 'Hỗ trợ và tài khoản', icon: Bot },
  { label: 'Thông báo', path: '/app/notifications', group: 'Hỗ trợ và tài khoản', icon: Bell },
  { label: 'Hồ sơ khuôn mặt', path: '/app/face-profile', group: 'Hỗ trợ và tài khoản', icon: ScanFace },
  { label: 'Hồ sơ cá nhân', path: '/app/profile', group: 'Hỗ trợ và tài khoản', icon: CircleUserRound },
];

const managerNavItems: NavItem[] = [
  { label: 'Tổng quan PTN', path: '/app/lab-overview', group: 'Điều hành hôm nay', icon: LayoutDashboard },
  { label: 'Trạm check-in', path: '/app/checkin-scan', group: 'Điều hành hôm nay', icon: ScanFace },
  { label: 'Khung giờ sử dụng', path: '/app/lab-slots', group: 'Điều hành hôm nay', icon: CalendarDays },
  { label: 'Hồ sơ ứng tuyển', path: '/app/applications', group: 'Thành viên', icon: ClipboardList },
  { label: 'Thành viên PTN', path: '/app/lab-members', group: 'Thành viên', icon: UserRoundCheck },
  { label: 'Vệ sinh PTN', path: '/app/cleaning', group: 'Vận hành PTN', icon: Sparkles },
  { label: 'Khiếu nại vi phạm', path: '/app/complaints', group: 'Vận hành PTN', icon: MessageSquareWarning },
  { label: 'Nghiên cứu khoa học', path: '/app/research', group: 'Vận hành PTN', icon: Microscope },
  { label: 'Nhật ký vận hành', path: '/app/operational-logs', group: 'Hỗ trợ và tài khoản', icon: Activity },
  { label: 'Kho tri thức AI', path: '/app/knowledge', group: 'Hỗ trợ và tài khoản', icon: BookOpenCheck },
  { label: 'Trợ lý AI', path: '/app/assistant', group: 'Hỗ trợ và tài khoản', icon: Bot },
  { label: 'Thông báo', path: '/app/notifications', group: 'Hỗ trợ và tài khoản', icon: Bell },
  { label: 'Hồ sơ khuôn mặt', path: '/app/face-profile', group: 'Hỗ trợ và tài khoản', icon: ScanFace },
  { label: 'Hồ sơ cá nhân', path: '/app/profile', group: 'Hỗ trợ và tài khoản', icon: CircleUserRound },
];

const managerLabScopedPaths = [
  '/app/lab-overview',
  '/app/applications',
  '/app/lab-members',
  '/app/lab-slots',
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
    ...studentCoreNavItems,
    ...(hasActiveMembership(user) ? studentActiveMembershipNavItems : []),
    ...studentSupportNavItems,
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
  const currentPageLabel = navItems.find(
    (item) => location.pathname === item.path || location.pathname.startsWith(`${item.path}/`),
  )?.label;
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
                <Menu aria-hidden="true" className="h-4 w-4" />
                <span className="sr-only">Mở menu</span>
              </Button>
              <div className="min-w-0">
                <p className="text-xs font-medium text-slate-500">{portalTitle}</p>
                <h1 className="truncate text-lg font-semibold text-slate-950 sm:text-xl">
                  {currentPageLabel ?? 'Không gian làm việc'}
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
          <div className="flex items-center gap-3 px-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-slate-950 text-white">
              <FlaskConical aria-hidden="true" className="h-5 w-5" />
            </span>
            <div>
              <div className="text-base font-semibold tracking-tight text-slate-950">Lab Portal</div>
              <div className="text-xs text-slate-500">{portalTitle}</div>
            </div>
          </div>
          <div className="mt-3 rounded-md bg-slate-100 px-3 py-2 text-xs font-medium text-slate-600">
            {roleLabel}
          </div>
        </>
      ) : null}

      <nav className={compact ? 'space-y-1' : 'mt-8 space-y-1'}>
        {navItems.map((item, index) => {
          const Icon = item.icon;
          const showGroup = Boolean(item.group && item.group !== navItems[index - 1]?.group);
          return (
            <div className={showGroup && index > 0 ? 'pt-5' : ''} key={item.path}>
              {showGroup ? <p className="mb-2 px-3 text-xs font-semibold uppercase tracking-wider text-slate-400">{item.group}</p> : null}
              <NavLink
                to={item.path}
                className={({ isActive }) =>
                  [
                    'flex min-h-11 items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500',
                    isActive
                      ? 'bg-slate-900 text-white shadow-sm'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950',
                  ].join(' ')
                }
                onClick={onNavigate}
              >
                {Icon ? <Icon aria-hidden="true" className="h-4 w-4 shrink-0" /> : null}
                <span>{item.label}</span>
              </NavLink>
            </div>
          );
        })}
      </nav>
    </>
  );
}
