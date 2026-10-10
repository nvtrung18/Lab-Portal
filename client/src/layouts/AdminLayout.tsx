import { useQueryClient } from '@tanstack/react-query';
import {
  Activity,
  ArrowUpRight,
  Bell,
  BookOpenCheck,
  Bot,
  ClipboardList,
  FlaskConical,
  Globe,
  LayoutDashboard,
  LogOut,
  Menu,
  ScanFace,
  Settings2,
  UsersRound,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';

import { clearAuthTokens, getStoredUser } from '../shared/api';
import { Button, LanguageSwitcher, Modal, PortalBrand, ThemeToggle } from '../shared/components';
import { useI18n } from '../shared/i18n';

interface NavItem {
  labelKey: string;
  path: string;
  groupKey: string;
  icon: LucideIcon;
}

const adminNavItems: NavItem[] = [
  { labelKey: 'nav.adminDashboard', path: '/admin/dashboard', groupKey: 'nav.adminOverviewGroup', icon: LayoutDashboard },
  { labelKey: 'nav.website', path: '/admin/website', groupKey: 'nav.adminOverviewGroup', icon: Globe },
  { labelKey: 'nav.users', path: '/admin/users', groupKey: 'nav.adminManagementGroup', icon: UsersRound },
  { labelKey: 'nav.labsAdmin', path: '/admin/labs', groupKey: 'nav.adminManagementGroup', icon: FlaskConical },
  { labelKey: 'nav.adminOperations', path: '/admin/operational-logs', groupKey: 'nav.adminMonitoringGroup', icon: Activity },
  { labelKey: 'nav.auditLogs', path: '/admin/audit-logs', groupKey: 'nav.adminMonitoringGroup', icon: ClipboardList },
  { labelKey: 'nav.systemConfig', path: '/admin/system-config', groupKey: 'nav.adminToolsGroup', icon: Settings2 },
  { labelKey: 'nav.knowledge', path: '/admin/knowledge', groupKey: 'nav.adminToolsGroup', icon: BookOpenCheck },
  { labelKey: 'nav.faceProfile', path: '/admin/face-profile', groupKey: 'nav.adminToolsGroup', icon: ScanFace },
  { labelKey: 'nav.aiAssistant', path: '/admin/assistant', groupKey: 'nav.adminToolsGroup', icon: Bot },
  { labelKey: 'common.notifications', path: '/admin/notifications', groupKey: 'nav.adminToolsGroup', icon: Bell },
];

export function AdminLayout() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const location = useLocation();
  const currentPage = adminNavItems.find(item => location.pathname === item.path || location.pathname.startsWith(`${item.path}/`));
  const queryClient = useQueryClient();
  const user = getStoredUser();
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);

  useEffect(() => {
    setIsMobileNavOpen(false);
  }, [location.pathname]);

  const handleLogout = () => {
    clearAuthTokens();
    queryClient.clear();
    navigate('/vi', { replace: true });
  };

  return (
    <div className="admin-shell app-shell min-h-dvh text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <a
        className="fixed left-4 top-3 z-toast -translate-y-20 rounded-lg bg-slate-950 px-4 py-3 text-sm font-semibold text-white shadow-lg transition-transform focus:translate-y-0 dark:bg-white dark:text-slate-950"
        href="#admin-main-content"
      >
        {t('common.skipMenu')}
      </a>
      <aside className="app-sidebar app-surface fixed inset-y-0 left-0 z-30 hidden w-64 overflow-y-auto border-r border-slate-200 px-4 py-6 dark:border-slate-800 dark:bg-slate-950 lg:block">
        <SidebarContent navItems={adminNavItems} onNavigate={() => setIsMobileNavOpen(false)} />
      </aside>

      <Modal id="admin-navigation-drawer" placement="drawer" closeOnBackdrop
        isOpen={isMobileNavOpen} title={t('layout.adminPortal')} subtitle={t('layout.adminRole')}
        closeLabel={t('common.closeMenu')} onClose={() => setIsMobileNavOpen(false)}>
        <SidebarContent navItems={adminNavItems} compact onNavigate={() => setIsMobileNavOpen(false)} />
      </Modal>

      <div className="min-w-0 lg:pl-64">
        <header className="app-workspace-header sticky top-0 z-20 px-4 py-4 lg:px-8">
          <div className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] items-center gap-x-3 gap-y-2 lg:flex lg:justify-between lg:gap-3">
            <div className="contents lg:flex lg:min-w-0 lg:items-center lg:gap-3">
              <Button
                aria-label={t('common.openMenu')}
                aria-controls="admin-navigation-drawer"
                aria-expanded={isMobileNavOpen}
                className="h-11 w-11 shrink-0 p-0 lg:hidden"
                size="sm"
                variant="outline"
                onClick={() => setIsMobileNavOpen(true)}
              >
                <Menu aria-hidden="true" className="h-4 w-4" />
                <span className="sr-only">{t('common.openMenu')}</span>
              </Button>
              <div className="col-span-2 row-start-2 min-w-0 lg:col-auto lg:row-auto">
                <p className="app-workspace-eyebrow">{t('common.appTitle')} / {t('layout.adminRole')}</p>
                <p className="app-workspace-title truncate">{currentPage ? t(currentPage.labelKey) : t('layout.adminPortal')}</p>
              </div>
            </div>

            <div className="col-start-2 row-start-1 flex min-w-0 shrink-0 items-center justify-end gap-2 sm:gap-3 lg:col-auto lg:row-auto">
              <LanguageSwitcher />
              <ThemeToggle />
              <div className="app-workspace-user hidden min-w-0 items-center gap-3 px-3 py-1 text-sm xl:flex">
                <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-semibold text-slate-600 dark:bg-slate-700 dark:text-slate-100">
                  {(user?.fullName || user?.email || 'A').charAt(0).toUpperCase()}
                </span>
                <span className="min-w-0">
                  <span className="block max-w-44 truncate font-medium text-slate-800 dark:text-slate-100">
                    {user?.fullName || user?.email || t('layout.adminRole')}
                  </span>
                  <span className="block text-xs text-slate-500 dark:text-slate-400">{t('layout.adminRole')}</span>
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
                <span className="sr-only">{t('common.logout')}</span>
                <LogOut aria-hidden="true" className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </header>

        <main className="app-content mx-auto min-w-0 w-full max-w-[1440px] px-4 lg:px-8" id="admin-main-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}

