import { useQueryClient } from '@tanstack/react-query';
import {
  Activity,
  Bell,
  BookOpenCheck,
  Bot,
  ClipboardList,
  FlaskConical,
  LayoutDashboard,
  LogOut,
  Menu,
  ScanFace,
  Settings2,
  UsersRound,
  X,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';

import { clearAuthTokens, getStoredUser } from '../shared/api';
import { Button, LanguageSwitcher, ThemeToggle } from '../shared/components';
import { useI18n } from '../shared/i18n';

interface NavItem {
  labelKey: string;
  path: string;
  groupKey: string;
  icon: LucideIcon;
}

const adminNavItems: NavItem[] = [
  { labelKey: 'nav.adminDashboard', path: '/admin/dashboard', groupKey: 'nav.adminOverviewGroup', icon: LayoutDashboard },
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
  const queryClient = useQueryClient();
  const user = getStoredUser();
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);

  useEffect(() => {
    if (!isMobileNavOpen) return;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsMobileNavOpen(false);
    };
    document.addEventListener('keydown', onEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', onEscape);
    };
  }, [isMobileNavOpen]);

  const handleLogout = () => {
    clearAuthTokens();
    queryClient.clear();
    navigate('/login', { replace: true });
  };

  return (
    <div className="admin-shell app-shell min-h-screen overflow-x-hidden text-slate-900 dark:bg-slate-950 dark:text-slate-100">
      <a
        className="fixed left-4 top-3 z-toast -translate-y-20 rounded-lg bg-slate-950 px-4 py-3 text-sm font-semibold text-white shadow-lg transition-transform focus:translate-y-0 dark:bg-white dark:text-slate-950"
        href="#admin-main-content"
      >
        {t('common.skipMenu')}
      </a>
      <aside className="app-surface fixed inset-y-0 left-0 z-30 hidden w-64 overflow-y-auto border-r border-slate-200 px-4 py-6 dark:border-slate-800 dark:bg-slate-950 lg:block">
        <SidebarContent navItems={adminNavItems} onNavigate={() => setIsMobileNavOpen(false)} />
      </aside>

      {isMobileNavOpen ? (
        <div className="fixed inset-0 z-40 lg:hidden" id="admin-navigation-drawer">
          <button
            aria-label={t('common.closeMenu')}
            className="absolute inset-0 bg-black/60"
            type="button"
            onClick={() => setIsMobileNavOpen(false)}
          />
          <aside aria-label={t('layout.adminPortal')} className="app-surface relative h-full w-72 max-w-[85vw] overflow-y-auto px-4 py-5 shadow-xl dark:bg-slate-950">
            <div className="mb-5 flex items-center justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate text-base font-semibold text-slate-950 dark:text-white">{t('layout.adminPortal')}</p>
                <p className="mt-1 text-xs font-medium text-slate-500 dark:text-slate-400">{t('layout.adminRole')}</p>
              </div>
              <Button
                aria-label={t('common.closeMenu')}
                size="sm"
                variant="outline"
                onClick={() => setIsMobileNavOpen(false)}
              >
                <X aria-hidden="true" className="h-4 w-4" />
                <span className="sr-only">{t('common.close')}</span>
              </Button>
            </div>
            <SidebarContent navItems={adminNavItems} compact onNavigate={() => setIsMobileNavOpen(false)} />
          </aside>
        </div>
      ) : null}

      <div className="min-w-0 lg:pl-64">
        <header className="app-gradient-banner sticky top-0 z-20 border-b border-slate-900 dark:border-slate-800 px-4 py-3 backdrop-blur lg:px-8">
          <div className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] items-center gap-x-3 gap-y-2 lg:flex lg:justify-between lg:gap-3">
            <div className="contents lg:flex lg:min-w-0 lg:items-center lg:gap-3">
              <Button
                aria-label={t('common.openMenu')}
                aria-controls="admin-navigation-drawer"
                aria-expanded={isMobileNavOpen}
                className="shrink-0 lg:hidden"
                size="sm"
                variant="outline"
                onClick={() => setIsMobileNavOpen(true)}
              >
                <Menu aria-hidden="true" className="h-4 w-4" />
                <span className="sr-only">{t('common.openMenu')}</span>
              </Button>
              <div className="col-span-2 row-start-2 min-w-0 lg:col-auto lg:row-auto">
                <p className="text-xs font-medium uppercase text-white/75">{t('layout.adminRole')}</p>
                <h1 className="truncate text-lg font-semibold text-white sm:text-xl">{t('layout.adminPortal')}</h1>
              </div>
            </div>

            <div className="col-start-2 row-start-1 flex min-w-0 shrink-0 items-center justify-end gap-2 sm:gap-3 lg:col-auto lg:row-auto">
              <LanguageSwitcher />
              <ThemeToggle />
              <div className="app-control-surface hidden min-w-0 items-center gap-3 rounded-md border px-3 py-2 text-sm shadow-sm sm:flex">
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

        <main className="mx-auto min-w-0 w-full max-w-[1600px] px-4 py-6 lg:px-8" id="admin-main-content" tabIndex={-1}>
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
  const { t } = useI18n();

  return (
    <>
      {!compact ? (
        <>
          <div className="flex items-center gap-3 px-3">
            <span className="app-icon-badge flex h-11 w-11 shrink-0 items-center justify-center rounded-xl">
              <FlaskConical aria-hidden="true" className="h-5 w-5" />
            </span>
            <div className="min-w-0">
              <p className="truncate text-base font-semibold tracking-tight text-slate-950 dark:text-white">{t('common.appTitle')}</p>
              <p className="truncate text-xs text-slate-600 dark:text-slate-300">{t('layout.adminPortal')}</p>
            </div>
          </div>
          <div className="app-control-surface mt-4 rounded-xl px-3 py-2 text-xs font-semibold text-slate-700 dark:text-slate-200">
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
                <p className="mb-2 px-3 text-[11px] font-semibold uppercase tracking-[0.12em] text-slate-500 dark:text-slate-400">
                  {t(item.groupKey)}
                </p>
              ) : null}
              <NavLink
                to={item.path}
                className={({ isActive }) => [
                  'group flex min-h-11 items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-slate-500',
                  isActive
                    ? 'app-control-surface text-slate-950 dark:text-white'
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
    </>
  );
}
