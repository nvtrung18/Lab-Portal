import { useQueryClient } from '@tanstack/react-query';
import {
  Activity,
  ArrowUpRight,
  Bell,
  BookOpenCheck,
  Bot,
  CalendarDays,
  ClipboardList,
  CircleUserRound,
  FlaskConical,
  Globe,
  LayoutDashboard,
  LogOut,
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
import { useEffect, useMemo, useState } from 'react';
import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom';

import { useCurrentUser } from '../modules/user/hooks';
import { useNotifications, useRealtimeEvents } from '../modules/notification/hooks';
import { clearAuthTokens, getStoredUser, type StoredUser } from '../shared/api';
import { Button, LanguageSwitcher, Modal, PortalBrand, ThemeToggle } from '../shared/components';
import { ADMIN, LAB_MANAGER, STUDENT } from '../shared/constants/roles';
import { useI18n } from '../shared/i18n';
import { getManagedLabId, hasActiveMembership } from '../shared/utils/membership';

interface NavItem {
  labelKey: string;
  path: string;
  groupKey?: string;
  icon?: LucideIcon;
}

const studentCoreNavItems: NavItem[] = [
  { labelKey: 'nav.studentDashboard', path: '/app/dashboard', groupKey: 'nav.start', icon: LayoutDashboard },
  { labelKey: 'nav.labs', path: '/app/labs', groupKey: 'nav.start', icon: FlaskConical },
];

const studentActiveMembershipNavItems: NavItem[] = [
  { labelKey: 'nav.myLab', path: '/app/other', groupKey: 'nav.labActivity', icon: UsersRound },
  { labelKey: 'nav.myBookings', path: '/app/my-bookings', groupKey: 'nav.labActivity', icon: CalendarDays },
  { labelKey: 'nav.research', path: '/app/research', groupKey: 'nav.labActivity', icon: Microscope },
  { labelKey: 'nav.penalties', path: '/app/penalties', groupKey: 'nav.labActivity', icon: Scale },
];

const studentSupportNavItems: NavItem[] = [
  { labelKey: 'nav.aiAssistant', path: '/app/assistant', groupKey: 'nav.supportAccount', icon: Bot },
  { labelKey: 'nav.profile', path: '/app/profile', groupKey: 'nav.supportAccount', icon: CircleUserRound },
];

const managerNavItems: NavItem[] = [
  { labelKey: 'nav.labOverview', path: '/app/lab-overview', groupKey: 'nav.managerToday', icon: LayoutDashboard },
  { labelKey: 'nav.checkinStation', path: '/app/checkin-scan', groupKey: 'nav.managerToday', icon: ScanFace },
  { labelKey: 'nav.labSlots', path: '/app/lab-slots', groupKey: 'nav.managerToday', icon: CalendarDays },
  { labelKey: 'nav.applications', path: '/app/applications', groupKey: 'nav.members', icon: ClipboardList },
  { labelKey: 'nav.labMembers', path: '/app/lab-members', groupKey: 'nav.members', icon: UserRoundCheck },
  { labelKey: 'nav.cleaning', path: '/app/cleaning', groupKey: 'nav.labOperations', icon: Sparkles },
  { labelKey: 'nav.complaints', path: '/app/complaints', groupKey: 'nav.labOperations', icon: MessageSquareWarning },
  { labelKey: 'nav.research', path: '/app/research', groupKey: 'nav.labOperations', icon: Microscope },
  { labelKey: 'nav.adminOperations', path: '/app/operational-logs', groupKey: 'nav.supportAccount', icon: Activity },
  { labelKey: 'nav.knowledge', path: '/app/knowledge', groupKey: 'nav.supportAccount', icon: BookOpenCheck },
  { labelKey: 'nav.aiAssistant', path: '/app/assistant', groupKey: 'nav.supportAccount', icon: Bot },
  { labelKey: 'nav.profile', path: '/app/profile', groupKey: 'nav.supportAccount', icon: CircleUserRound },
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
          name: currentUser.managedLab.name ?? currentUser.managedLab.labName ?? '—',
        }
      : null,
    managedLabId: currentUser.managedLab?.id ?? currentUser.managedLabId ?? null,
    memberships: currentUser.memberships?.map((membership) => ({
      labId: membership.labId ?? membership.lab?.id ?? membership.id ?? 0,
      labName: membership.labName ?? membership.lab?.name ?? membership.lab?.labName ?? '—',
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
  const { t } = useI18n();

  return (
    <section className="rounded-lg border border-amber-200 bg-white p-6 text-sm text-amber-700 shadow-sm">
      {t('layout.unassignedLab')}
    </section>
  );
}

export function MainLayout() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [isNavOpen, setIsNavOpen] = useState(false);
  const storedUser = getStoredUser();
  const { data: currentUser } = useCurrentUser();
  const user = useMemo(() => buildUser(currentUser, storedUser), [currentUser, storedUser]);
  useRealtimeEvents(Boolean(user));
  const { data: notificationData } = useNotifications(0, 1);
  const roles = user?.roles.map(normalizeRole) ?? [];
  const isAdmin = roles.includes(ADMIN);
  const isManager = roles.includes(LAB_MANAGER);
  const isStudent = roles.includes(STUDENT);
  const managedLabId = getManagedLabId(user);
  const navItems = getNavItems(user, isManager);
  const activeNavItem = navItems.find(
    (item) => location.pathname === item.path || location.pathname.startsWith(`${item.path}/`),
  );
  const currentPageLabel = location.pathname === '/app/notifications'
    ? t('common.notifications')
    : activeNavItem ? t(activeNavItem.labelKey) : undefined;
  const portalTitle = isManager ? t('layout.managerPortal') : t('layout.studentPortal');
  const roleLabel = isManager ? t('layout.managerRole') : isStudent ? t('layout.studentRole') : t('layout.noRole');
  const shouldShowManagedLabEmptyState =
    isManager && !managedLabId && isLabScopedManagerPath(location.pathname);
  const unreadCount = notificationData?.unreadCount ?? 0;

  useEffect(() => {
    setIsNavOpen(false);
  }, [location.pathname]);


  const handleLogout = () => {
    clearAuthTokens();
    queryClient.clear();
    navigate('/vi', { replace: true });
  };

  if (isAdmin) {
    return <Navigate to="/admin/dashboard" replace />;
  }

  return (
    <div className="app-shell min-h-dvh text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <a
        className="fixed left-4 top-3 z-toast -translate-y-20 rounded-lg bg-slate-950 px-4 py-3 text-sm font-semibold text-white shadow-lg transition-transform focus:translate-y-0 dark:bg-white dark:text-slate-950"
        href="#main-content"
      >
        {t('common.skipMenu')}
      </a>
      <Modal id="main-navigation-drawer" placement="drawer" closeOnBackdrop
        isOpen={isNavOpen} title={portalTitle}
        subtitle={<>{roleLabel}{isManager && user?.managedLab?.name ? <p className="mt-1 break-words">{user.managedLab.name}</p> : null}</>}
        closeLabel={t('common.closeMenu')} onClose={() => setIsNavOpen(false)}>
        <SidebarContent navItems={navItems} portalTitle={portalTitle} roleLabel={roleLabel}
          managedLabName={isManager ? user?.managedLab?.name : null}
          compact onNavigate={() => setIsNavOpen(false)} />
      </Modal>

      <aside className="app-sidebar app-surface fixed inset-y-0 left-0 z-30 hidden w-64 overflow-y-auto border-r border-slate-200 px-4 py-6 dark:border-slate-800 dark:bg-slate-900 lg:block">
        <SidebarContent
          navItems={navItems}
          portalTitle={portalTitle}
          roleLabel={roleLabel}
          managedLabName={isManager ? user?.managedLab?.name : null}
          onNavigate={() => undefined}
        />
      </aside>

      <div className="min-w-0 lg:pl-64">
        <header className="app-workspace-header sticky top-0 z-20 px-4 py-4 lg:px-8">
          <div className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] items-center gap-x-3 gap-y-2 lg:flex lg:justify-between lg:gap-3">
            <div className="contents lg:flex lg:min-w-0 lg:items-center lg:gap-3">
              <Button
                aria-label={t('common.openMenu')}
                aria-controls="main-navigation-drawer"
                aria-expanded={isNavOpen}
                className="h-11 w-11 shrink-0 p-0 lg:hidden"
                size="sm"
                variant="outline"
                onClick={() => setIsNavOpen(true)}
              >
                <Menu aria-hidden="true" className="h-4 w-4" />
                <span className="sr-only">{t('common.openMenu')}</span>
              </Button>
              <div className="col-span-2 row-start-2 min-w-0 lg:col-auto lg:row-auto">
                <p className="app-workspace-eyebrow">{t('common.appTitle')} / {portalTitle}</p>
                <p className="app-workspace-title truncate">
                  {currentPageLabel ?? t('common.workspace')}
                </p>
              </div>
            </div>

            <div className="col-start-2 row-start-1 flex min-w-0 shrink-0 items-center justify-end gap-2 sm:gap-3 lg:col-auto lg:row-auto">
              <LanguageSwitcher />
              <ThemeToggle />
              <button
                aria-label={unreadCount > 0 ? `${t('common.notifications')} (${unreadCount})` : t('common.notifications')}
                className="app-control-surface relative flex h-11 w-11 shrink-0 items-center justify-center rounded-lg border text-slate-600 shadow-sm transition-colors duration-200 hover:border-slate-300 hover:text-slate-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:text-slate-300 dark:hover:border-slate-600 dark:hover:text-white"
                type="button"
                onClick={() => navigate('/app/notifications')}
              >
                <Bell aria-hidden="true" className="h-5 w-5" />
                {unreadCount > 0 ? (
                  <span className="absolute right-1.5 top-1.5 h-2.5 w-2.5 rounded-full border-2 border-white bg-red-500" />
                ) : null}
              </button>
              <div className="app-workspace-user hidden min-w-0 items-center gap-3 px-3 py-1 text-sm xl:flex">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-semibold text-slate-600 dark:bg-slate-700 dark:text-slate-100">
                  {(user?.fullName || user?.email || 'U').charAt(0).toUpperCase()}
                </span>
                <span className="min-w-0">
                  <span className="block max-w-44 truncate font-medium text-slate-800 dark:text-slate-100">
                    {user?.fullName || user?.email || t('common.user')}
                  </span>
                  <span className="block text-xs text-slate-500 dark:text-slate-400">{roleLabel}</span>
                </span>
              </div>
              <Button className="hidden sm:inline-flex" onClick={handleLogout} size="sm" variant="outline">
                {t('common.logout')}
              </Button>
              <Button
                aria-label={t('common.logout')}
                className="sm:hidden"
                onClick={handleLogout}
                size="sm"
                variant="outline"
              >
                <LogOut aria-hidden="true" className="h-4 w-4" />
                <span className="sr-only">{t('common.logout')}</span>
              </Button>
            </div>
          </div>
        </header>

        <main className="app-content mx-auto min-w-0 max-w-[1440px] px-4 lg:px-8" id="main-content" tabIndex={-1}>
          <div className={location.pathname.startsWith('/app/research') ? '' : 'page-enter'} key={location.pathname}>
            {shouldShowManagedLabEmptyState ? <EmptyManagedLabState /> : <Outlet />}
          </div>
        </main>
      </div>
    </div>
  );
}

interface SidebarContentProps {
  navItems: NavItem[];
  portalTitle: string;
  roleLabel: string;
  managedLabName?: string | null;
  compact?: boolean;
  interactive?: boolean;
  onNavigate: () => void;
}

function SidebarContent({
  navItems,
  portalTitle,
  roleLabel,
  managedLabName,
  compact = false,
  interactive = true,
  onNavigate,
}: SidebarContentProps) {
  const { locale, t } = useI18n();

  return (
    <>
      {!compact ? (
        <>
          <div className="app-sidebar-identity">
            <PortalBrand subtitle={portalTitle} onNavigate={onNavigate} tabIndex={interactive ? 0 : -1} />
          </div>
          <div className="app-sidebar-role">
            {roleLabel}
          </div>
          {managedLabName ? (
            <div className="app-sidebar-context rounded-md">
              <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-400">{t('layout.managedLab')}</p>
              <p className="mt-1 truncate text-xs font-medium text-slate-700 dark:text-slate-200" title={managedLabName}>{managedLabName}</p>
            </div>
          ) : null}
        </>
      ) : null}

      <nav className={compact ? 'space-y-1' : 'mt-8 space-y-1'}>
        {navItems.map((item, index) => {
          const Icon = item.icon;
          const showGroup = Boolean(item.groupKey && item.groupKey !== navItems[index - 1]?.groupKey);
          return (
            <div className={showGroup && index > 0 ? 'pt-5' : ''} key={item.path}>
              {showGroup ? <p className="app-nav-group">{t(item.groupKey!)}</p> : null}
              <NavLink
                to={item.path}
                className={({ isActive }) =>
                  [
                    'app-nav-link flex min-h-11 items-center gap-3 text-sm font-medium transition-colors',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500',
                    isActive
                      ? 'font-semibold'
                      : 'text-slate-600 hover:bg-slate-100 hover:text-slate-950 dark:text-slate-300 dark:hover:bg-slate-800 dark:hover:text-white',
                  ].join(' ')
                }
                tabIndex={interactive ? 0 : -1}
                onClick={onNavigate}
              >
                {Icon ? <Icon aria-hidden="true" className="h-4 w-4 shrink-0" /> : null}
                <span className="min-w-0 break-words">{t(item.labelKey)}</span>
              </NavLink>
            </div>
          );
        })}
      </nav>
      <Link className="app-sidebar-footer" to={`/${locale}`} onClick={onNavigate} tabIndex={interactive ? 0 : -1}>
        <Globe aria-hidden="true" size={16} /><span>{locale === 'vi' ? 'Website giới thiệu' : 'Lab website'}</span><ArrowUpRight aria-hidden="true" size={14} className="ml-auto" />
      </Link>
    </>
  );
}