interface SidebarContentProps {
  navItems: NavItem[];
  compact?: boolean;
  onNavigate: () => void;
}

function SidebarContent({ navItems, compact = false, onNavigate }: SidebarContentProps) {
  const { locale, t } = useI18n();

  return (
    <>
      {!compact ? (
        <>
          <div className="app-sidebar-identity">
            <PortalBrand subtitle={t('layout.adminPortal')} onNavigate={onNavigate} />
          </div>
          <div className="app-sidebar-role">
            {t('layout.adminRole')}
          </div>
        </>
      ) : null}

      <nav aria-label={t('layout.adminPortal')} className={compact ? 'mt-2 space-y-1' : 'mt-7 space-y-1'}>
        {navItems.map((item, index) => {
          const Icon = item.icon;
          const showGroup = index === 0 || item.groupKey !== navItems[index - 1]?.groupKey;

          return (
            <div className={showGroup && index > 0 ? 'pt-4' : ''} key={item.path}>
              {showGroup ? (
                <p className="app-nav-group">
                  {t(item.groupKey)}
                </p>
              ) : null}
              <NavLink
                to={item.path}
                className={({ isActive }) => [
                  'app-nav-link group flex min-h-11 items-center gap-3 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500',
                  isActive
                    ? 'font-semibold'
                    : 'text-slate-600 hover:bg-slate-900/5 dark:text-slate-300 dark:hover:bg-slate-800',
                ].join(' ')}
                onClick={onNavigate}
              >
                <Icon aria-hidden="true" className="h-[18px] w-[18px] shrink-0" />
                <span className="min-w-0 break-words">{t(item.labelKey)}</span>
              </NavLink>
            </div>
          );
        })}
      </nav>
      <Link className="app-sidebar-footer" to={`/${locale}`} onClick={onNavigate}>
        <Globe aria-hidden="true" size={16} /><span>{locale === 'vi' ? 'Website giới thiệu' : 'Lab website'}</span><ArrowUpRight aria-hidden="true" size={14} className="ml-auto" />
      </Link>
    </>
  );
}
